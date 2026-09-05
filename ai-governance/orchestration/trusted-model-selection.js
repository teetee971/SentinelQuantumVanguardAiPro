import { assessTrust, canSupportDecision } from '../trust-engine/trust-engine.js';
import { routeModel } from '../model-router/model-router.js';

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/**
 * Bounded model-selection orchestration.
 *
 * Derives trust from explicit signals, rejects candidates that cannot support a
 * decision under the requested trust policy, then delegates eligibility and
 * locality/capability routing to the canonical model router. It does not invoke,
 * deploy, mutate, or promote a model.
 */
export function selectTrustedModel({ models, routing = {}, trustPolicy = {} } = {}) {
  if (!Array.isArray(models) || models.length === 0) {
    return { allowed: false, reason: 'NO_REGISTERED_MODELS', assessments: [], side_effect_performed: false };
  }
  if (!isPlainObject(routing) || !isPlainObject(trustPolicy)) {
    return { allowed: false, reason: 'INVALID_SELECTION_POLICY', assessments: [], side_effect_performed: false };
  }

  const assessments = models.map((model) => {
    if (!model || !isPlainObject(model.trust_signals)) {
      return {
        model_id: model?.model_id ?? null,
        version: model?.version ?? null,
        supported: false,
        reason: 'TRUST_SIGNALS_REQUIRED',
        trust: null,
      };
    }

    const trust = assessTrust(model.trust_signals);
    const supported = canSupportDecision(trust, trustPolicy);
    return {
      model_id: model.model_id ?? null,
      version: model.version ?? null,
      supported,
      reason: supported ? 'TRUST_POLICY_ALLOW' : 'TRUST_POLICY_DENY',
      trust,
    };
  });

  const trustedCandidates = models.flatMap((model, index) => {
    const assessment = assessments[index];
    if (!assessment.supported) return [];
    return [{
      ...model,
      trust: {
        ...(isPlainObject(model.trust) ? model.trust : {}),
        score: assessment.trust.score,
        uncertainty: assessment.trust.uncertainty,
      },
    }];
  });

  if (trustedCandidates.length === 0) {
    return {
      allowed: false,
      reason: 'NO_TRUST_SUPPORTED_MODEL',
      assessments,
      side_effect_performed: false,
    };
  }

  const minimumTrustScore = Math.max(
    Number.isFinite(routing.minimumTrustScore) ? routing.minimumTrustScore : 0,
    Number.isFinite(trustPolicy.minimumScore) ? trustPolicy.minimumScore : 0.7,
  );

  const routed = routeModel(trustedCandidates, { ...routing, minimumTrustScore });
  if (!routed.allowed) {
    return {
      ...routed,
      assessments,
      side_effect_performed: false,
    };
  }

  return {
    allowed: true,
    reason: 'TRUSTED_MODEL_ROUTED',
    model: routed.model,
    assessments,
    route: routed,
    side_effect_performed: false,
  };
}
