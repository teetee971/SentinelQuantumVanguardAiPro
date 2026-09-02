const DATA_RANK = Object.freeze({
  PUBLIC: 0,
  INTERNAL: 1,
  CONFIDENTIAL: 2,
  HIGHLY_RESTRICTED: 3,
});

export function isModelEligible(model, dataClass, task = {}) {
  if (!model || model.approval?.status !== 'approved' || model.approval?.approved_for_production !== true) {
    return { allowed: false, reason: 'MODEL_NOT_APPROVED' };
  }

  if (!Object.hasOwn(DATA_RANK, dataClass)) {
    return { allowed: false, reason: 'UNKNOWN_DATA_CLASS' };
  }

  if (!Array.isArray(model.allowed_data_classes) || !model.allowed_data_classes.includes(dataClass)) {
    return { allowed: false, reason: 'DATA_CLASS_NOT_ALLOWED' };
  }

  if (model.deployment === 'approved_remote' && DATA_RANK[dataClass] >= DATA_RANK.CONFIDENTIAL) {
    return { allowed: false, reason: 'REMOTE_MODEL_FOR_RESTRICTED_DATA' };
  }

  if (task.required_capability && !model.capabilities?.includes(task.required_capability)) {
    return { allowed: false, reason: 'CAPABILITY_NOT_SUPPORTED' };
  }

  return { allowed: true, reason: 'POLICY_ALLOW' };
}
