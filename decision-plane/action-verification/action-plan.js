const REQUIRED_FIELDS = Object.freeze(['action', 'target_id', 'preconditions', 'postconditions', 'rollback']);

export function validateActionPlan(plan) {
  if (!plan || typeof plan !== 'object') return { valid: false, reason: 'INVALID_PLAN' };
  for (const field of REQUIRED_FIELDS) {
    if (!(field in plan)) return { valid: false, reason: `MISSING_${field.toUpperCase()}` };
  }
  if (typeof plan.action !== 'string' || typeof plan.target_id !== 'string') return { valid: false, reason: 'INVALID_ACTION_TARGET' };
  if (!Array.isArray(plan.preconditions) || !Array.isArray(plan.postconditions)) return { valid: false, reason: 'INVALID_CONDITION_LIST' };
  if (!plan.rollback || plan.rollback.enabled !== true || typeof plan.rollback.reference !== 'string') return { valid: false, reason: 'ROLLBACK_REQUIRED' };
  if (plan.execution_adapter?.approved !== true) return { valid: false, reason: 'EXECUTION_ADAPTER_NOT_APPROVED' };
  return { valid: true, reason: 'ACTION_PLAN_VALID' };
}

export function verifyActionPlan(plan, state = {}) {
  const structure = validateActionPlan(plan);
  if (!structure.valid) return structure;

  const failedPreconditions = plan.preconditions.filter((condition) => state[condition] !== true);
  if (failedPreconditions.length) return { valid: false, reason: 'PRECONDITION_FAILED', failed: failedPreconditions };

  if (plan.postconditions.some((condition) => state[condition] !== true)) {
    return { valid: false, reason: 'POSTCONDITION_NOT_VERIFIED' };
  }

  return { valid: true, reason: 'ACTION_PLAN_VERIFIED' };
}
