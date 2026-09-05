import { evaluateDecision } from '../../ai-orchestrator/policy/policy-guardian.js';
import { authorizeBoundExecutionStart } from '../action-verification/execution-binding.js';
import { validateActionPlan } from '../action-verification/action-plan.js';
import { isKnownAction, normalizeOperation } from '../policy/action-catalog.js';

/**
 * Bounded orchestration boundary.
 *
 * This function connects policy evaluation, canonical action validation and
 * action-plan validation to the final cryptographically bound execution-
 * authorization boundary. It deliberately performs no privileged side effect
 * and accepts no executor callback. A caller must separately decide how to
 * handle an authorized EXECUTING record.
 */
export async function authorizeBoundedOperation({
  decision,
  policyContext = {},
  actionPlan,
  executionRecord,
  operation,
  replayGuard,
  simulationBinding,
  simulation,
  now,
  authorizationRecord,
  humanApprovalRecord,
  proofTrust,
} = {}) {
  const policy = evaluateDecision(decision, policyContext);
  if (policy.status !== 'approved') {
    return {
      valid: false,
      reason: `POLICY_${String(policy.reason || 'DENIED').toUpperCase()}`,
      policy,
    };
  }

  if (!isKnownAction(operation?.action)) {
    return { valid: false, reason: 'UNKNOWN_OPERATION_ACTION', policy };
  }

  const plan = validateActionPlan(actionPlan);
  if (!plan.valid) {
    return { valid: false, reason: `ACTION_PLAN_${plan.reason}`, policy, plan };
  }

  const operationAction = normalizeOperation(operation.action);
  const planAction = normalizeOperation(actionPlan.action);
  if (planAction !== operationAction) {
    return { valid: false, reason: 'ACTION_PLAN_ACTION_MISMATCH', policy, plan };
  }
  if (actionPlan.target_id !== operation.target_id) {
    return { valid: false, reason: 'ACTION_PLAN_TARGET_MISMATCH', policy, plan };
  }

  const authorized = await authorizeBoundExecutionStart(
    executionRecord,
    operation,
    replayGuard,
    simulationBinding,
    simulation,
    now,
    authorizationRecord,
    humanApprovalRecord,
    proofTrust,
  );

  if (!authorized.valid) return authorized;

  return {
    valid: true,
    reason: 'BOUNDED_OPERATION_AUTHORIZED',
    record: authorized.record,
    policy,
    plan,
    side_effect_performed: false,
  };
}
