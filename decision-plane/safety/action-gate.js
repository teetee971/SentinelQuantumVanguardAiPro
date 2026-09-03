import {
  DECISION_TYPES,
  SENSITIVE_ACTIONS,
  normalizeOperation,
} from '../policy/action-catalog.js';
import { validateAuthorizationRecord } from '../policy/authorization-record.js';
import { validateHumanApprovalRecord } from '../policy/human-approval-record.js';

const ALLOWED_ACTIONS = new Set([...DECISION_TYPES, ...SENSITIVE_ACTIONS]);
const CRITICAL_ACTIONS = new Set(SENSITIVE_ACTIONS);
const MAX_ACTION_LENGTH = 128;

export function evaluateActionGate({
  action,
  authorization = null,
  humanApproval = null,
  targetAuthorized = false,
  humanValidated = false,
  evidenceIntegrity = false,
  trust = null,
  policyDecision = 'deny',
  simulation = null,
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
  if (!simulation || simulation.safe !== true) return { allowed: false, reason: 'SAFE_SIMULATION_REQUIRED' };

  if (CRITICAL_ACTIONS.has(canonicalAction)) {
    if (targetAuthorized !== true || humanValidated !== true) {
      return { allowed: false, reason: 'AUTHORIZATION_AND_HUMAN_VALIDATION_REQUIRED' };
    }

    const authorizationResult = validateAuthorizationRecord(authorization, now);
    if (!authorizationResult.valid) return { allowed: false, reason: authorizationResult.reason };

    if (normalizeOperation(authorization.action) !== canonicalAction) {
      return { allowed: false, reason: 'AUTHORIZATION_BINDING_MISMATCH:action' };
    }

    const approvalResult = validateHumanApprovalRecord(humanApproval, {
      action: canonicalAction,
      target_id: authorization.target_id,
      policy_version: authorization.policy_version,
    }, now);
    if (!approvalResult.valid) return { allowed: false, reason: approvalResult.reason };
  }

  return { allowed: true, reason: 'ACTION_GATE_ALLOW' };
}
