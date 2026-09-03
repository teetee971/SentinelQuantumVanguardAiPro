import {
  DECISION_TYPES,
  SENSITIVE_ACTIONS,
  normalizeOperation,
} from '../policy/action-catalog.js';

const ALLOWED_ACTIONS = new Set([
  ...DECISION_TYPES,
  ...SENSITIVE_ACTIONS,
]);
const CRITICAL_ACTIONS = new Set(SENSITIVE_ACTIONS);
const MAX_ACTION_LENGTH = 128;

/**
 * Decides whether an action plan may proceed. This module never executes actions.
 * Unknown actions are denied. Critical actions require explicit target authorization
 * and human validation.
 */
export function evaluateActionGate({
  action,
  targetAuthorized = false,
  humanValidated = false,
  evidenceIntegrity = false,
  trust = null,
  policyDecision = 'deny',
  simulation = null,
} = {}) {
  if (typeof action !== 'string' || action.length === 0 || action.length > MAX_ACTION_LENGTH) {
    return { allowed: false, reason: 'INVALID_ACTION' };
  }

  // Canonicalize before applying security policy so case/whitespace cannot bypass
  // the action classification.
  const canonicalAction = normalizeOperation(action);
  if (canonicalAction === null) return { allowed: false, reason: 'INVALID_ACTION' };
  if (!ALLOWED_ACTIONS.has(canonicalAction)) return { allowed: false, reason: 'UNKNOWN_ACTION' };

  if (policyDecision !== 'allow') return { allowed: false, reason: 'POLICY_DENIED' };
  if (evidenceIntegrity !== true) return { allowed: false, reason: 'EVIDENCE_INTEGRITY_REQUIRED' };
  if (!trust
    || typeof trust.score !== 'number'
    || !Number.isFinite(trust.score)
    || trust.score < 0.7
    || typeof trust.uncertainty !== 'number'
    || !Number.isFinite(trust.uncertainty)
    || trust.uncertainty > 0.3
    || trust.uncertainty < 0) {
    return { allowed: false, reason: 'TRUST_THRESHOLD_FAILED' };
  }
  if (!simulation || simulation.safe !== true) return { allowed: false, reason: 'SAFE_SIMULATION_REQUIRED' };

  if (CRITICAL_ACTIONS.has(canonicalAction) && (targetAuthorized !== true || humanValidated !== true)) {
    return { allowed: false, reason: 'AUTHORIZATION_AND_HUMAN_VALIDATION_REQUIRED' };
  }

  return { allowed: true, reason: 'ACTION_GATE_ALLOW' };
}
