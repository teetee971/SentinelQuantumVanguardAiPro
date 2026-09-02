import { isModelEligible } from '../model-registry/model-policy.js';

const LOCALITY_RANK = Object.freeze({ local: 3, private: 2, approved_remote: 1 });

function finiteOr(value, fallback) {
  return Number.isFinite(value) ? value : fallback;
}

/**
 * Selects only explicitly registered and policy-eligible models.
 * Routing never executes a model and never treats provider identity as trust.
 */
export function routeModel(models, {
  dataClass,
  requiredCapability,
  locality = 'local',
  maxLatencyMs = Infinity,
  maxCost = Infinity,
  minimumTrustScore = 0,
} = {}) {
  if (!Array.isArray(models) || models.length === 0) {
    return { allowed: false, model: null, reason: 'NO_REGISTERED_MODELS', candidates: [] };
  }

  const candidates = models.map((model) => {
    const eligibility = isModelEligible(model, dataClass, {
      required_capability: requiredCapability,
    });
    const latency = finiteOr(model.performance?.latency_ms, Infinity);
    const cost = finiteOr(model.performance?.cost, Infinity);
    const trust = finiteOr(model.trust?.score, 0);
    const localityRank = LOCALITY_RANK[model.deployment] ?? 0;

    const eligible = eligibility.allowed
      && latency <= maxLatencyMs
      && cost <= maxCost
      && trust >= minimumTrustScore;

    return {
      model,
      eligible,
      reason: eligibility.allowed
        ? (eligible ? 'ROUTABLE' : 'ROUTING_CONSTRAINT_FAILED')
        : eligibility.reason,
      rank: eligible ? localityRank * 1000 + trust * 100 - latency * 0.001 - cost * 0.01 : -Infinity,
    };
  });

  const eligible = candidates.filter((item) => item.eligible).sort((a, b) => b.rank - a.rank);
  if (!eligible.length) {
    return { allowed: false, model: null, reason: 'NO_ELIGIBLE_MODEL', candidates };
  }

  return {
    allowed: true,
    model: eligible[0].model,
    reason: 'MODEL_ROUTED',
    candidates,
  };
}
