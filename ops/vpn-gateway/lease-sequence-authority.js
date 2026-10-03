const GATEWAY_ID = /^[a-z0-9][a-z0-9-]{1,62}$/;
const SNAPSHOT_DIGEST = /^[a-f0-9]{64}$/;

export class VpnLeaseSequenceAuthority {
  async readCommit(_gatewayId) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async commitSnapshot(_gatewayId, _sequence, _snapshotDigest) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED");
  }

  async invalidateSnapshot(_gatewayId, _sequence, _expectedDigest, _invalidationDigest) {
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
  if (!GATEWAY_ID.test(String(gatewayId || ""))) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_GATEWAY_INVALID");
  }
  if (!Number.isSafeInteger(sequence) || sequence < 1) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID");
  }
  return Object.freeze({ gatewayId, sequence });
}

export function validateVpnLeaseCommitBoundary({ gatewayId, sequence, snapshotDigest }) {
  const boundary = validateVpnLeaseSequenceBoundary({ gatewayId, sequence });
  if (!SNAPSHOT_DIGEST.test(String(snapshotDigest || ""))) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_DIGEST_INVALID");
  }
  return Object.freeze({
    gatewayId: boundary.gatewayId,
    sequence: boundary.sequence,
    snapshotDigest,
  });
}

export function validateVpnLeaseInvalidationBoundary({
  gatewayId,
  sequence,
  expectedDigest,
  invalidationDigest,
}) {
  const expected = validateVpnLeaseCommitBoundary({
    gatewayId,
    sequence,
    snapshotDigest: expectedDigest,
  });
  if (!SNAPSHOT_DIGEST.test(String(invalidationDigest || ""))) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_INVALIDATION_DIGEST_INVALID");
  }
  if (invalidationDigest === expected.snapshotDigest) {
    throw new Error("VPN_SEQUENCE_AUTHORITY_INVALIDATION_DIGEST_REUSED");
  }
  return Object.freeze({
    gatewayId: expected.gatewayId,
    sequence: expected.sequence,
    expectedDigest: expected.snapshotDigest,
    invalidationDigest,
  });
}
