import { isKnownDecisionType, isSensitiveAction, normalizeOperation } from '../../decision-plane/policy/action-catalog.js';
import { validateAuthorizationRecord } from '../../decision-plane/policy/authorization-record.js';
import { validateHumanApprovalRecord } from '../../decision-plane/policy/human-approval-record.js';

/**
 * Deterministic policy gate. AI suggestions never bypass this layer.
 * Unknown decision types fail closed.
 * Sensitive decisions require structured authorization and human approval.
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

  if (isSensitiveAction(decisionType)) {
    if (context.kill_switch_active === true) {
      return { status: 'denied', reason: 'kill_switch_active' };
    }

    const now = context.now ?? Date.now();
    const authorizationResult = validateAuthorizationRecord(context.authorization, now);
    if (!authorizationResult.valid) {
      return { status: 'pending', reason: authorizationResult.reason };
    }

    const targetId = context.target_id;
    const policyVersion = context.policy_version;
    if (context.authorization.action !== decisionType) {
      return { status: 'denied', reason: 'authorization_action_mismatch' };
    }
    if (!targetId || context.authorization.target_id !== targetId) {
      return { status: 'denied', reason: 'target_not_authorized' };
    }
    if (!policyVersion || context.authorization.policy_version !== policyVersion) {
      return { status: 'denied', reason: 'policy_version_mismatch' };
    }

    const approvalResult = validateHumanApprovalRecord(context.humanApproval, {
      action: decisionType,
      target_id: targetId,
      policy_version: policyVersion,
    }, now);
    if (!approvalResult.valid) {
      return { status: 'pending', reason: approvalResult.reason };
    }
  }

  return { status: 'approved', reason: 'policy_checks_passed' };
}

export { evaluateDecision };
