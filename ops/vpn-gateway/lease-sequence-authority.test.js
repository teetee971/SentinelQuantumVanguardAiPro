import assert from "node:assert/strict";
import test from "node:test";
import {
  VpnLeaseSequenceAuthority,
  assertVpnLeaseSequenceAuthority,
  validateVpnLeaseSequenceBoundary,
} from "./lease-sequence-authority.js";

test("sequence authority is fail-closed until backed by external monotonic storage", async () => {
  const authority = new VpnLeaseSequenceAuthority();
  await assert.rejects(
    authority.readMinimumSequence("gw-prod-1"),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
  await assert.rejects(
    authority.commitSequence("gw-prod-1", 7),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
});

test("sequence authority boundary accepts only canonical gateway and positive safe sequence", () => {
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

test("sequence authority type boundary rejects duck-typed local substitutes", () => {
  assert.throws(
    () => assertVpnLeaseSequenceAuthority({
      readMinimumSequence: async () => 1,
      commitSequence: async () => {},
    }),
    /VpnLeaseSequenceAuthority required/
  );
});
