const SCORE_MIN = 0;
const SCORE_MAX = 1;

function clamp(value) {
  return Math.min(SCORE_MAX, Math.max(SCORE_MIN, Number.isFinite(value) ? value : SCORE_MIN));
}

/**
 * Computes a bounded trust assessment from explicit, auditable signals.
 * Trust is an uncertainty indicator, never proof of truth.
 */
export function assessTrust({
  sourceReliability = 0.5,
  evidenceConfidence = 0.5,
  modelReliability = 0.5,
  provenanceIntegrity = 0.5,
  uncertainty = 0.5,
} = {}) {
  const reliability = clamp(sourceReliability) * 0.25;
  const evidence = clamp(evidenceConfidence) * 0.25;
  const model = clamp(modelReliability) * 0.25;
  const provenance = clamp(provenanceIntegrity) * 0.25;
  const uncertaintyPenalty = clamp(uncertainty) * 0.1;

  const score = clamp(reliability + evidence + model + provenance - uncertaintyPenalty);

  return {
    score,
    uncertainty: clamp(uncertainty),
    interpretation: score >= 0.8 ? 'HIGHER_TRUST' : score >= 0.55 ? 'MEDIUM_TRUST' : 'LOWER_TRUST',
    disclaimer: 'Trust is an assessment signal and must not be treated as proof of truth.',
  };
}

export function canSupportDecision(trust, { minimumScore = 0.7, maximumUncertainty = 0.3 } = {}) {
  if (!trust || typeof trust.score !== 'number') return false;
  return trust.score >= minimumScore && trust.uncertainty <= maximumUncertainty;
}
