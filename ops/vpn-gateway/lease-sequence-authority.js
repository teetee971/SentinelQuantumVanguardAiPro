import { createHash } from "node:crypto";
const GATEWAY_ID = /^[a-z0-9][a-z0-9-]{1,62}$/;

export function vpnLeaseSnapshotDigest(state) {
  return createHash("sha256").update(JSON.stringify(state)).digest("hex");
}

export class VpnLeaseSequenceAuthority {
  async readMinimumSequence(_gatewayId) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async assertCommittedSnapshot(_gatewayId, _sequence, _digest) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async commitSequence(_gatewayId, _sequence) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }
}

export function assertVpnLeaseSequenceAuthority(authority) {
  if (!(authority instanceof VpnLeaseSequenceAuthority)) {
    throw new TypeError("VpnLeaseSequenceAuthority required");
  }
  return authority;
}

export function validateVpnLeaseSequenceBoundary({ gatewayId, sequence }) {
  if (typeof gatewayId !== "string" || !GATEWAY_ID.test(gatewayId)) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_GATEWAY_INVALID");
  }
  if (!Number.isSafeInteger(sequence) || sequence < 1) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID");
  }
  return Object.freeze({ gatewayId, sequence });
}

/** Parameterized adapter; the caller supplies an independently provisioned PostgreSQL executor. */
export class PostgresVpnLeaseSequenceAuthority extends VpnLeaseSequenceAuthority {
  #execute;
  constructor({ execute } = {}) {
    super();
    if (typeof execute !== "function") throw new Error("VPN_SEQUENCE_AUTHORITY_EXECUTOR_REQUIRED");
    this.#execute = execute;
  }

  async readMinimumSequence(gatewayId) {
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence: 1 });
    let result;
    try {
      result = await this.#execute({
        text: "SELECT sequence, state_digest FROM sentinel_vpn_lease_sequences WHERE gateway_id = $1",
        values: [gatewayId],
      });
    } catch { throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE"); }
    if (!Array.isArray(result?.rows) || result.rows.length !== 1) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_UNAVAILABLE");
    }
    this.#digest(result.rows[0]?.state_digest);
    const sequence = this.#sequence(result.rows[0]?.sequence);
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
    return sequence;
  }

  async commitSequence(gatewayId, sequence, digest) {
    this.#digest(digest);
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
    let result;
    try {
      result = await this.#execute({
        text: "INSERT INTO sentinel_vpn_lease_sequences (gateway_id, sequence, state_digest) VALUES ($1, $2, $3) ON CONFLICT (gateway_id) DO UPDATE SET sequence = EXCLUDED.sequence, state_digest = EXCLUDED.state_digest WHERE sentinel_vpn_lease_sequences.sequence < EXCLUDED.sequence OR (sentinel_vpn_lease_sequences.sequence = EXCLUDED.sequence AND sentinel_vpn_lease_sequences.state_digest = EXCLUDED.state_digest) RETURNING sequence, state_digest",
        values: [gatewayId, sequence, digest],
      });
    } catch { throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE"); }
    if (!Array.isArray(result?.rows) || result.rows.length !== 1 || this.#sequence(result.rows[0]?.sequence) !== sequence || result.rows[0]?.state_digest !== digest) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED");
    }
    return sequence;
  }

  async assertCommittedSnapshot(gatewayId, sequence, digest) {
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
    this.#digest(digest);
    let result;
    try {
      result = await this.#execute({ text: "SELECT sequence, state_digest FROM sentinel_vpn_lease_sequences WHERE gateway_id = $1", values: [gatewayId] });
    } catch { throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE"); }
    if (!Array.isArray(result?.rows) || result.rows.length !== 1 ||
        this.#sequence(result.rows[0]?.sequence) !== sequence || result.rows[0]?.state_digest !== digest) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_SNAPSHOT_UNCONFIRMED");
    }
  }

  #digest(value) {
    if (typeof value !== "string" || !/^[a-f0-9]{64}$/.test(value)) throw new Error("VPN_SEQUENCE_AUTHORITY_DIGEST_INVALID");
  }

  #sequence(value) {
    if (typeof value === "string" && /^[1-9][0-9]{0,15}$/.test(value)) value = Number(value);
    if (!Number.isSafeInteger(value) || value < 1) throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID");
    return value;
  }
}
