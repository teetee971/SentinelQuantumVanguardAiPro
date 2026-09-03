import test from 'node:test';
import assert from 'node:assert/strict';
import {
  authorizeBoundExecutionStart,
  createExecutionRecord,
  transitionBoundExecution,
  verifyExecutionBinding,
} from './execution-binding.js';
import { createInMemoryReplayGuard } from './anti-replay.js';

function operation(overrides = {}) {
  return {
    action_id: 'a1',
    authorization_id: 'auth-1',
    action: 'block',
    target_id: 'target-1',
    policy_version: 'policy-1',
    input_hash: 'input-1',
    ...overrides,
  };
}

function readyRecord() {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const validated = transitionBoundExecution(created.record, operation(), 'VALIDATED', '2026-09-03T12:00:01.000Z');
  const authorized = transitionBoundExecution(validated.record, operation(), 'AUTHORIZED', '2026-09-03T12:00:02.000Z');
  const approved = transitionBoundExecution(authorized.record, operation(), 'APPROVED', '2026-09-03T12:00:03.000Z');
  return transitionBoundExecution(approved.record, operation(), 'READY', '2026-09-03T12:00:04.000Z');
}

test('creates a PROPOSED execution record bound to the operation digest', () => {
  const result = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'PROPOSED');
  assert.match(result.record.operation_digest, /^[a-f0-9]{64}$/);
  assert.equal(result.record.authorization_id, 'auth-1');
});

test('rejects an operation mutation before transition', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const mutated = verifyExecutionBinding(created.record, operation({ target_id: 'target-critical' }));
  assert.equal(mutated.valid, false);
  assert.equal(mutated.reason, 'OPERATION_DIGEST_MISMATCH');
});

test('rejects action-id substitution even if a new digest is supplied', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const substituted = operation({ action_id: 'attacker-action' });
  const result = verifyExecutionBinding(created.record, substituted);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ACTION_ID_MISMATCH');
});

test('rejects authorization-id substitution even if the operation remains otherwise valid', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const substituted = operation({ authorization_id: 'attacker-auth' });
  const result = verifyExecutionBinding(created.record, substituted);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'AUTHORIZATION_ID_MISMATCH');
});

test('permits a bound forward transition', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const result = transitionBoundExecution(created.record, operation(), 'VALIDATED', '2026-09-03T12:00:01.000Z');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'VALIDATED');
  assert.equal(result.record.operation_digest, created.record.operation_digest);
});

test('blocks READY to EXECUTING when the operation changes', () => {
  const ready = readyRecord();
  const mutated = transitionBoundExecution(ready.record, operation({ policy_version: 'policy-2' }), 'EXECUTING', '2026-09-03T12:00:05.000Z');
  assert.equal(mutated.valid, false);
  assert.equal(mutated.reason, 'OPERATION_DIGEST_MISMATCH');
});

test('consumes the authorization before entering EXECUTING', () => {
  const ready = readyRecord();
  const guard = createInMemoryReplayGuard();
  const result = authorizeBoundExecutionStart(ready.record, operation(), guard, '2026-09-03T12:00:05.000Z');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'EXECUTING');
  assert.equal(result.record.authorization_id, 'auth-1');
  assert.equal(result.record.operation_digest, ready.record.operation_digest);
});

test('fails closed on replay at the final execution boundary', () => {
  const ready = readyRecord();
  const guard = createInMemoryReplayGuard();
  assert.equal(authorizeBoundExecutionStart(ready.record, operation(), guard, '2026-09-03T12:00:05.000Z').valid, true);
  const second = authorizeBoundExecutionStart(ready.record, operation(), guard, '2026-09-03T12:00:06.000Z');
  assert.equal(second.valid, false);
  assert.equal(second.reason, 'REPLAY_DETECTED');
});

test('fails closed when no replay guard is supplied', () => {
  const ready = readyRecord();
  const result = authorizeBoundExecutionStart(ready.record, operation(), null, '2026-09-03T12:00:05.000Z');
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ANTI_REPLAY_GUARD_REQUIRED');
});
