import assert from "node:assert/strict";
import test from "node:test";
import {
  COMMIT_SCRIPT,
  INVALIDATE_SCRIPT,
  RedisVpnLeaseSequenceAuthority,
  parseCommitReply,
  parseInvalidationReply,
  parseStoredCommit,
} from "./redis-lease-sequence-authority.js";

const D1 = "1".repeat(64);
const D2 = "2".repeat(64);
const D3 = "3".repeat(64);

function parseRecord(raw) {
  const [sequence, digest, ...extra] = String(raw).split(":");
  if (extra.length || !/^[1-9][0-9]*$/.test(sequence || "") || !/^[a-f0-9]{64}$/.test(digest || "")) {
    return null;
  }
  const numeric = Number(sequence);
  if (!Number.isSafeInteger(numeric) || numeric < 1) return null;
  return { sequence: numeric, digest };
}

function fakeRedis() {
  const values = new Map();
  return {
    values,
    async getValue(key) {
      return values.get(key) ?? null;
    },
    async evalScript({ script, keys, args }) {
      assert.equal(keys.length, 1);
      const key = keys[0];
      const currentRaw = values.get(key);
      const current = currentRaw === undefined ? null : parseRecord(currentRaw);

      if (script === COMMIT_SCRIPT) {
        assert.equal(args.length, 2);
        const proposedSequence = Number(args[0]);
        const proposedDigest = args[1];
        if (!Number.isSafeInteger(proposedSequence) || proposedSequence < 1 || !/^[a-f0-9]{64}$/.test(proposedDigest)) {
          return ["PROTOCOL_ERROR", "", ""];
        }
        if (currentRaw !== undefined && !current) return ["PROTOCOL_ERROR", "", ""];
        if (current) {
          if (proposedSequence < current.sequence) return ["ROLLBACK", String(current.sequence), current.digest];
          if (proposedSequence === current.sequence) {
            if (proposedDigest !== current.digest) return ["CONFLICT", String(current.sequence), current.digest];
            return ["OK", String(current.sequence), current.digest];
          }
        }
        values.set(key, `${proposedSequence}:${proposedDigest}`);
        return ["OK", String(proposedSequence), proposedDigest];
      }

      if (script === INVALIDATE_SCRIPT) {
        assert.equal(args.length, 3);
        const [expectedSequenceRaw, expectedDigest, invalidationDigest] = args;
        const expectedSequence = Number(expectedSequenceRaw);
        if (!current) return currentRaw === undefined ? ["MISSING", "", ""] : ["PROTOCOL_ERROR", "", ""];
        if (current.sequence !== expectedSequence) return ["STALE", String(current.sequence), current.digest];
        if (current.digest === invalidationDigest) return ["OK", String(current.sequence), current.digest];
        if (current.digest !== expectedDigest) return ["CONFLICT", String(current.sequence), current.digest];
        values.set(key, `${expectedSequence}:${invalidationDigest}`);
        return ["OK", String(expectedSequence), invalidationDigest];
      }

      throw new Error("unknown test script");
    },
  };
}

function authority(redis = fakeRedis()) {
  return {
    redis,
    authority: new RedisVpnLeaseSequenceAuthority({
      getValue: redis.getValue,
      evalScript: redis.evalScript,
      environment: "prod-eu",
    }),
  };
}

test("constructor fails closed without the injected Redis contract", () => {
  assert.throws(
    () => new RedisVpnLeaseSequenceAuthority({ environment: "prod" }),
    /VPN_SEQUENCE_AUTHORITY_REDIS_GET_REQUIRED/
  );
  assert.throws(
    () => new RedisVpnLeaseSequenceAuthority({ getValue: async () => null, environment: "prod" }),
    /VPN_SEQUENCE_AUTHORITY_REDIS_EVAL_REQUIRED/
  );
  assert.throws(
    () => new RedisVpnLeaseSequenceAuthority({ getValue() {}, evalScript() {}, environment: "PROD!" }),
    /VPN_SEQUENCE_AUTHORITY_ENVIRONMENT_INVALID/
  );
});

test("read fails closed before any snapshot commit exists", async () => {
  const { authority: sequenceAuthority } = authority();
  await assert.rejects(
    sequenceAuthority.readCommit("fr-par-01"),
    /VPN_SEQUENCE_AUTHORITY_SEQUENCE_MISSING/
  );
});

test("commit is monotonic and idempotent only for the exact snapshot digest", async () => {
  const { authority: sequenceAuthority } = authority();

  assert.deepEqual(await sequenceAuthority.commitSnapshot("fr-par-01", 3, D1), {
    sequence: 3,
    snapshotDigest: D1,
  });
  assert.deepEqual(await sequenceAuthority.readCommit("fr-par-01"), {
    sequence: 3,
    snapshotDigest: D1,
  });
  assert.deepEqual(await sequenceAuthority.commitSnapshot("fr-par-01", 3, D1), {
    sequence: 3,
    snapshotDigest: D1,
  });
  await assert.rejects(
    sequenceAuthority.commitSnapshot("fr-par-01", 3, D2),
    /VPN_SEQUENCE_AUTHORITY_SNAPSHOT_CONFLICT/
  );
  assert.deepEqual(await sequenceAuthority.commitSnapshot("fr-par-01", 4, D2), {
    sequence: 4,
    snapshotDigest: D2,
  });
  await assert.rejects(
    sequenceAuthority.commitSnapshot("fr-par-01", 2, D3),
    /VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED/
  );
});

test("separate authority instances cannot accept divergent state at the same sequence", async () => {
  const redis = fakeRedis();
  const first = authority(redis).authority;
  const second = authority(redis).authority;

  await first.commitSnapshot("fr-par-01", 7, D1);
  await assert.rejects(
    second.commitSnapshot("fr-par-01", 7, D2),
    /VPN_SEQUENCE_AUTHORITY_SNAPSHOT_CONFLICT/
  );
  assert.deepEqual(await second.readCommit("fr-par-01"), {
    sequence: 7,
    snapshotDigest: D1,
  });
});

test("exact snapshot invalidation changes only the digest at the same sequence", async () => {
  const { authority: sequenceAuthority } = authority();
  await sequenceAuthority.commitSnapshot("fr-par-01", 9, D1);
  assert.deepEqual(
    await sequenceAuthority.invalidateSnapshot("fr-par-01", 9, D1, D2),
    { sequence: 9, snapshotDigest: D2 }
  );
  assert.deepEqual(await sequenceAuthority.readCommit("fr-par-01"), {
    sequence: 9,
    snapshotDigest: D2,
  });
  await assert.rejects(
    sequenceAuthority.invalidateSnapshot("fr-par-01", 9, D1, D3),
    /VPN_SEQUENCE_AUTHORITY_SNAPSHOT_CONFLICT/
  );
});

test("stored values must be canonical positive safe integer plus digest", async () => {
  for (const raw of [
    `0:${D1}`,
    `1.5:${D1}`,
    `01:${D1}`,
    `9007199254740992:${D1}`,
    "1:not-a-digest",
  ]) {
    assert.throws(() => parseStoredCommit(raw), /STORED_VALUE_INVALID/);
  }

  assert.doesNotMatch(COMMIT_SCRIPT, /tonumber/);
  assert.doesNotMatch(INVALIDATE_SCRIPT, /tonumber/);

  const redis = fakeRedis();
  const { authority: sequenceAuthority } = authority(redis);
  redis.values.set("sentinel:prod-eu:vpn-lease-sequence:fr-par-01", `01:${D1}`);
  await assert.rejects(
    sequenceAuthority.readCommit("fr-par-01"),
    /VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID/
  );
  await assert.rejects(
    sequenceAuthority.commitSnapshot("fr-par-01", 2, D2),
    /VPN_SEQUENCE_AUTHORITY_PROTOCOL_ERROR/
  );
});

test("reply parsers reject malformed, rollback and conflict protocols", () => {
  assert.throws(() => parseCommitReply(["PROTOCOL_ERROR", "", ""]), /PROTOCOL_ERROR/);
  assert.throws(() => parseCommitReply(["ROLLBACK", "3", D1]), /ROLLBACK_REJECTED/);
  assert.throws(() => parseCommitReply(["CONFLICT", "3", D1]), /SNAPSHOT_CONFLICT/);
  assert.throws(() => parseInvalidationReply(["MISSING", "", ""]), /SEQUENCE_MISSING/);
  assert.throws(() => parseInvalidationReply(["STALE", "3", D1]), /INVALIDATION_STALE/);
});

test("invalid gateway, sequence and digest are rejected before Redis", async () => {
  const { authority: sequenceAuthority } = authority();
  await assert.rejects(
    sequenceAuthority.commitSnapshot("../gateway", 1, D1),
    /VPN_SEQUENCE_AUTHORITY_GATEWAY_INVALID/
  );
  await assert.rejects(
    sequenceAuthority.commitSnapshot("fr-par-01", 0, D1),
    /VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID/
  );
  await assert.rejects(
    sequenceAuthority.commitSnapshot("fr-par-01", 1, "bad"),
    /VPN_SEQUENCE_AUTHORITY_DIGEST_INVALID/
  );
});
