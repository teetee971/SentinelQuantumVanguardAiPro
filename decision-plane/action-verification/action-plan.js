const REQUIRED_FIELDS = Object.freeze(['action', 'target_id', 'preconditions', 'postconditions', 'rollback']);
const MAX_ACTION_LENGTH = 128;
const MAX_TARGET_LENGTH = 256;
const MAX_CONDITIONS = 64;
const MAX_CONDITION_LENGTH = 128;
const MAX_ROLLBACK_REFERENCE_LENGTH = 256;

function validString(value, maxLength) {
  return typeof value === 'string' && value.length > 0 && value.length <= maxLength;
}

function validConditions(conditions) {
  return Array.isArray(conditions)
    && conditions.length <= MAX_CONDITIONS
    && conditions.every((condition) => validString(condition, MAX_CONDITION_LENGTH));
}

export function validateActionPlan(plan) {
  if (!plan || typeof plan !== 'object') return { valid: false, reason: 'INVALID_PLAN' };
  for (const field of REQUIRED_FIELDS) {
    if (!Object.hasOwn(plan, field)) return { valid: false, reason: `MISSING_${field.toUpperCase()}` };
  }
  if (!validString(plan.action, MAX_ACTION_LENGTH) || !validString(plan.target_id, MAX_TARGET_LENGTH)) {
    return { valid: false, reason: 'INVALID_ACTION_TARGET' };
  }
  if (!validConditions(plan.preconditions) || !validConditions(plan.postconditions)) {
    return { valid: false, reason: 'INVALID_CONDITION_LIST' };
  }
  if (
    !plan.rollback
    || plan.rollback.enabled !== true
    || !validString(plan.rollback.reference, MAX_ROLLBACK_REFERENCE_LENGTH)
  ) {
    return { valid: false, reason: 'ROLLBACK_REQUIRED' };
  }
  if (plan.execution_adapter?.approved !== true) return { valid: false, reason: 'EXECUTION_ADAPTER_NOT_APPROVED' };
  return { valid: true, reason: 'ACTION_PLAN_VALID' };
}

function hasOwnTrue(state, condition) {
  return Object.hasOwn(state, condition) && state[condition] === true;
}

export function verifyActionPlan(plan, state = {}) {
  const structure = validateActionPlan(plan);
  if (!structure.valid) return structure;
  if (!state || typeof state !== 'object') return { valid: false, reason: 'INVALID_STATE' };

  const failedPreconditions = plan.preconditions.filter((condition) => !hasOwnTrue(state, condition));
  if (failedPreconditions.length) return { valid: false, reason: 'PRECONDITION_FAILED', failed: failedPreconditions };

  if (plan.postconditions.some((condition) => !hasOwnTrue(state, condition))) {
    return { valid: false, reason: 'POSTCONDITION_NOT_VERIFIED' };
  }

  return { valid: true, reason: 'ACTION_PLAN_VERIFIED' };
}
