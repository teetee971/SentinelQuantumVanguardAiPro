import { isKnownDecisionType, normalizeOperation } from '../../decision-plane/policy/action-catalog.js';

const HIGH_IMPACT_ACTIONS = new Set(['contain', 'block']);

/**
 * Deterministic policy gate. AI suggestions never bypass this layer.
 * Unknown decision types fail closed.
 */
function evaluateDecision(decision, context = {}) {
  if (!decision || typeof decision !== 'object') {
    return { status: 'denied', reason: 'invalid_decision' };
  }

  const decisionType = normalizeOperation(decision.decision_type);
  if (!isKnownDecisionType(decisionType)) {
    return { status: 'denied', reason: 'unknown_decision_type' };
  }

  if (!Array.isArray(decision.evidence) || decision.evidence.length === 0) {
    return { status: 'denied', reason: 'missing_evidence' };
  }

  const hasInferenceOnly = decision.evidence.every((item) => item.kind !== 'observation');
  if (hasInferenceOnly) {
    return { status: 'denied', reason: 'no_direct_observation' };
  }

  const risk = Number(decision.assessment?.risk_score);
  if (!Number.isFinite(risk) || risk < 0 || risk > 1) {
    return { status: 'denied', reason: 'invalid_risk_score' };
  }

  if (HIGH_IMPACT_ACTIONS.has(decisionType)) {
    if (context.human_approval !== true) {
      return { status: 'pending', reason: 'human_approval_required' };
    }
    if (context.authorized_target !== true) {
      return { status: 'denied', reason: 'target_not_authorized' };
    }
    if (context.kill_switch_active === true) {
      return { status: 'denied', reason: 'kill_switch_active' };
    }
  }

  return { status: 'approved', reason: 'policy_checks_passed' };
}

export { evaluateDecision, HIGH_IMPACT_ACTIONS };
