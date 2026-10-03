import assert from "node:assert/strict";
import test from "node:test";
import {
  RedisVpnLeaseSequenceAuthority,
  parseCommitReply,
  parseStoredSequence,
} from "./redis-lease-sequence-authority.js";

function fakeRedis() {
  const values = new Map();
  return {
    values,
    async getValue(key) {
      return values.get(key) ?? null;
    },
    async evalScript({ keys, args }) {
      assert.equal(keys.length, 1);
      assert.equal(args.length, 1);
      const key = keys[0];
      const proposed = Number(args[0]);
      const existingRaw = values.get(key);
      if (existingRaw !== undefined) {
        const current = Number(existingRaw);
        if (!Number.isSafeInteger(current) || current < 1) return ["PROTOCOL_ERROR", existingRaw];
        if (proposed < current) return ["ROLLBACK", String(current)];
        if (proposed === current) return ["OK", String(current)];
      }
      values.set(key, String(proposed));
      return ["OK", String(proposed)];
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

test("read fails closed before any monotonic sequence exists", async () => {
  const { authority: sequenceAuthority } = authority();
  await assert.rejects(
    sequenceAuthority.readMinimumSequence("fr-par-01"),
    /VPN_SEQUENCE_AUTHORITY_SEQUENCE_MISSING/
  );
});

test("commit is monotonic, idempotent and rejects rollback", async () => {
  const { authority: sequenceAuthority } = authority();

  assert.equal(await sequenceAuthority.commitSequence("fr-par-01", 3), 3);
  assert.equal(await sequenceAuthority.readMinimumSequence("fr-par-01"), 3);
  assert.equal(await sequenceAuthority.commitSequence("fr-par-01", 3), 3);
  assert.equal(await sequenceAuthority.commitSequence("fr-par-01", 4), 4);
  assert.equal(await sequenceAuthority.readMinimumSequence("fr-par-01"), 4);

  await assert.rejects(
    sequenceAuthority.commitSequence("fr-par-01", 2),
    /VPN_SEQUENCE_AUTHORITY_ROLLBACK_REJECTED/
  );
  assert.equal(await sequenceAuthority.readMinimumSequence("fr-par-01"), 4);
});

test("separate authority instances share the durable Redis sequence", async () => {
  const redis = fakeRedis();
  const first = authority(redis).authority;
  const second = authority(redis).authority;

  await first.commitSequence("fr-par-01", 7);
  assert.equal(await second.readMinimumSequence("fr-par-01"), 7);
  await second.commitSequence("fr-par-01", 8);
  assert.equal(await first.readMinimumSequence("fr-par-01"), 8);
});

test("invalid stored/protocol values never become trusted sequences", async () => {
  assert.throws(() => parseStoredSequence("0"), /STORED_VALUE_INVALID/);
  assert.throws(() => parseStoredSequence("1.5"), /STORED_VALUE_INVALID/);
  assert.throws(() => parseCommitReply(["PROTOCOL_ERROR", "x"]), /PROTOCOL_ERROR/);

  const redis = fakeRedis();
  const { authority: sequenceAuthority } = authority(redis);
  redis.values.set("sentinel:prod-eu:vpn-lease-sequence:fr-par-01", "corrupt");
  await assert.rejects(
    sequenceAuthority.readMinimumSequence("fr-par-01"),
    /VPN_SEQUENCE_AUTHORITY_STORED_VALUE_INVALID/
  );
});

test("invalid gateway ids and sequences are rejected before Redis", async () => {
  const { authority: sequenceAuthority } = authority();
  await assert.rejects(
    sequenceAuthority.commitSequence("../gateway", 1),
    /VPN_SEQUENCE_AUTHORITY_GATEWAY_INVALID/
  );
  await assert.rejects(
    sequenceAuthority.commitSequence("fr-par-01", 0),
    /VPN_SEQUENCE_AUTHORITY_SEQUENCE_INVALID/
  );
});
