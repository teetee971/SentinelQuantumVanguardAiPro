import { consumeAuthorizationOnce } from './anti-replay.js';
import { computeOperationDigest, verifyOperationDigest } from './operation-digest.js';
import { canTransition, isExecutionState } from './execution-state-machine.js';
import { verifySimulationBinding } from './simulation-binding.js';
import { validateAuthorizationRecord } from '../policy/authorization-record.js';
import { validateHumanApprovalRecord } from '../policy/human-approval-record.js';
import { verifyProofAuthenticity } from '../policy/proof-authenticity.js';

const MAX_ID_LENGTH = 256;
const MAX_TIMESTAMP_LENGTH = 64;

function validString(value, maxLength) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;
}

function validDigest(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
}

function validFinalAuthorization(record, operation, nowMs) {
  const result = validateAuthorizationRecord(record, nowMs);
  if (!result.valid) return result;
  if (record.authorization_id !== operation.authorization_id) {
    return { valid: false, reason: 'AUTHORIZATION_RECORD_ID_MISMATCH' };
  }
  if (record.action.trim().toLowerCase() !== String(operation.action).trim().toLowerCase()) {
    return { valid: false, reason: 'AUTHORIZATION_ACTION_MISMATCH' };
  }
  if (record.target_id !== operation.target_id) {
    return { valid: false, reason: 'AUTHORIZATION_TARGET_MISMATCH' };
  }
  if (record.policy_version !== operation.policy_version) {
    return { valid: false, reason: 'AUTHORIZATION_POLICY_MISMATCH' };
  }
  return { valid: true, reason: 'FINAL_AUTHORIZATION_VALID' };
}

function validFinalApproval(record, operation, nowMs) {
  return validateHumanApprovalRecord(record, {
    action: String(operation.action).trim().toLowerCase(),
    target_id: operation.target_id,
    policy_version: operation.policy_version,
  }, nowMs);
}

export function createExecutionRecord(operation, now = new Date().toISOString()) {
  const digest = computeOperationDigest(operation);
  if (!digest.valid) return digest;
  if (!validString(operation?.action_id, MAX_ID_LENGTH)) {
    return { valid: false, reason: 'ACTION_ID_REQUIRED' };
  }
  if (!validString(operation?.authorization_id, MAX_ID_LENGTH)) {
    return { valid: false, reason: 'AUTHORIZATION_ID_REQUIRED' };
  }
  if (!validString(now, MAX_TIMESTAMP_LENGTH) || !Number.isFinite(Date.parse(now))) {
    return { valid: false, reason: 'EXECUTION_TIMESTAMP_INVALID' };
  }
  return {
    valid: true,
    reason: 'EXECUTION_RECORD_CREATED',
    record: Object.freeze({
      action_id: operation.action_id,
      authorization_id: operation.authorization_id,
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
  if (!validString(record.authorization_id, MAX_ID_LENGTH)) {
    return { valid: false, reason: 'AUTHORIZATION_ID_REQUIRED' };
  }
  if (record.authorization_id !== operation?.authorization_id) {
    return { valid: false, reason: 'AUTHORIZATION_ID_MISMATCH' };
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
  if (record.state === 'READY' && nextState === 'EXECUTING') {
    return { valid: false, reason: 'EXECUTION_START_REQUIRES_FINAL_BOUNDARY' };
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

/**
 * Final pre-side-effect boundary. The exact operation is re-bound to the
 * stored digest, the simulation binding is re-verified, the live authorization
 * and human approval proofs are revalidated for freshness and exact scope,
 * and every authorization/approval/simulation proof must carry a valid Ed25519
 * signature from an issuer explicitly authorized for that proof type.
 * The authorization is consumed before EXECUTING is entered.
 *
 * The replay guard may be backed by durable asynchronous storage. Therefore
 * this boundary is asynchronous and MUST be awaited by the caller before any
 * privileged side effect is started.
 */
export async function authorizeBoundExecutionStart(
  record,
  operation,
  replayGuard,
  simulationBinding,
  simulation,
  now = new Date().toISOString(),
  authorizationRecord = null,
  humanApprovalRecord = null,
  proofTrust = null,
) {
  const binding = verifyExecutionBinding(record, operation);
  if (!binding.valid) return binding;
  if (record.state !== 'READY') {
    return { valid: false, reason: 'EXECUTION_STATE_NOT_READY' };
  }
  if (!validString(now, MAX_TIMESTAMP_LENGTH) || !Number.isFinite(Date.parse(now))) {
    return { valid: false, reason: 'EXECUTION_TIMESTAMP_INVALID' };
  }

  const nowMs = Date.parse(now);
  const authorizationResult = validFinalAuthorization(authorizationRecord, operation, nowMs);
  if (!authorizationResult.valid) return authorizationResult;

  const approvalResult = validFinalApproval(humanApprovalRecord, operation, nowMs);
  if (!approvalResult.valid) return approvalResult;

  const simulationResult = verifySimulationBinding(simulationBinding, operation, simulation, nowMs);
  if (!simulationResult.valid) return simulationResult;

  const signedProofs = [
    ['authorization', authorizationRecord],
    ['approval', humanApprovalRecord],
    ['simulation', simulation],
  ];
  for (const [proofType, proof] of signedProofs) {
    const authenticity = verifyProofAuthenticity(proof, proofType, proofTrust);
    if (!authenticity.valid) return authenticity;
  }

  const replay = await consumeAuthorizationOnce(replayGuard, record.authorization_id);
  if (!replay.valid) return replay;

  return {
    valid: true,
    reason: 'EXECUTION_START_AUTHORIZED',
    record: Object.freeze({
      ...record,
      state: 'EXECUTING',
      started_at: now,
      updated_at: now,
      simulation_id: simulation.simulation_id,
    }),
  };
}
