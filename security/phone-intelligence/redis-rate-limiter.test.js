import test from 'node:test';
import assert from 'node:assert/strict';
import { createRedisRateLimiter, REDIS_SLIDING_WINDOW_SCRIPT } from './redis-rate-limiter.js';

const subjectHmacKey = 'k'.repeat(32);

test('fails closed without Redis', async () => {
  const result = await createRedisRateLimiter().consume({ ip_subject: 'ip-1', endpoint: '/sync/rules' });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'REDIS_RATE_LIMIT_EXECUTOR_REQUIRED');
});

test('uses one atomic Redis script and server time for all scopes', async () => {
  let request;
  const limiter = createRedisRateLimiter({ subjectHmacKey, execute: async (value) => { request = value; return [1, 0, 0]; } });
  const result = await limiter.consume({ ip_subject: '203.0.113.10', user_subject: 'user-1', endpoint: '/sync/rules' });
  assert.equal(result.allowed, true);
  assert.equal(request.script, REDIS_SLIDING_WINDOW_SCRIPT);
  assert.match(request.script, /redis\.call\('TIME'\)/);
  assert.match(request.script, /ZREMRANGEBYSCORE/);
  assert.match(request.script, /ZCARD/);
  assert.match(request.script, /ZADD/);
  assert.match(request.script, /max_window_ms/);
  assert.equal(request.keys.length, 5);
  assert.equal(request.keys.every((key) => key.startsWith('{sentinel-phone-rate-limit}:')), true);
  assert.equal(request.keys.some((key) => key.includes('203.0.113.10')), false);
  assert.deepEqual(request.arguments.slice(-2), ['60000', '30']);
});

test('returns the precise blocked scope and bounded retry', async () => {
  const limiter = createRedisRateLimiter({ subjectHmacKey, execute: async () => [0, 3, 2500] });
  const result = await limiter.consume({ ip_subject: 'ip-1', user_subject: 'user-1', endpoint: '/auth/login' });
  assert.deepEqual(result, { allowed: false, reason: 'REDIS_RATE_LIMITED', scope: 'user', retry_after_ms: 2500 });
});

test('sensitive endpoints have independent policies', async () => {
  const captured = [];
  const limiter = createRedisRateLimiter({ subjectHmacKey, execute: async (value) => { captured.push(value); return [1, 0, 0]; } });
  await limiter.consume({ ip_subject: 'ip-1', endpoint: '/auth/login' });
  await limiter.consume({ ip_subject: 'ip-1', endpoint: '/publication/daily' });
  await limiter.consume({ ip_subject: 'ip-1', endpoint: '/sync/rules' });
  await limiter.consume({ ip_subject: 'ip-1', endpoint: '/reports' });
  assert.equal(captured[0].arguments.at(-1), '10');
  assert.equal(captured[1].arguments.at(-1), '5');
  assert.equal(captured[2].arguments.at(-1), '30');
  assert.equal(captured[3].arguments.at(-1), '120');
});

test('invalid inputs, invalid Redis replies and outages fail closed', async () => {
  let calls = 0;
  const limiter = createRedisRateLimiter({ subjectHmacKey, execute: async () => { calls += 1; return ['unexpected']; } });
  assert.equal((await limiter.consume({ ip_subject: '', endpoint: '/sync/rules' })).reason, 'REDIS_RATE_LIMIT_INPUT_INVALID');
  assert.equal((await limiter.consume({ ip_subject: 'ip-1', user_subject: null, endpoint: '/sync/rules' })).reason, 'REDIS_RATE_LIMIT_INPUT_INVALID');
  assert.equal(calls, 0);
  assert.equal((await limiter.consume({ ip_subject: 'ip-1', endpoint: '/sync/rules' })).reason, 'REDIS_RATE_LIMIT_RESPONSE_INVALID');
  const outage = createRedisRateLimiter({ subjectHmacKey, execute: async () => { throw new Error('offline'); } });
  assert.equal((await outage.consume({ ip_subject: 'ip-1', endpoint: '/sync/rules' })).reason, 'REDIS_RATE_LIMIT_UNAVAILABLE');
});

test('rejects unsafe policy configuration', () => {
  assert.throws(() => createRedisRateLimiter({ subjectHmacKey, execute: async () => [1, 0, 0], policies: {} }), /REDIS_RATE_LIMIT_CONFIG_INVALID/);
  assert.throws(() => createRedisRateLimiter({ subjectHmacKey: 'short', execute: async () => [1, 0, 0] }), /REDIS_SUBJECT_HMAC_KEY_INVALID/);
});
