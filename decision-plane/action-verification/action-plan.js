const REQUIRED_FIELDS = Object.freeze(['action', 'target_id', 'preconditions', 'postconditions', 'rollback']);
const MAX_ACTION_LENGTH = 128;
const MAX_TARGET_LENGTH = 256;
const MAX_CONDITIONS = 64;
const MAX_CONDITION_LENGTH = 128;
const MAX_ROLLBACK_REFERENCE_LENGTH = 256;
const MAX_ADAPTER_ID_LENGTH = 128;
const MAX_POLICY_VERSION_LENGTH = 128;
const MAX_TIMESTAMP_LENGTH = 64;

function validString(value, maxLength) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;
}

function parseTimestamp(value) {
  if (!validString(value, MAX_TIMESTAMP_LENGTH)) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function validConditions(conditions) {
  return Array.isArray(conditions)
    && conditions.length <= MAX_CONDITIONS
    && conditions.every((condition) => validString(condition, MAX_CONDITION_LENGTH));
}

function validateExecutionAdapter(adapter, plan, now = Date.now()) {
  if (!adapter || typeof adapter !== 'object' || Array.isArray(adapter)) {
    return { valid: false, reason: 'EXECUTION_ADAPTER_REQUIRED' };
  }
  if (!validString(adapter.adapter_id, MAX_ADAPTER_ID_LENGTH)) {
    return { valid: false, reason: 'EXECUTION_ADAPTER_ID_REQUIRED' };
  }
  if (adapter.approved !== undefined) {
    return { valid: false, reason: 'EXECUTION_ADAPTER_BOOLEAN_APPROVAL_FORBIDDEN' };
  }
  if (adapter.status !== 'validated' || adapter.source !== 'system') {
    return { valid: false, reason: 'EXECUTION_ADAPTER_NOT_VALIDATED' };
  }
  if (!validString(adapter.action, MAX_ACTION_LENGTH)
    || adapter.action.trim().toLowerCase() !== plan.action.trim().toLowerCase()) {
    return { valid: false, reason: 'EXECUTION_ADAPTER_ACTION_MISMATCH' };
  }
  if (!validString(adapter.target_id, MAX_TARGET_LENGTH) || adapter.target_id !== plan.target_id) {
    return { valid: false, reason: 'EXECUTION_ADAPTER_TARGET_MISMATCH' };
  }
  if (!validString(adapter.policy_version, MAX_POLICY_VERSION_LENGTH)) {
    return { valid: false, reason: 'EXECUTION_ADAPTER_POLICY_REQUIRED' };
  }
  const expiresAt = parseTimestamp(adapter.expires_at);
  if (expiresAt === null || !Number.isFinite(now) || now >= expiresAt) {
    return { valid: false, reason: 'EXECUTION_ADAPTER_EXPIRED' };
  }
  return { valid: true, reason: 'EXECUTION_ADAPTER_VALID' };
}

export function validateActionPlan(plan, now = Date.now()) {
  if (!plan || typeof plan !== 'object' || Array.isArray(plan)) return { valid: false, reason: 'INVALID_PLAN' };
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
    || typeof plan.rollback !== 'object'
    || Array.isArray(plan.rollback)
    || plan.rollback.enabled !== true
    || !validString(plan.rollback.reference, MAX_ROLLBACK_REFERENCE_LENGTH)
  ) {
    return { valid: false, reason: 'ROLLBACK_REQUIRED' };
  }
  const adapterResult = validateExecutionAdapter(plan.execution_adapter, plan, now);
  if (!adapterResult.valid) return adapterResult;
  return { valid: true, reason: 'ACTION_PLAN_VALID' };
}

function hasOwnTrue(state, condition) {
  return Object.hasOwn(state, condition) && state[condition] === true;
}

export function verifyPreconditions(plan, state = {}, now = Date.now()) {
  const structure = validateActionPlan(plan, now);
  if (!structure.valid) return structure;
  if (!state || typeof state !== 'object' || Array.isArray(state)) return { valid: false, reason: 'INVALID_STATE' };

  const failedPreconditions = plan.preconditions.filter((condition) => !hasOwnTrue(state, condition));
  if (failedPreconditions.length) {
    return { valid: false, reason: 'PRECONDITION_FAILED', failed: failedPreconditions };
  }
  return { valid: true, reason: 'PRECONDITIONS_VERIFIED' };
}

export function verifyPostconditions(plan, state = {}, now = Date.now()) {
  const structure = validateActionPlan(plan, now);
  if (!structure.valid) return structure;
  if (!state || typeof state !== 'object' || Array.isArray(state)) return { valid: false, reason: 'INVALID_STATE' };

  const failedPostconditions = plan.postconditions.filter((condition) => !hasOwnTrue(state, condition));
  if (failedPostconditions.length) {
    return { valid: false, reason: 'POSTCONDITION_NOT_VERIFIED', failed: failedPostconditions };
  }
  return { valid: true, reason: 'POSTCONDITIONS_VERIFIED' };
}

export function verifyActionPlan(plan, state = {}, now = Date.now()) {
  const preconditions = verifyPreconditions(plan, state, now);
  if (!preconditions.valid) return preconditions;

  const postconditions = verifyPostconditions(plan, state, now);
  if (!postconditions.valid) return postconditions;

  return { valid: true, reason: 'ACTION_PLAN_VERIFIED' };
}
