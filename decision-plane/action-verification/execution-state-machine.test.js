import test from 'node:test';
import assert from 'node:assert/strict';
import { canTransition, isExecutionState, isTerminalExecutionState, transitionExecutionState } from './execution-state-machine.js';
test('allows only canonical forward transitions', () => {
  assert.equal(canTransition('PROPOSED', 'VALIDATED'), true);
  assert.equal(canTransition('VALIDATED', 'AUTHORIZED'), true);
  assert.equal(canTransition('AUTHORIZED', 'APPROVED'), true);
  assert.equal(canTransition('APPROVED', 'READY'), true);
  assert.equal(canTransition('READY', 'EXECUTING'), true);
  assert.equal(canTransition('EXECUTING', 'COMPLETED'), true);
  assert.equal(canTransition('EXECUTING', 'FAILED'), true);
});
test('rejects direct escalation and terminal transitions', () => {
  for (const [from, to] of [['PROPOSED', 'EXECUTING'], ['PROPOSED', 'COMPLETED'], ['APPROVED', 'EXECUTING'], ['VALIDATED', 'COMPLETED'], ['COMPLETED', 'EXECUTING'], ['FAILED', 'READY']]) assert.equal(canTransition(from, to), false);
  assert.equal(isExecutionState('EXECUTING'), true);
  assert.equal(isExecutionState('EXECUTED'), false);
  assert.equal(isTerminalExecutionState('COMPLETED'), true);
});
test('returns an immutable record on a valid transition', () => {
  const result = transitionExecutionState({ action_id: 'a1', state: 'READY' }, 'EXECUTING');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'EXECUTING');
  assert.equal(Object.isFrozen(result.record), true);
});
test('fails closed on malformed records', () => {
  assert.equal(transitionExecutionState(null, 'READY').valid, false);
  assert.equal(transitionExecutionState({ state: 'READY' }, 'UNKNOWN').valid, false);
  assert.equal(transitionExecutionState({ state: 'PROPOSED' }, 'EXECUTING').valid, false);
});
