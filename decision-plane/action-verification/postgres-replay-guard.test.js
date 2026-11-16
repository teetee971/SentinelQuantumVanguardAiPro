import test from 'node:test';
import assert from 'node:assert/strict';
import { createPostgresReplayGuard } from './postgres-replay-guard.js';

test('fails closed when no durable executor is supplied', async () => {
  const guard = createPostgresReplayGuard();
  assert.deepEqual(await guard.consumeAtomically('authorization:auth-1'), {
    valid: false,
    reason: 'DURABLE_REPLAY_EXECUTOR_REQUIRED',
  });
});

test('consumes only when the database reports an inserted row', async () => {
  let request;
  const guard = createPostgresReplayGuard({
    execute: async (query) => {
      request = query;
      return { rows: [{ replay_key: 'authorization:auth-1' }] };
    },
  });

  assert.deepEqual(await guard.consumeAtomically('authorization:auth-1'), {
    valid: true,
    reason: 'REPLAY_KEY_CONSUMED',
  });
  assert.match(request.text, /ON CONFLICT \(replay_key\) DO NOTHING/);
  assert.deepEqual(request.values, ['authorization:auth-1']);
});

test('treats zero inserted rows as replay', async () => {
  const guard = createPostgresReplayGuard({
    execute: async () => ({ rows: [] }),
  });
  assert.deepEqual(await guard.consumeAtomically('approval:approval-1'), {
    valid: false,
    reason: 'REPLAY_DETECTED',
  });
});

test('fails closed when the durable store is unavailable', async () => {
  const guard = createPostgresReplayGuard({
    execute: async () => { throw new Error('database unavailable'); },
  });
  assert.deepEqual(await guard.consumeAtomically('authorization:auth-1'), {
    valid: false,
    reason: 'REPLAY_STORE_UNAVAILABLE',
  });
});

test('rejects invalid replay keys before database access', async () => {
  let called = false;
  const guard = createPostgresReplayGuard({
    execute: async () => { called = true; return { rows: [] }; },
  });
  assert.deepEqual(await guard.consumeAtomically(''), {
    valid: false,
    reason: 'INVALID_REPLAY_KEY',
  });
  assert.equal(called, false);
});
