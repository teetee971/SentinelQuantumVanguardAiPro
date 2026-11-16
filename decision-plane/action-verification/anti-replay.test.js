import test from 'node:test';
import assert from 'node:assert/strict';
import {
  consumeAuthorizationOnce,
  createInMemoryReplayGuard,
} from './anti-replay.js';

test('consumes an authorization only once', () => {
  const guard = createInMemoryReplayGuard();
  assert.deepEqual(consumeAuthorizationOnce(guard, 'auth-1'), {
    valid: true,
    reason: 'REPLAY_KEY_CONSUMED',
  });
  assert.deepEqual(consumeAuthorizationOnce(guard, 'auth-1'), {
    valid: false,
    reason: 'REPLAY_DETECTED',
  });
});

test('keeps distinct authorization identifiers independent', () => {
  const guard = createInMemoryReplayGuard();
  assert.equal(consumeAuthorizationOnce(guard, 'auth-1').valid, true);
  assert.equal(consumeAuthorizationOnce(guard, 'auth-2').valid, true);
  assert.equal(guard.hasConsumed('authorization:auth-1'), true);
  assert.equal(guard.hasConsumed('authorization:auth-2'), true);
});

test('fails closed without a replay guard or with an invalid identifier', () => {
  assert.deepEqual(consumeAuthorizationOnce(null, 'auth-1'), {
    valid: false,
    reason: 'ANTI_REPLAY_GUARD_REQUIRED',
  });
  const guard = createInMemoryReplayGuard();
  assert.deepEqual(consumeAuthorizationOnce(guard, ''), {
    valid: false,
    reason: 'INVALID_AUTHORIZATION_ID',
  });
});

test('does not confuse similarly prefixed replay domains', () => {
  const guard = createInMemoryReplayGuard();
  assert.equal(guard.consumeAtomically('authorization:auth-1').valid, true);
  assert.equal(guard.consumeAtomically('approval:auth-1').valid, true);
  assert.equal(guard.consumeAtomically('authorization:auth-1').valid, false);
});
