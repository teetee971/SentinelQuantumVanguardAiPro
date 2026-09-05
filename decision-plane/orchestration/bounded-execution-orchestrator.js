import { evaluateDecision } from '../../ai-orchestrator/policy/policy-guardian.js';
import { authorizeBoundExecutionStart } from '../action-verification/execution-binding.js';

/**
 * Bounded orchestration boundary.
 *
 * This function connects policy evaluation to the final cryptographically bound
 * execution-authorization boundary. It deliberately performs no privileged side
 * effect and accepts no executor callback. A caller must separately decide how
 * to handle an authorized EXECUTING record.
 */
export async function authorizeBoundedOperation({
  decision,
  policyContext = {},
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
    side_effect_performed: false,
  };
}
