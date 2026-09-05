import { evaluateModel } from '../evaluation/evaluation-engine.js';
import { canApproveForProduction } from '../evaluation/approval-gate.js';

/**
 * Bounded model-governance orchestration.
 *
 * Evaluates one exact model/version result and immediately binds that
 * evaluation to the production-approval gate. This function does not mutate a
 * model registry, deploy a model, or perform any runtime promotion.
 */
export function evaluateModelForApproval({ model, evaluationInput } = {}) {
  if (!model?.model_id || !model?.version) {
    return { allowed: false, reason: 'MODEL_BINDING_REQUIRED' };
  }
  if (!evaluationInput || typeof evaluationInput !== 'object' || Array.isArray(evaluationInput)) {
    return { allowed: false, reason: 'EVALUATION_INPUT_REQUIRED' };
  }

  let evaluation;
  try {
    evaluation = evaluateModel({
      ...evaluationInput,
      model_id: model.model_id,
      version: model.version,
    });
  } catch (error) {
    return {
      allowed: false,
      reason: 'MODEL_EVALUATION_ERROR',
      error_code: error instanceof Error ? error.message : 'UNKNOWN_ERROR',
    };
  }

  const approval = canApproveForProduction(model, evaluation);
  if (!approval.allowed) {
    return { ...approval, evaluation };
  }

  return {
    allowed: true,
    reason: 'MODEL_EVALUATION_APPROVED',
    evaluation,
    production_side_effect_performed: false,
  };
}
