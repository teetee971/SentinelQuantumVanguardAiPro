import test from 'node:test';
import assert from 'node:assert/strict';
import { createRedisReplayGuard, MAX_TTL_MS, MIN_TTL_MS } from './redis-replay-guard.js';

test('uses one namespaced hashed key with atomic NX/PX semantics', async () => {
  let request;
  const guard = createRedisReplayGuard({
    environment: 'production',
    ttlMs: 120_000,
    setNxPx: async (value) => { request = value; return 'OK'; },
  });
  assert.deepEqual(await guard.consumeAtomically('soc_event:event-1'), {
    valid: true,
    reason: 'REPLAY_KEY_CONSUMED',
  });
  assert.match(request.key, /^sentinel:production:soc-replay:[a-f0-9]{64}$/);
  assert.doesNotMatch(request.key, /event-1/);
  assert.deepEqual({ value: request.value, ttlMs: request.ttlMs }, { value: '1', ttlMs: 120_000 });
});

test('isolates identical event identifiers by environment', async () => {
  const keys = [];
  for (const environment of ['test', 'production']) {
    const guard = createRedisReplayGuard({
      environment,
      setNxPx: async ({ key }) => { keys.push(key); return 'OK'; },
    });
    await guard.consumeAtomically('soc_event:event-1');
  }
  assert.notEqual(keys[0], keys[1]);
  assert.match(keys[0], /^sentinel:test:/);
  assert.match(keys[1], /^sentinel:production:/);
});

test('treats an existing Redis key as replay', async () => {
  const guard = createRedisReplayGuard({
    environment: 'production',
    setNxPx: async () => null,
  });
  assert.equal((await guard.consumeAtomically('soc_event:event-1')).reason, 'REPLAY_DETECTED');
});

test('fails closed on Redis outage or unexpected protocol response', async () => {
  const unavailable = createRedisReplayGuard({
    environment: 'production',
    setNxPx: async () => { throw new Error('offline'); },
  });
  assert.equal((await unavailable.consumeAtomically('soc_event:event-1')).reason, 'REPLAY_STORE_UNAVAILABLE');

  const invalid = createRedisReplayGuard({
    environment: 'production',
    setNxPx: async () => 'QUEUED',
  });
  assert.equal((await invalid.consumeAtomically('soc_event:event-1')).reason, 'REPLAY_STORE_PROTOCOL_ERROR');
});

test('rejects missing executors, unsafe namespaces and invalid TTLs', async () => {
  assert.equal((await createRedisReplayGuard({ environment: 'production' }).consumeAtomically('x')).reason, 'REDIS_SET_NX_PX_REQUIRED');
  assert.equal((await createRedisReplayGuard({ environment: '../prod', setNxPx: async () => 'OK' }).consumeAtomically('x')).reason, 'REPLAY_ENVIRONMENT_INVALID');
  assert.equal((await createRedisReplayGuard({ environment: 'prod', namespace: 'bad:scope', setNxPx: async () => 'OK' }).consumeAtomically('x')).reason, 'REPLAY_NAMESPACE_INVALID');
  assert.equal((await createRedisReplayGuard({ environment: 'prod', ttlMs: MIN_TTL_MS - 1, setNxPx: async () => 'OK' }).consumeAtomically('x')).reason, 'REPLAY_TTL_INVALID');
  assert.equal((await createRedisReplayGuard({ environment: 'prod', ttlMs: MAX_TTL_MS + 1, setNxPx: async () => 'OK' }).consumeAtomically('x')).reason, 'REPLAY_TTL_INVALID');
});

test('rejects invalid raw keys before contacting Redis', async () => {
  let calls = 0;
  const guard = createRedisReplayGuard({
    environment: 'production',
    setNxPx: async () => { calls += 1; return 'OK'; },
  });
  assert.equal((await guard.consumeAtomically('')).reason, 'INVALID_REPLAY_KEY');
  assert.equal((await guard.consumeAtomically('x'.repeat(513))).reason, 'INVALID_REPLAY_KEY');
  assert.equal(calls, 0);
});
