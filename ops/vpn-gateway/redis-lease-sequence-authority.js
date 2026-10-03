import {
  VpnLeaseSequenceAuthority,
  validateVpnLeaseSequenceBoundary,
} from "./lease-sequence-authority.js";

const SAFE_SEGMENT = /^[a-z0-9][a-z0-9_-]{0,63}$/;

// One atomic compare-and-set. Equal values are idempotent; lower values are rejected.
const COMMIT_SCRIPT = `
local current = redis.call('GET', KEYS[1])
local proposed = tonumber(ARGV[1])
if not proposed then
  return {'PROTOCOL_ERROR', ''}
end
if current then
  local currentNumber = tonumber(current)
  if not currentNumber then
    return {'PROTOCOL_ERROR', current}
  end
  if proposed < currentNumber then
    return {'ROLLBACK', current}
  end
  if proposed == currentNumber then
    return {'OK', current}
  end
end
redis.call('SET', KEYS[1], ARGV[1])
return {'OK', ARGV[1]}
`.trim();

function parseStoredSequence(raw) {
  if (typeof raw !== "string" || !/^[1-9][0-9]*$/.test(raw)) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID");
  }
  const sequence = Number(raw);
  if (!Number.isSafeInteger(sequence) || sequence < 1) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID");
  }
  return sequence;
}

function parseCommitReply(reply) {
  if (!Array.isArray(reply) || reply.length !== 2) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_PROTOCOL_ERROR");
  }
  const [status, rawSequence] = reply;
  if (status === "ROLLBACK") {
    const current = parseStoredSequence(String(rawSequence));
    const error = new Error("VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED");
    error.currentSequence = current;
    throw error;
  }
  if (status !== "OK") {
    throw new Error("VPN_SEQUENCE_AUTHORITY_PROTOCOL_ERROR");
  }
  return parseStoredSequence(String(rawSequence));
}

/**
 * Durable monotonic sequence authority backed by Redis.
 *
 * The adapter deliberately accepts a tiny injected Redis contract instead of constructing a
 * client or reading credentials itself:
 *   - getValue(key) -> string|null
 *   - evalScript({ script, keys, args }) -> Redis Lua return value
 *
 * This keeps secret management and connection lifecycle outside the policy library while the
 * monotonic compare-and-set remains atomic inside Redis.
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
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence: 1 });
    return `sentinel:${this.environment}:${this.namespace}:${gatewayId}`;
  }

  async readMinimumSequence(gatewayId) {
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
    return parseStoredSequence(String(raw));
  }

  async commitSequence(gatewayId, sequence) {
    const boundary = validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
    const key = this.keyFor(boundary.gatewayId);

    let reply;
    try {
      reply = await this.evalScript({
        script: COMMIT_SCRIPT,
        keys: [key],
        args: [String(boundary.sequence)],
      });
    } catch (error) {
      if (
        error?.message === "VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED" ||
        error?.message === "VPN_SEQUENCE_AUTHORITY_PROTOCOL_ERROR" ||
        error?.message === "VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID"
      ) {
        throw error;
      }
      throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE");
    }

    const committed = parseCommitReply(reply);
    if (committed !== boundary.sequence) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED");
    }
    return committed;
  }
}

export { COMMIT_SCRIPT, parseCommitReply, parseStoredSequence };
