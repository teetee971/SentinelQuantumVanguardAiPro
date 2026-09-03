import test from 'node:test';
import assert from 'node:assert/strict';
import {
  createExecutionRecord,
  transitionBoundExecution,
  verifyExecutionBinding,
} from './execution-binding.js';

function operation(overrides = {}) {
  return {
    action_id: 'a1',
    action: 'block',
    target_id: 'target-1',
    policy_version: 'policy-1',
    input_hash: 'input-1',
    ...overrides,
  };
}

test('creates a PROPOSED execution record bound to the operation digest', () => {
  const result = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'PROPOSED');
  assert.match(result.record.operation_digest, /^[a-f0-9]{64}$/);
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

test('permits a bound forward transition', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const result = transitionBoundExecution(created.record, operation(), 'VALIDATED', '2026-09-03T12:00:01.000Z');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'VALIDATED');
  assert.equal(result.record.operation_digest, created.record.operation_digest);
});

test('blocks READY to EXECUTING when the operation changes', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const validated = transitionBoundExecution(created.record, operation(), 'VALIDATED', '2026-09-03T12:00:01.000Z');
  const authorized = transitionBoundExecution(validated.record, operation(), 'AUTHORIZED', '2026-09-03T12:00:02.000Z');
  const approved = transitionBoundExecution(authorized.record, operation(), 'APPROVED', '2026-09-03T12:00:03.000Z');
  const ready = transitionBoundExecution(approved.record, operation(), 'READY', '2026-09-03T12:00:04.000Z');
  const mutated = transitionBoundExecution(ready.record, operation({ policy_version: 'policy-2' }), 'EXECUTING', '2026-09-03T12:00:05.000Z');
  assert.equal(mutated.valid, false);
  assert.equal(mutated.reason, 'OPERATION_DIGEST_MISMATCH');
});
