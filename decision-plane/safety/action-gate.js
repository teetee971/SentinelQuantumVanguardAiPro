import {
  DECISION_TYPES,
  SENSITIVE_ACTIONS,
  normalizeOperation,
} from '../policy/action-catalog.js';
import { validateAuthorizationRecord } from '../policy/authorization-record.js';
import { validateHumanApprovalRecord } from '../policy/human-approval-record.js';
import { validateSimulationRecord } from '../policy/simulation-record.js';

const ALLOWED_ACTIONS = new Set([...DECISION_TYPES, ...SENSITIVE_ACTIONS]);
const CRITICAL_ACTIONS = new Set(SENSITIVE_ACTIONS);
const MAX_ACTION_LENGTH = 128;

export function evaluateActionGate({
  action,
  actionId,
  authorization = null,
  humanApproval = null,
  evidenceIntegrity = false,
  trust = null,
  policyDecision = 'deny',
  simulation = null,
  targetId,
  policyVersion,
  now = Date.now(),
} = {}) {
  if (typeof action !== 'string' || action.length === 0 || action.length > MAX_ACTION_LENGTH) {
    return { allowed: false, reason: 'INVALID_ACTION' };
  }

  const canonicalAction = normalizeOperation(action);
  if (canonicalAction === null) return { allowed: false, reason: 'INVALID_ACTION' };
  if (!ALLOWED_ACTIONS.has(canonicalAction)) return { allowed: false, reason: 'UNKNOWN_ACTION' };
  if (policyDecision !== 'allow') return { allowed: false, reason: 'POLICY_DENIED' };
  if (evidenceIntegrity !== true) return { allowed: false, reason: 'EVIDENCE_INTEGRITY_REQUIRED' };
  if (!trust || typeof trust.score !== 'number' || !Number.isFinite(trust.score) || trust.score < 0.7
    || typeof trust.uncertainty !== 'number' || !Number.isFinite(trust.uncertainty)
    || trust.uncertainty > 0.3 || trust.uncertainty < 0) {
    return { allowed: false, reason: 'TRUST_THRESHOLD_FAILED' };
  }

  if (!CRITICAL_ACTIONS.has(canonicalAction)) {
    if (simulation && simulation.safe !== true) return { allowed: false, reason: 'UNSAFE_SIMULATION' };
    return { allowed: true, reason: 'ACTION_GATE_ALLOW' };
  }

  if (typeof actionId !== 'string' || actionId.trim() === '') {
    return { allowed: false, reason: 'ACTION_ID_REQUIRED' };
  }
  if (typeof targetId !== 'string' || targetId.trim() === '') {
    return { allowed: false, reason: 'TARGET_ID_REQUIRED' };
  }
  if (typeof policyVersion !== 'string' || policyVersion.trim() === '') {
    return { allowed: false, reason: 'POLICY_VERSION_REQUIRED' };
  }

  const authorizationResult = validateAuthorizationRecord(authorization, now);
  if (!authorizationResult.valid) return { allowed: false, reason: authorizationResult.reason };

  if (normalizeOperation(authorization.action) !== canonicalAction) {
    return { allowed: false, reason: 'AUTHORIZATION_BINDING_MISMATCH:action' };
  }
  if (authorization.target_id !== targetId) {
    return { allowed: false, reason: 'AUTHORIZATION_BINDING_MISMATCH:target_id' };
  }
  if (authorization.policy_version !== policyVersion) {
    return { allowed: false, reason: 'AUTHORIZATION_BINDING_MISMATCH:policy_version' };
  }

  const approvalResult = validateHumanApprovalRecord(humanApproval, {
    action: canonicalAction,
    target_id: targetId,
    policy_version: policyVersion,
  }, now);
  if (!approvalResult.valid) return { allowed: false, reason: approvalResult.reason };

  const simulationResult = validateSimulationRecord(simulation, {
    action: canonicalAction,
    action_id: actionId,
    target_id: targetId,
    policy_version: policyVersion,
  }, now);
  if (!simulationResult.valid) return { allowed: false, reason: simulationResult.reason };

  return { allowed: true, reason: 'ACTION_GATE_ALLOW' };
}
