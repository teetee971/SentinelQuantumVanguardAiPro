import assert from "node:assert/strict";
import test from "node:test";
import {
  VpnLeaseSequenceAuthority,
  assertVpnLeaseSequenceAuthority,
  validateVpnLeaseCommitBoundary,
  validateVpnLeaseInvalidationBoundary,
  validateVpnLeaseSequenceBoundary,
} from "./lease-sequence-authority.js";

const D1 = "1".repeat(64);
const D2 = "2".repeat(64);

test("sequence authority is fail-closed until backed by external snapshot storage", async () => {
  const authority = new VpnLeaseSequenceAuthority();
  await assert.rejects(
    authority.readCommit("gw-prod-1"),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
  await assert.rejects(
    authority.commitSnapshot("gw-prod-1", 7, D1),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
  await assert.rejects(
    authority.invalidateSnapshot("gw-prod-1", 7, D1, D2),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
});

test("sequence boundary accepts only canonical gateway and positive safe sequence", () => {
  assert.deepEqual(
    validateVpnLeaseSequenceBoundary({ gatewayId: "gw-prod-1", sequence: 7 }),
    { gatewayId: "gw-prod-1", sequence: 7 }
  );
  assert.throws(
    () => validateVpnLeaseSequenceBoundary({ gatewayId: "../gw", sequence: 7 }),
    /VPN_SEQUENCE_AUTHORITY_GATEWAY_INVALID/
  );
  assert.throws(
    () => validateVpnLeaseSequenceBoundary({ gatewayId: "gw-prod-1", sequence: 0 }),
    /VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID/
  );
});

test("commit boundary requires a canonical sha256 snapshot digest", () => {
  assert.deepEqual(
    validateVpnLeaseCommitBoundary({ gatewayId: "gw-prod-1", sequence: 7, snapshotDigest: D1 }),
    { gatewayId: "gw-prod-1", sequence: 7, snapshotDigest: D1 }
  );
  assert.throws(
    () => validateVpnLeaseCommitBoundary({ gatewayId: "gw-prod-1", sequence: 7, snapshotDigest: "bad" }),
    /VPN_SEQUENCE_AUTHORITY_DIGEST_INVALID/
  );
});

test("invalidation boundary requires a distinct canonical replacement digest", () => {
  assert.deepEqual(
    validateVpnLeaseInvalidationBoundary({
      gatewayId: "gw-prod-1",
      sequence: 7,
      expectedDigest: D1,
      invalidationDigest: D2,
    }),
    {
      gatewayId: "gw-prod-1",
      sequence: 7,
      expectedDigest: D1,
      invalidationDigest: D2,
    }
  );
  assert.throws(
    () => validateVpnLeaseInvalidationBoundary({
      gatewayId: "gw-prod-1",
      sequence: 7,
      expectedDigest: D1,
      invalidationDigest: D1,
    }),
    /VPN_SEQUENCE_AUTHORITY_INVALIDATION_DIGEST_REUSED/
  );
});

test("sequence authority type boundary rejects duck-typed local substitutes", () => {
  assert.throws(
    () => assertVpnLeaseSequenceAuthority({
      readCommit: async () => ({ sequence: 1, snapshotDigest: D1 }),
      commitSnapshot: async () => {},
      invalidateSnapshot: async () => {},
    }),
    /VpnLeaseSequenceAuthority required/
  );
});
