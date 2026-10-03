import { createHash } from "node:crypto";
const GATEWAY_ID = /^[a-z0-9][a-z0-9-]{1,62}$/;
const SNAPSHOT_DIGEST = /^[a-f0-9]{64}$/;

export function vpnLeaseSnapshotDigest(state) {
  return createHash("sha256").update(JSON.stringify(state)).digest("hex");
}

export class VpnLeaseSequenceAuthority {
  async readMinimumSequence(_gatewayId) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async readCommit(_gatewayId) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async assertCommittedSnapshot(_gatewayId, _sequence, _digest) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async commitSequence(_gatewayId, _sequence, _digest) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async invalidateSnapshot(_gatewayId, _sequence, _expectedDigest) {
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

export function validateVpnLeaseCommitBoundary({ gatewayId, sequence, snapshotDigest }) {
  const boundary = validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
  if (typeof snapshotDigest !== "string" || !SNAPSHOT_DIGEST.test(snapshotDigest)) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_DIGEST_INVALID");
  }
  return Object.freeze({ ...boundary, snapshotDigest });
}

/** Parameterized adapter; the caller supplies an independently provisioned PostgreSQL executor. */
export class PostgresVpnLeaseSequenceAuthority extends VpnLeaseSequenceAuthority {
  #execute;
  constructor({ execute } = {}) {
    super();
    if (typeof execute !== "function") throw new Error("VPN_SEQUENCE_AUTHORITY_EXECUTOR_REQUIRED");
    this.#execute = execute;
  }

  async readCommit(gatewayId) {
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence: 1 });
    let result;
    try {
      result = await this.#execute({
        text: "SELECT sequence, state_digest, invalidated FROM sentinel_vpn_lease_sequences WHERE gateway_id = $1",
        values: [gatewayId],
      });
    } catch {
      throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE");
    }
    if (!Array.isArray(result?.rows) || result.rows.length !== 1) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_UNAVAILABLE");
    }
    const row = result.rows[0];
    const sequence = this.#sequence(row?.sequence);
    const snapshotDigest = this.#digest(row?.state_digest);
    const invalidated = this.#invalidated(row?.invalidated);
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
    return Object.freeze({ gatewayId, sequence, snapshotDigest, invalidated });
  }

  async readMinimumSequence(gatewayId) {
    return (await this.readCommit(gatewayId)).sequence;
  }

  async commitSequence(gatewayId, sequence, digest) {
    this.#digest(digest);
    validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
    let result;
    try {
      result = await this.#execute({
        text: "INSERT INTO sentinel_vpn_lease_sequences (gateway_id, sequence, state_digest, invalidated) VALUES ($1, $2, $3, FALSE) ON CONFLICT (gateway_id) DO UPDATE SET sequence = EXCLUDED.sequence, state_digest = EXCLUDED.state_digest, invalidated = FALSE WHERE sentinel_vpn_lease_sequences.sequence < EXCLUDED.sequence OR (sentinel_vpn_lease_sequences.sequence = EXCLUDED.sequence AND sentinel_vpn_lease_sequences.state_digest = EXCLUDED.state_digest AND sentinel_vpn_lease_sequences.invalidated = FALSE) RETURNING sequence, state_digest, invalidated",
        values: [gatewayId, sequence, digest],
      });
    } catch {
      throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE");
    }
    if (!Array.isArray(result?.rows) || result.rows.length !== 1) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED");
    }
    const row = result.rows[0];
    if (this.#sequence(row?.sequence) !== sequence ||
        this.#digest(row?.state_digest) !== digest ||
        this.#invalidated(row?.invalidated)) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED");
    }
    return sequence;
  }

  async assertCommittedSnapshot(gatewayId, sequence, digest) {
    validateVpnLeaseCommitBoundary({ gatewayId, sequence, snapshotDigest: digest });
    const committed = await this.readCommit(gatewayId);
    if (committed.sequence !== sequence ||
        committed.snapshotDigest !== digest ||
        committed.invalidated) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_SNAPSHOT_UNCONFIRMED");
    }
  }

  async invalidateSnapshot(gatewayId, sequence, expectedDigest) {
    validateVpnLeaseCommitBoundary({
      gatewayId,
      sequence,
      snapshotDigest: expectedDigest,
    });
    let result;
    try {
      result = await this.#execute({
        text: "UPDATE sentinel_vpn_lease_sequences SET invalidated = TRUE WHERE gateway_id = $1 AND sequence = $2 AND state_digest = $3 AND invalidated = FALSE RETURNING sequence, state_digest, invalidated",
        values: [gatewayId, sequence, expectedDigest],
      });
    } catch {
      throw new Error("VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE");
    }

    if (Array.isArray(result?.rows) && result.rows.length === 1) {
      const row = result.rows[0];
      if (this.#sequence(row?.sequence) === sequence &&
          this.#digest(row?.state_digest) === expectedDigest &&
          this.#invalidated(row?.invalidated)) {
        return true;
      }
    }

    try {
      const committed = await this.readCommit(gatewayId);
      if (committed.sequence === sequence &&
          committed.snapshotDigest === expectedDigest &&
          committed.invalidated) {
        return true;
      }
    } catch {
      // Preserve the explicit invalidation failure below.
    }
    throw new Error("VPN_SEQUENCE_AUTHORITY_INVALIDATION_UNVERIFIED");
  }

  #digest(value) {
    if (typeof value !== "string" || !SNAPSHOT_DIGEST.test(value)) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_DIGEST_INVALID");
    }
    return value;
  }

  #sequence(value) {
    if (typeof value === "string" && /^[1-9][0-9]{0,15}$/.test(value)) value = Number(value);
    if (!Number.isSafeInteger(value) || value < 1) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID");
    }
    return value;
  }

  #invalidated(value) {
    if (value !== true && value !== false) {
      throw new Error("VPN_SEQUENCE_AUTHORITY_INVALIDATION_STATE_INVALID");
    }
    return value;
  }
}
