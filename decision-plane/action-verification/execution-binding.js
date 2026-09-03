import { computeOperationDigest, verifyOperationDigest } from './operation-digest.js';
import { canTransition, isExecutionState } from './execution-state-machine.js';

const MAX_ID_LENGTH = 256;
const MAX_TIMESTAMP_LENGTH = 64;

function validString(value, maxLength) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;
}

function validDigest(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
}

export function createExecutionRecord(operation, now = new Date().toISOString()) {
  const digest = computeOperationDigest(operation);
  if (!digest.valid) return digest;
  if (!validString(operation?.action_id, MAX_ID_LENGTH)) {
    return { valid: false, reason: 'ACTION_ID_REQUIRED' };
  }
  if (!validString(now, MAX_TIMESTAMP_LENGTH) || !Number.isFinite(Date.parse(now))) {
    return { valid: false, reason: 'EXECUTION_TIMESTAMP_INVALID' };
  }
  return {
    valid: true,
    reason: 'EXECUTION_RECORD_CREATED',
    record: Object.freeze({
      action_id: operation.action_id,
      operation_digest: digest.digest,
      state: 'PROPOSED',
      created_at: now,
      updated_at: now,
    }),
  };
}

export function verifyExecutionBinding(record, operation) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) {
    return { valid: false, reason: 'INVALID_EXECUTION_RECORD' };
  }
  if (!isExecutionState(record.state)) {
    return { valid: false, reason: 'INVALID_EXECUTION_STATE' };
  }
  if (!validString(record.action_id, MAX_ID_LENGTH)) {
    return { valid: false, reason: 'ACTION_ID_REQUIRED' };
  }
  if (record.action_id !== operation?.action_id) {
    return { valid: false, reason: 'ACTION_ID_MISMATCH' };
  }
  if (!validDigest(record.operation_digest)) {
    return { valid: false, reason: 'OPERATION_DIGEST_REQUIRED' };
  }
  return verifyOperationDigest(operation, record.operation_digest);
}

export function transitionBoundExecution(record, operation, nextState, now = new Date().toISOString()) {
  const binding = verifyExecutionBinding(record, operation);
  if (!binding.valid) return binding;
  if (!validString(now, MAX_TIMESTAMP_LENGTH) || !Number.isFinite(Date.parse(now))) {
    return { valid: false, reason: 'EXECUTION_TIMESTAMP_INVALID' };
  }
  if (!canTransition(record.state, nextState)) {
    return { valid: false, reason: 'INVALID_EXECUTION_TRANSITION' };
  }
  return {
    valid: true,
    reason: 'BOUND_EXECUTION_TRANSITION_ALLOWED',
    record: Object.freeze({
      ...record,
      state: nextState,
      updated_at: now,
    }),
  };
}
