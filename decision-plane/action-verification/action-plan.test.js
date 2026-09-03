import assert from 'node:assert/strict';
import test from 'node:test';
import { validateActionPlan, verifyActionPlan, verifyPostconditions } from './action-plan.js';

const NOW = Date.parse('2026-09-03T12:00:00.000Z');
const plan = {
  action: 'contain', target_id: 'asset-1', policy_version: 'policy-v1',
  preconditions: ['authorized'], postconditions: ['verified'],
  rollback: { enabled: true, reference: 'rollback-v1' },
  execution_adapter: {
    adapter_id: 'adapter-contain-v1', status: 'validated', source: 'system', action: 'contain',
    target_id: 'asset-1', policy_version: 'policy-v1', expires_at: '2026-09-03T13:00:00.000Z',
  },
};

test('accepts structurally valid validated adapter plan', () => assert.equal(validateActionPlan(plan, NOW).valid, true));
test('rejects plan without rollback', () => assert.equal(validateActionPlan({ ...plan, rollback: { enabled: false } }, NOW).valid, false));
test('rejects plan without policy version', () => assert.equal(validateActionPlan({ ...plan, policy_version: undefined }, NOW).reason, 'POLICY_VERSION_REQUIRED'));
test('rejects unknown action', () => assert.equal(validateActionPlan({ ...plan, action: 'arbitrary-execution' }, NOW).reason, 'UNKNOWN_ACTION'));
test('rejects legacy boolean execution adapter approval', () => {
  const result = validateActionPlan({ ...plan, execution_adapter: { approved: true } }, NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'EXECUTION_ADAPTER_BOOLEAN_APPROVAL_FORBIDDEN');
});
test('rejects adapter bound to another target', () => {
  const result = validateActionPlan({
    ...plan,
    execution_adapter: { ...plan.execution_adapter, target_id: 'asset-2' },
  }, NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'EXECUTION_ADAPTER_TARGET_MISMATCH');
});
test('rejects adapter bound to another policy', () => {
  const result = validateActionPlan({
    ...plan,
    execution_adapter: { ...plan.execution_adapter, policy_version: 'policy-v2' },
  }, NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'EXECUTION_ADAPTER_POLICY_MISMATCH');
});
test('rejects expired execution adapter', () => {
  const result = validateActionPlan({
    ...plan,
    execution_adapter: { ...plan.execution_adapter, expires_at: '2026-09-03T11:59:59.000Z' },
  }, NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'EXECUTION_ADAPTER_EXPIRED');
});
test('fails precondition verification closed', () => {
  const result = verifyActionPlan(plan, { authorized: false, verified: true }, NOW);
  assert.equal(result.reason, 'PRECONDITION_FAILED');
});
test('verifies execution preconditions without requiring future postconditions', () => {
  const result = verifyActionPlan(plan, { authorized: true, verified: false }, NOW);
  assert.equal(result.valid, true);
  assert.equal(result.reason, 'PRECONDITIONS_VERIFIED');
});
test('verifies postconditions separately after execution', () => {
  const result = verifyPostconditions(plan, { authorized: true, verified: true }, NOW);
  assert.equal(result.valid, true);
  assert.equal(result.reason, 'POSTCONDITIONS_VERIFIED');
});
test('rejects missing postcondition after execution', () => {
  const result = verifyPostconditions(plan, { authorized: true, verified: false }, NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'POSTCONDITION_NOT_VERIFIED');
});

test('rejects required plan fields inherited from Object.prototype', () => {
  const polluted = {};
  Object.defineProperty(Object.prototype, 'action', { value: 'polluted', configurable: true });
  try {
    const incomplete = {
      target_id: 'asset-1', policy_version: 'policy-v1', preconditions: [], postconditions: [],
      rollback: { enabled: true, reference: 'rollback-v1' }, execution_adapter: plan.execution_adapter,
    };
    assert.equal(validateActionPlan(incomplete, NOW).valid, false);
    assert.equal(validateActionPlan(incomplete, NOW).reason, 'MISSING_ACTION');
  } finally {
    delete Object.prototype.action;
  }
  assert.equal(Object.hasOwn(polluted, 'action'), false);
});

test('rejects state values inherited from Object.prototype', () => {
  Object.defineProperty(Object.prototype, 'authorized', { value: true, configurable: true });
  try {
    const result = verifyActionPlan(plan, { verified: true }, NOW);
    assert.equal(result.valid, false);
    assert.equal(result.reason, 'PRECONDITION_FAILED');
    assert.deepEqual(result.failed, ['authorized']);
  } finally {
    delete Object.prototype.authorized;
  }
});

test('rejects oversized action-plan fields', () => {
  assert.equal(validateActionPlan({ ...plan, action: 'x'.repeat(129) }, NOW).reason, 'INVALID_ACTION_TARGET');
  assert.equal(validateActionPlan({ ...plan, target_id: 'x'.repeat(257) }, NOW).reason, 'INVALID_ACTION_TARGET');
  assert.equal(validateActionPlan({ ...plan, preconditions: ['x'.repeat(129)] }, NOW).reason, 'INVALID_CONDITION_LIST');
  assert.equal(validateActionPlan({ ...plan, preconditions: Array.from({ length: 65 }, () => 'x') }, NOW).reason, 'INVALID_CONDITION_LIST');
  assert.equal(validateActionPlan({ ...plan, rollback: { enabled: true, reference: 'x'.repeat(257) } }, NOW).reason, 'ROLLBACK_REQUIRED');
});
