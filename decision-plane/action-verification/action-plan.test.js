import assert from 'node:assert/strict';
import test from 'node:test';
import { validateActionPlan, verifyActionPlan } from './action-plan.js';

const plan = {
  action: 'contain', target_id: 'asset-1', preconditions: ['authorized'], postconditions: ['verified'],
  rollback: { enabled: true, reference: 'rollback-v1' }, execution_adapter: { approved: true },
};

test('accepts structurally valid approved plan', () => assert.equal(validateActionPlan(plan).valid, true));
test('rejects plan without rollback', () => assert.equal(validateActionPlan({ ...plan, rollback: { enabled: false } }).valid, false));
test('rejects unapproved execution adapter', () => assert.equal(validateActionPlan({ ...plan, execution_adapter: { approved: false } }).valid, false));
test('fails precondition verification closed', () => {
  const result = verifyActionPlan(plan, { authorized: false, verified: true });
  assert.equal(result.reason, 'PRECONDITION_FAILED');
});
test('verifies satisfied pre/postconditions', () => {
  const result = verifyActionPlan(plan, { authorized: true, verified: true });
  assert.equal(result.valid, true);
});

test('rejects required plan fields inherited from Object.prototype', () => {
  const polluted = {};
  Object.defineProperty(Object.prototype, 'action', { value: 'polluted', configurable: true });
  try {
    const incomplete = {
      target_id: 'asset-1', preconditions: [], postconditions: [],
      rollback: { enabled: true, reference: 'rollback-v1' }, execution_adapter: { approved: true },
    };
    assert.equal(validateActionPlan(incomplete).valid, false);
    assert.equal(validateActionPlan(incomplete).reason, 'MISSING_ACTION');
  } finally {
    delete Object.prototype.action;
  }
  assert.equal(Object.hasOwn(polluted, 'action'), false);
});

test('rejects state values inherited from Object.prototype', () => {
  Object.defineProperty(Object.prototype, 'authorized', { value: true, configurable: true });
  try {
    const result = verifyActionPlan(plan, { verified: true });
    assert.equal(result.valid, false);
    assert.equal(result.reason, 'PRECONDITION_FAILED');
    assert.deepEqual(result.failed, ['authorized']);
  } finally {
    delete Object.prototype.authorized;
  }
});

test('rejects oversized action-plan fields', () => {
  assert.equal(validateActionPlan({ ...plan, action: 'x'.repeat(129) }).reason, 'INVALID_ACTION_TARGET');
  assert.equal(validateActionPlan({ ...plan, target_id: 'x'.repeat(257) }).reason, 'INVALID_ACTION_TARGET');
  assert.equal(validateActionPlan({ ...plan, preconditions: ['x'.repeat(129)] }).reason, 'INVALID_CONDITION_LIST');
  assert.equal(validateActionPlan({ ...plan, preconditions: Array.from({ length: 65 }, () => 'x') }).reason, 'INVALID_CONDITION_LIST');
  assert.equal(validateActionPlan({ ...plan, rollback: { enabled: true, reference: 'x'.repeat(257) } }).reason, 'ROLLBACK_REQUIRED');
});
