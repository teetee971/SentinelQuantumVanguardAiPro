import { evaluateDecision } from '../../ai-orchestrator/policy/policy-guardian.js';
import { authorizeBoundExecutionStart } from '../action-verification/execution-binding.js';
import { validateActionPlan } from '../action-verification/action-plan.js';
import { isKnownAction, normalizeOperation } from '../policy/action-catalog.js';
import { simulateImpact } from '../simulation/impact-engine.js';

/**
 * Bounded orchestration boundary.
 *
 * This function connects policy evaluation, canonical action validation,
 * action-plan validation and a deterministic impact preflight to the final
 * cryptographically bound execution-authorization boundary. The impact
 * preflight is defense in depth only; it does not replace the separately signed
 * simulation proof verified at the final boundary.
 *
 * It deliberately performs no privileged side effect and accepts no executor
 * callback. A caller must separately decide how to handle an authorized
 * EXECUTING record.
 */
export async function authorizeBoundedOperation({
  decision,
  policyContext = {},
  actionPlan,
  impactInput,
  impactLimits = {},
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

  if (!impactInput || typeof impactInput !== 'object' || Array.isArray(impactInput)) {
    return { valid: false, reason: 'IMPACT_PREFLIGHT_REQUIRED', policy, plan };
  }
  if (normalizeOperation(impactInput.action) !== operationAction) {
    return { valid: false, reason: 'IMPACT_PREFLIGHT_ACTION_MISMATCH', policy, plan };
  }
  if (
    !Array.isArray(impactInput.targetIds)
    || impactInput.targetIds.length !== 1
    || impactInput.targetIds[0] !== operation.target_id
  ) {
    return { valid: false, reason: 'IMPACT_PREFLIGHT_TARGET_MISMATCH', policy, plan };
  }

  const impact = simulateImpact(impactInput, impactLimits);
  if (!impact.safe) {
    return { valid: false, reason: `IMPACT_PREFLIGHT_${impact.reason}`, policy, plan, impact };
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
    impact,
    side_effect_performed: false,
  };
}
