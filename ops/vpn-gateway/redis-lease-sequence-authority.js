import { validateVpnLeaseCommitBoundary, VpnLeaseSequenceAuthority } from "./lease-sequence-authority.js";

const SAFE_SEGMENT = /^[a-z0-9][a-z0-9_-]{0,63}$/;
const SNAPSHOT_DIGEST = /^[a-f0-9]{64}$/;

// Atomic compare-and-set over the exact committed snapshot identity. Decimal strings are compared
// without tonumber so corrupted/out-of-safe-range Redis values can never be normalized silently.
const COMMIT_SCRIPT = `
local MAX_SAFE = '9007199254740991'

local function validSequence(value)
  if type(value) ~= 'string' or not string.match(value, '^[1-9][0-9]*$') then
    return false
  end
  if #value > #MAX_SAFE then return false end
  if #value == #MAX_SAFE and value > MAX_SAFE then return false end
  return true
end

local function validDigest(value)
  return type(value) == 'string' and #value == 64 and string.match(value, '^[a-f0-9]+$') ~= nil
end

local function compareDecimal(left, right)
  if #left < #right then return -1 end
  if #left > #right then return 1 end
  if left < right then return -1 end
  if left > right then return 1 end
  return 0
end

local proposedSequence = ARGV[1]
local proposedDigest = ARGV[2]
if not validSequence(proposedSequence) or not validDigest(proposedDigest) then
  return {'PROTOCOL_ERROR', '', ''}
end

local current = redis.call('GET', KEYS[1])
if current then
  local separator = string.find(current, ':', 1, true)
  if not separator then
    return {'PROTOCOL_ERROR', '', ''}
  end
  local currentSequence = string.sub(current, 1, separator - 1)
  local currentDigest = string.sub(current, separator + 1)
  if not validSequence(currentSequence) or not validDigest(currentDigest) then
    return {'PROTOCOL_ERROR', '', ''}
  end

  local comparison = compareDecimal(proposedSequence, currentSequence)
  if comparison < 0 then
    return {'ROLLBACK', currentSequence, currentDigest}
  end
  if comparison == 0 then
    if proposedDigest ~= currentDigest then
      return {'CONFLICT', currentSequence, currentDigest}
    end
    return {'OK', currentSequence, currentDigest}
  end
end

redis.call('SET', KEYS[1], proposedSequence .. ':' .. proposedDigest)
return {'OK', proposedSequence, proposedDigest}
`.trim();

function parseStoredCommit(raw) {
  if (typeof raw !== "string") {
    throw new Error("VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID");
  }
  const match = raw.match(/^([1-9][0-9]*):([a-f0-9]{64})$/);
  if (!match) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID");
  }
  const sequence = Number(match[1]);
  if (!Number.isSafeInteger(sequence) || sequence < 1 || !SNAPSHOT_DIGEST.test(match[2])) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID");
  }
  return Object.freeze({ sequence, snapshotDigest: match[2] });
}

function parseCommitReply(reply) {
  if (!Array.isArray(reply) || reply.length !== 3) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_PROTOCOL_ERROR");
  }
  const [status, rawSequence, rawDigest] = reply;
  const rawRecord = `${String(rawSequence)}:${String(rawDigest)}`;
  if (status === "ROLLBACK") {
    const current = parseStoredCommit(rawRecord);
    const error = new Error("VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED");
    error.currentCommit = current;
    throw error;
  }
  if (status === "CONFLICT") {
    const current = parseStoredCommit(rawRecord);
    const error = new Error("VPN_SEQUENCE_AUTHORITY_SNAPSHOT_CONFLICT");
    error.currentCommit = current;
    throw error;
  }
  if (status !== "OK") {
    throw new Error("VPN_SEQUENCE_AUTHORITY_PROTOCOL_ERROR");
  }
  return parseStoredCommit(rawRecord);
}

/**
 * Durable snapshot commit authority backed by Redis.
 *
 * The adapter deliberately accepts a tiny injected Redis contract instead of constructing a
 * client or reading credentials itself:
 *   - getValue(key) -> string|null
 *   - evalScript({ script, keys, args }) -> Redis Lua return value
 *
 * Redis stores `sequence:snapshotDigest`; equality is idempotent only when both fields match.
 */
export class RedisVpnLeaseSequenceAuthority extends VpnLeaseSequenceAuthority {
  constructor({
    getValue,
    evalScript,
    environment,
    namespace = "vpn-lease-sequence",
  } = {}) {
    super();
    if (typeof getValue !== "function") {
      throw new TypeError("VPN_SEQUENCE_AUTHORITY_REDIS_GET_REQUIRED");
    }
    if (typeof evalScript !== "function") {
      throw new TypeError("VPN_SEQUENCE_AUTHORITY_REDIS_EVAL_REQUIRED");
    }
    if (!SAFE_SEGMENT.test(environment ?? "")) {
      throw new TypeError("VPN_SEQUENCE_AUTHORITY_ENVIRONMENT_INVALID");
    }
    if (!SAFE_SEGMENT.test(namespace)) {
      throw new TypeError("VPN_SEQUENCE_AUTHORITY_NAMESPACE_INVALID");
    }

    this.getValue = getValue;
    this.evalScript = evalScript;
    this.environment = environment;
    this.namespace = namespace;
  }

  keyFor(gatewayId) {
    validateVpnLeaseCommitBoundary({
      gatewayId,
      sequence: 1,
      snapshotDigest: "0".repeat(64),
    });
    return `sentinel:${this.environment}:${this.namespace}:${gatewayId}`;
  }

  async readCommit(gatewayId) {
    const key = this.keyFor(gatewayId);
    let raw;
    try {
      raw = await this.getValue(key);
    } catch {
      throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE");
    }
    if (raw === null || raw === undefined) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_MISSING");
    }
    return parseStoredCommit(String(raw));
  }

  async commitSnapshot(gatewayId, sequence, snapshotDigest) {
    const boundary = validateVpnLeaseCommitBoundary({ gatewayId, sequence, snapshotDigest });
    const key = this.keyFor(boundary.gatewayId);

    let reply;
    try {
      reply = await this.evalScript({
        script: COMMIT_SCRIPT,
        keys: [key],
        args: [String(boundary.sequence), boundary.snapshotDigest],
      });
    } catch {
      throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE");
    }

    const committed = parseCommitReply(reply);
    if (
      committed.sequence !== boundary.sequence ||
      committed.snapshotDigest !== boundary.snapshotDigest
    ) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED");
    }
    return committed;
  }
}

export { COMMIT_SCRIPT, parseCommitReply, parseStoredCommit };
