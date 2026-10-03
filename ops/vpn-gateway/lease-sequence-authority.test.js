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


import { PostgresVpnLeaseSequenceAuthority } from "./lease-sequence-authority.js";
test("PostgreSQL authority requires an executor and parameterizes all identities", async () => {
  assert.throws(() => new PostgresVpnLeaseSequenceAuthority(), /EXECUTOR_REQUIRED/);
  const queries = [];
  const authority = new PostgresVpnLeaseSequenceAuthority({ execute: async query => {
    queries.push(query); return { rows: [{ sequence: "7", state_digest: "a".repeat(64) }] };
  }});
  assert.equal(await authority.commitSequence("fr-par-01", 7, "a".repeat(64)), 7);
  assert.equal(await authority.readMinimumSequence("fr-par-01"), 7);
  assert.deepEqual(queries[0].values, ["fr-par-01", 7, "a".repeat(64)]);
  assert.ok(!queries[0].text.includes("fr-par-01"));
  await assert.rejects(authority.commitSequence("../bad", 7, "a".repeat(64)), /GATEWAY_INVALID/);
  assert.equal(queries.length, 2);
});
test("PostgreSQL authority rejects missing, malformed or inconsistent confirmations", async () => {
  for (const rows of [[], [{ sequence: "07" }], [{ sequence: "9007199254740992" }], [{ sequence: 6 }], [{ sequence: 7 }, { sequence: 7 }]]) {
    const authority = new PostgresVpnLeaseSequenceAuthority({ execute: async () => ({ rows }) });
    await assert.rejects(authority.commitSequence("fr-par-01", 7, "a".repeat(64)), /VPN_SEQUENCE_AUTHORITY_/);
  }
  const unavailable = new PostgresVpnLeaseSequenceAuthority({ execute: async () => { throw Error("private connection details"); } });
  await assert.rejects(unavailable.readMinimumSequence("fr-par-01"), /^Error: VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE$/);
});
