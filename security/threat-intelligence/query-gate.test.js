import assert from 'node:assert/strict';
import test from 'node:test';
import { createThreatIntelQueryGate } from './query-gate.js';

test('caches identical source/query results within TTL', async () => {
  let now = 1000;
  let calls = 0;
  const gate = createThreatIntelQueryGate({ minIntervalMs: 1000, cacheTtlMs: 5000, now: () => now });
  const first = await gate.run({ sourceId: 'malwarebazaar', queryKey: 'sha256:a', execute: async () => ({ value: ++calls }) });
  const second = await gate.run({ sourceId: 'malwarebazaar', queryKey: 'sha256:a', execute: async () => ({ value: ++calls }) });
  assert.equal(first.cache_hit, false);
  assert.equal(second.cache_hit, true);
  assert.equal(calls, 1);
  assert.deepEqual(second.result, { value: 1 });
});

test('enforces minimum interval per canonical source across different queries', async () => {
  let now = 1000;
  const gate = createThreatIntelQueryGate({ minIntervalMs: 1000, cacheTtlMs: 0, now: () => now });
  await gate.run({ sourceId: 'VirusShare', queryKey: 'one', execute: async () => 1 });
  await assert.rejects(
    gate.run({ sourceId: 'virusshare', queryKey: 'two', execute: async () => 2 }),
    error => error.message === 'THREAT_INTEL_RATE_LIMITED' && error.retry_after_ms === 1000
  );
  now = 2000;
  const result = await gate.run({ sourceId: 'virusshare', queryKey: 'two', execute: async () => 2 });
  assert.equal(result.result, 2);
});

test('does not rate-limit independent sources against each other', async () => {
  const gate = createThreatIntelQueryGate({ minIntervalMs: 1000, cacheTtlMs: 0, now: () => 1000 });
  const a = await gate.run({ sourceId: 'malwarebazaar', queryKey: 'x', execute: async () => 'a' });
  const b = await gate.run({ sourceId: 'virusshare', queryKey: 'x', execute: async () => 'b' });
  assert.equal(a.result, 'a');
  assert.equal(b.result, 'b');
});

test('coalesces concurrent identical requests into one execution', async () => {
  let calls = 0;
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const gate = createThreatIntelQueryGate({ minIntervalMs: 1000, cacheTtlMs: 5000, now: () => 1000 });
  const execute = async () => { calls += 1; await pending; return 'ok'; };
  const one = gate.run({ sourceId: 'malwarebazaar', queryKey: 'same', execute });
  const two = gate.run({ sourceId: 'malwarebazaar', queryKey: 'same', execute });
  release();
  const [a, b] = await Promise.all([one, two]);
  assert.equal(calls, 1);
  assert.deepEqual(a, b);
});

test('failed requests still consume the source interval but are not cached', async () => {
  let now = 1000;
  let calls = 0;
  const gate = createThreatIntelQueryGate({ minIntervalMs: 1000, cacheTtlMs: 5000, now: () => now });
  await assert.rejects(gate.run({ sourceId: 'virusshare', queryKey: 'x', execute: async () => { calls += 1; throw new Error('provider failure'); } }), /provider failure/);
  await assert.rejects(gate.run({ sourceId: 'virusshare', queryKey: 'x', execute: async () => { calls += 1; return 'ok'; } }), /THREAT_INTEL_RATE_LIMITED/);
  now = 2000;
  const result = await gate.run({ sourceId: 'virusshare', queryKey: 'x', execute: async () => { calls += 1; return 'ok'; } });
  assert.equal(result.cache_hit, false);
  assert.equal(result.result, 'ok');
  assert.equal(calls, 2);
});

test('cache capacity is bounded', async () => {
  let now = 1000;
  let calls = 0;
  const gate = createThreatIntelQueryGate({ minIntervalMs: 0, cacheTtlMs: 5000, maxEntries: 2, now: () => now });
  for (const key of ['a', 'b', 'c']) {
    await gate.run({ sourceId: 'source', queryKey: key, execute: async () => ++calls });
    now += 1;
  }
  const again = await gate.run({ sourceId: 'source', queryKey: 'a', execute: async () => ++calls });
  assert.equal(again.cache_hit, false);
  assert.equal(calls, 4);
});

test('cached result is an immutable snapshot', async () => {
  const gate = createThreatIntelQueryGate({ minIntervalMs: 0, cacheTtlMs: 5000, now: () => 1000 });
  const providerResult = { observations: [{ verdict: 'malicious' }] };
  const first = await gate.run({ sourceId: 'virusshare', queryKey: 'x', execute: async () => providerResult });

  providerResult.observations[0].verdict = 'benign';
  assert.throws(() => { first.result.observations[0].verdict = 'benign'; }, TypeError);

  const cached = await gate.run({ sourceId: 'virusshare', queryKey: 'x', execute: async () => ({}) });
  assert.equal(cached.result.observations[0].verdict, 'malicious');
  assert.equal(Object.isFrozen(cached.result.observations[0]), true);
});

test('rejects non-data results instead of caching mutable executable objects', async () => {
  const gate = createThreatIntelQueryGate({ minIntervalMs: 0, cacheTtlMs: 5000, now: () => 1000 });
  await assert.rejects(
    gate.run({ sourceId: 'virusshare', queryKey: 'x', execute: async () => ({ callback() {} }) }),
    /THREAT_INTEL_RESULT_NOT_CACHEABLE/
  );
});

test('clear prevents an older in-flight request from repopulating the cache', async () => {
  let calls = 0;
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const gate = createThreatIntelQueryGate({ minIntervalMs: 0, cacheTtlMs: 5000, now: () => 1000 });

  const first = gate.run({
    sourceId: 'virusshare',
    queryKey: 'x',
    execute: async () => { calls += 1; await pending; return { value: 'old' }; }
  });
  gate.clear();
  release();
  await first;

  const second = await gate.run({
    sourceId: 'virusshare',
    queryKey: 'x',
    execute: async () => { calls += 1; return { value: 'new' }; }
  });
  assert.equal(second.cache_hit, false);
  assert.equal(second.result.value, 'new');
  assert.equal(calls, 2);
});

test('fails closed if the injected clock moves backwards', async () => {
  let now = 1000;
  const gate = createThreatIntelQueryGate({ minIntervalMs: 0, cacheTtlMs: 0, now: () => now });
  await gate.run({ sourceId: 'virusshare', queryKey: 'one', execute: async () => 1 });
  now = 999;
  await assert.rejects(
    gate.run({ sourceId: 'virusshare', queryKey: 'two', execute: async () => 2 }),
    /THREAT_INTEL_CLOCK_ROLLBACK/
  );
});

test('rejects ambiguous source identifiers', async () => {
  const gate = createThreatIntelQueryGate({ minIntervalMs: 0, cacheTtlMs: 0, now: () => 1000 });
  await assert.rejects(
    gate.run({ sourceId: 'virus:share', queryKey: 'x', execute: async () => 1 }),
    /THREAT_INTEL_SOURCE_REQUIRED/
  );
});
