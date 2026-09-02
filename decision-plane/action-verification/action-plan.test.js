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
