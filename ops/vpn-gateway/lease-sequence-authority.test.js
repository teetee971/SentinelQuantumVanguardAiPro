import assert from "node:assert/strict";
import test from "node:test";
import {
  VpnLeaseSequenceAuthority,
  assertVpnLeaseSequenceAuthority,
  validateVpnLeaseSequenceBoundary,
  validateVpnLeaseCommitBoundary,
  PostgresVpnLeaseSequenceAuthority,
} from "./lease-sequence-authority.js";

test("sequence authority is fail-closed until backed by external monotonic storage", async () => {
  const authority = new VpnLeaseSequenceAuthority();
  await assert.rejects(
    authority.readMinimumSequence("gw-prod-1"),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
  await assert.rejects(
    authority.readCommit("gw-prod-1"),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
  await assert.rejects(
    authority.commitSequence("gw-prod-1", 7, "a".repeat(64)),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
  await assert.rejects(
    authority.invalidateSnapshot("gw-prod-1", 7, "a".repeat(64)),
    /VPN_SEQUENCE_AUTHORITY_NOT_IMPLEMENTED/
  );
});

test("sequence authority boundary accepts only canonical gateway, sequence and digest", () => {
  assert.deepEqual(
    validateVpnLeaseSequenceBoundary({ gatewayId: "gw-prod-1", sequence: 7 }),
    { gatewayId: "gw-prod-1", sequence: 7 }
  );
  assert.deepEqual(
    validateVpnLeaseCommitBoundary({
      gatewayId: "gw-prod-1",
      sequence: 7,
      snapshotDigest: "a".repeat(64),
    }),
    { gatewayId: "gw-prod-1", sequence: 7, snapshotDigest: "a".repeat(64) }
  );
  assert.throws(
    () => validateVpnLeaseSequenceBoundary({ gatewayId: "../gw", sequence: 7 }),
    /VPN_SEQUENCE_AUTHORITY_GATEWAY_INVALID/
  );
  assert.throws(
    () => validateVpnLeaseSequenceBoundary({ gatewayId: "gw-prod-1", sequence: 0 }),
    /VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID/
  );
  assert.throws(
    () => validateVpnLeaseCommitBoundary({ gatewayId: "gw-prod-1", sequence: 7, snapshotDigest: "bad" }),
    /VPN_SEQUENCE_AUTHORITY_DIGEST_INVALID/
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

test("PostgreSQL authority requires an executor and parameterizes all identities", async () => {
  assert.throws(() => new PostgresVpnLeaseSequenceAuthority(), /EXECUTOR_REQUIRED/);
  const queries = [];
  const authority = new PostgresVpnLeaseSequenceAuthority({ execute: async query => {
    queries.push(query);
    return { rows: [{ sequence: "7", state_digest: "a".repeat(64), invalidated: false }] };
  }});
  assert.equal(await authority.commitSequence("fr-par-01", 7, "a".repeat(64)), 7);
  assert.equal(await authority.readMinimumSequence("fr-par-01"), 7);
  assert.deepEqual(await authority.readCommit("fr-par-01"), {
    gatewayId: "fr-par-01",
    sequence: 7,
    snapshotDigest: "a".repeat(64),
    invalidated: false,
  });
  assert.deepEqual(queries[0].values, ["fr-par-01", 7, "a".repeat(64)]);
  assert.ok(!queries[0].text.includes("fr-par-01"));
  await assert.rejects(authority.commitSequence("../bad", 7, "a".repeat(64)), /GATEWAY_INVALID/);
  assert.equal(queries.length, 3);
});

test("PostgreSQL authority rejects missing, malformed or inconsistent confirmations", async () => {
  for (const rows of [
    [],
    [{ sequence: "07", state_digest: "a".repeat(64), invalidated: false }],
    [{ sequence: "9007199254740992", state_digest: "a".repeat(64), invalidated: false }],
    [{ sequence: 6, state_digest: "a".repeat(64), invalidated: false }],
    [{ sequence: 7, state_digest: "a".repeat(64), invalidated: false }, { sequence: 7, state_digest: "a".repeat(64), invalidated: false }],
    [{ sequence: 7, state_digest: "a".repeat(64), invalidated: "false" }],
  ]) {
    const authority = new PostgresVpnLeaseSequenceAuthority({ execute: async () => ({ rows }) });
    await assert.rejects(authority.commitSequence("fr-par-01", 7, "a".repeat(64)), /VPN_SEQUENCE_AUTHORITY_/);
  }
  const unavailable = new PostgresVpnLeaseSequenceAuthority({ execute: async () => { throw Error("private connection details"); } });
  await assert.rejects(unavailable.readMinimumSequence("fr-par-01"), /^Error: VPN_SEQUENCE_AUTHORITY_STORE_UNAVAILABLE$/);
});

test("PostgreSQL authority invalidates an exact committed snapshot and never accepts it again", async () => {
  const digest = "b".repeat(64);
  let row = { sequence: 11, state_digest: digest, invalidated: false };
  const authority = new PostgresVpnLeaseSequenceAuthority({ execute: async query => {
    if (query.text.startsWith("UPDATE")) {
      assert.deepEqual(query.values, ["fr-par-01", 11, digest]);
      row = { ...row, invalidated: true };
      return { rows: [row] };
    }
    if (query.text.startsWith("INSERT")) return { rows: row.invalidated ? [] : [row] };
    return { rows: [row] };
  }});

  await authority.assertCommittedSnapshot("fr-par-01", 11, digest);
  assert.equal(await authority.invalidateSnapshot("fr-par-01", 11, digest), true);
  await assert.rejects(
    authority.assertCommittedSnapshot("fr-par-01", 11, digest),
    /VPN_SEQUENCE_AUTHORITY_SNAPSHOT_UNCONFIRMED/
  );
  await assert.rejects(
    authority.commitSequence("fr-par-01", 11, digest),
    /VPN_SEQUENCE_AUTHORITY_COMMIT_UNVERIFIED/
  );
});
