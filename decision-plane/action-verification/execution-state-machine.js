const STATES = Object.freeze([
  'PROPOSED', 'VALIDATED', 'AUTHORIZED', 'APPROVED', 'READY', 'EXECUTING', 'COMPLETED', 'FAILED',
]);
const TRANSITIONS = Object.freeze({
  PROPOSED: ['VALIDATED'], VALIDATED: ['AUTHORIZED'], AUTHORIZED: ['APPROVED'], APPROVED: ['READY'],
  READY: ['EXECUTING'], EXECUTING: ['COMPLETED', 'FAILED'], COMPLETED: [], FAILED: [],
});
const TERMINAL_STATES = new Set(['COMPLETED', 'FAILED']);
export function isExecutionState(value) { return typeof value === 'string' && STATES.includes(value); }
export function isTerminalExecutionState(value) { return TERMINAL_STATES.has(value); }
export function canTransition(from, to) { return isExecutionState(from) && isExecutionState(to) && TRANSITIONS[from].includes(to); }
export function transitionExecutionState(record, nextState) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) return { valid: false, reason: 'INVALID_EXECUTION_RECORD' };
  if (!isExecutionState(record.state) || !isExecutionState(nextState)) return { valid: false, reason: 'INVALID_EXECUTION_STATE' };
  if (!canTransition(record.state, nextState)) return { valid: false, reason: isTerminalExecutionState(record.state) ? 'TERMINAL_STATE_IMMUTABLE' : 'INVALID_EXECUTION_TRANSITION' };
  return { valid: true, reason: 'EXECUTION_TRANSITION_ALLOWED', record: Object.freeze({ ...record, state: nextState }) };
}
export { STATES as EXECUTION_STATES };
