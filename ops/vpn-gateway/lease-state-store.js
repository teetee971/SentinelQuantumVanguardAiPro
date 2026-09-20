import { createHmac, timingSafeEqual } from "node:crypto";
import { mkdir, open, readFile, rename } from "node:fs/promises";
import { dirname } from "node:path";

const SCHEMA_VERSION = 1;
const MAX_FILE_BYTES = 256 * 1024;
const GATEWAY_ID = /^[a-z0-9][a-z0-9-]{1,62}$/;

function assertSecret(secret) {
  if (typeof secret !== "string" || secret.length < 32 || secret.length > 4096) {
    throw new Error("VPN_LEASE_STORE_SECRET_INVALID");
  }
}

function canonicalPayload(gatewayId, sequence, state) {
  return JSON.stringify({
    schemaVersion: SCHEMA_VERSION,
    gatewayId,
    sequence,
    state,
  });
}

function sign(gatewayId, sequence, state, secret) {
  return createHmac("sha256", secret)
    .update(canonicalPayload(gatewayId, sequence, state))
    .digest("hex");
}

export class VpnLeaseStateStore {
  #path;
  #secret;
  #gatewayId;
  #lastSequence = 0;

  constructor({ path, secret, gatewayId }) {
    if (typeof path !== "string" || !path.trim() || path.includes("\0")) {
      throw new Error("VPN_LEASE_STORE_PATH_INVALID");
    }
    assertSecret(secret);
    if (!GATEWAY_ID.test(String(gatewayId || ""))) {
      throw new Error("VPN_LEASE_STORE_GATEWAY_INVALID");
    }
    this.#path = path;
    this.#secret = secret;
    this.#gatewayId = gatewayId;
  }

  async save(state) {
    if (!state || typeof state !== "object" || Array.isArray(state)) {
      throw new Error("VPN_LEASE_STORE_STATE_INVALID");
    }

    const sequence = this.#lastSequence + 1;
    const envelope = {
      schemaVersion: SCHEMA_VERSION,
      gatewayId: this.#gatewayId,
      sequence,
      state,
      mac: sign(this.#gatewayId, sequence, state, this.#secret),
    };
    const encoded = JSON.stringify(envelope);
    if (Buffer.byteLength(encoded, "utf8") > MAX_FILE_BYTES) {
      throw new Error("VPN_LEASE_STORE_TOO_LARGE");
    }

    await mkdir(dirname(this.#path), { recursive: true, mode: 0o700 });
    const temporary = `${this.#path}.tmp-${process.pid}`;
    const handle = await open(temporary, "w", 0o600);
    try {
      await handle.writeFile(encoded, "utf8");
      await handle.sync();
    } finally {
      await handle.close();
    }
    await rename(temporary, this.#path);
    this.#lastSequence = sequence;
    return sequence;
  }

  async load({ minimumSequence = null } = {}) {
    if (minimumSequence !== null &&
        (!Number.isSafeInteger(minimumSequence) || minimumSequence < 1)) {
      throw new Error("VPN_LEASE_STORE_MINIMUM_SEQUENCE_INVALID");
    }
    const raw = await readFile(this.#path);
    if (raw.length > MAX_FILE_BYTES) throw new Error("VPN_LEASE_STORE_TOO_LARGE");

    const envelope = JSON.parse(raw.toString("utf8"));
    if (envelope?.schemaVersion !== SCHEMA_VERSION) {
      throw new Error("VPN_LEASE_STORE_SCHEMA_INVALID");
    }
    if (envelope.gatewayId !== this.#gatewayId) {
      throw new Error("VPN_LEASE_STORE_GATEWAY_MISMATCH");
    }
    if (!Number.isSafeInteger(envelope.sequence) || envelope.sequence < 1) {
      throw new Error("VPN_LEASE_STORE_SEQUENCE_INVALID");
    }
    if (!envelope.state || typeof envelope.state !== "object" || Array.isArray(envelope.state)) {
      throw new Error("VPN_LEASE_STORE_STATE_INVALID");
    }
    if (typeof envelope.mac !== "string" || !/^[a-f0-9]{64}$/.test(envelope.mac)) {
      throw new Error("VPN_LEASE_STORE_MAC_INVALID");
    }

    const expected = sign(
      envelope.gatewayId,
      envelope.sequence,
      envelope.state,
      this.#secret
    );
    const expectedBytes = Buffer.from(expected, "hex");
    const providedBytes = Buffer.from(envelope.mac, "hex");
    if (!timingSafeEqual(expectedBytes, providedBytes)) {
      throw new Error("VPN_LEASE_STORE_INTEGRITY_FAILURE");
    }
    const replayFloor = minimumSequence === null
      ? this.#lastSequence
      : Math.max(this.#lastSequence, minimumSequence);
    if (envelope.sequence < replayFloor) {
      throw new Error("VPN_LEASE_STORE_REPLAY_DETECTED");
    }

    this.#lastSequence = envelope.sequence;
    return Object.freeze({
      sequence: envelope.sequence,
      state: envelope.state,
    });
  }
}

export const vpnLeaseStateStoreLimits = Object.freeze({
  schemaVersion: SCHEMA_VERSION,
  maxFileBytes: MAX_FILE_BYTES,
});
