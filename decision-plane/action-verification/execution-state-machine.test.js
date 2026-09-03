import test from 'node:test';
import assert from 'node:assert/strict';
import {
  canTransition,
  isExecutionState,
  isTerminalExecutionState,
  transitionExecutionState,
} from './execution-state-machine.js';

test('allows only the canonical forward state transitions', () => {
  assert.equal(canTransition('PROPOSED', 'VALIDATED'), true);
  assert.equal(canTransition('VALIDATED', 'AUTHORIZED'), true);
  assert.equal(canTransition('AUTHORIZED', 'APPROVED'), true);
  assert.equal(canTransition('APPROVED', 'READY'), true);
  assert.equal(canTransition('READY', 'EXECUTING'), true);
  assert.equal(canTransition('EXECUTING', 'COMPLETED'), true);
  assert.equal(canTransition('EXECUTING', 'FAILED'), true);
});

test('rejects direct escalation to EXECUTING or COMPLETED', () => {
  assert.equal(canTransition('PROPOSED', 'EXECUTING'), false);
  assert.equal(canTransition('PROPOSED', 'COMPLETED'), false);
  assert.equal(canTransition('APPROVED', 'EXECUTING'), false);
  assert.equal(canTransition('VALIDATED', 'COMPLETED'), false);
});

test('rejects unknown and terminal-state transitions', () => {
  assert.equal(isExecutionState('EXECUTING'), true);
  assert.equal(isExecutionState('EXECUTED'), false);
  assert.equal(isTerminalExecutionState('COMPLETED'), true);
  assert.equal(isTerminalExecutionState('FAILED'), true);
  assert.equal(isTerminalExecutionState('READY'), false);
  assert.equal(canTransition('COMPLETED', 'EXECUTING'), false);
  assert.equal(canTransition('FAILED', 'READY'), false);
});

test('returns an immutable record for a valid transition', () => {
  const result = transitionExecutionState({ action_id: 'a1', state: 'READY' }, 'EXECUTING');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'EXECUTING');
  assert.equal(Object.isFrozen(result.record), true);
});

test('fails closed on malformed execution records', () => {
  assert.equal(transitionExecutionState(null, 'READY').valid, false);
  assert.equal(transitionExecutionState({ state: 'READY' }, 'UNKNOWN').valid, false);
  assert.equal(transitionExecutionState({ state: 'PROPOSED' }, 'EXECUTING').valid, false);
});
