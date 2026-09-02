export function canApproveForProduction(model, evaluation) {
  if (!model?.model_id || !model?.version) {
    return { allowed: false, reason: 'MODEL_BINDING_REQUIRED' };
  }

  if (!evaluation || evaluation.overall?.passed !== true) {
    return { allowed: false, reason: 'EVALUATION_FAILED' };
  }

  if (
    evaluation.model_id !== model.model_id ||
    evaluation.version !== model.version ||
    evaluation.binding?.model_id !== model.model_id ||
    evaluation.binding?.version !== model.version ||
    evaluation.binding?.suite_version !== evaluation.suite_version
  ) {
    return { allowed: false, reason: 'EVALUATION_BINDING_MISMATCH' };
  }

  if (!Array.isArray(evaluation.evaluations) || evaluation.evaluations.length === 0) {
    return { allowed: false, reason: 'EVALUATION_RECORD_INCOMPLETE' };
  }

  const mandatoryFailures = evaluation.evaluations.filter((item) => item.passed !== true);
  if (mandatoryFailures.length > 0) {
    return { allowed: false, reason: 'MANDATORY_EVALUATION_FAILURE' };
  }

  return { allowed: true, reason: 'EVALUATION_APPROVAL_ALLOW' };
}
