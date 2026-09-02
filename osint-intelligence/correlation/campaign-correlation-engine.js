const clamp01 = (value) => Math.max(0, Math.min(1, Number(value) || 0));

/**
 * Correlates already-observed, public/authorized events into analyst-review
 * priorities. It does not infer identity, guilt, intent, or maliciousness.
 *
 * Expected event signals are normalized to [0,1]:
 * - behavioral_anomaly
 * - temporal_sync
 * - narrative_similarity
 * - source_convergence
 * - propagation_strength
 */
function scoreCampaignSignal(signals = {}) {
  const factors = {
    behavioral_anomaly: clamp01(signals.behavioral_anomaly),
    temporal_sync: clamp01(signals.temporal_sync),
    narrative_similarity: clamp01(signals.narrative_similarity),
    source_convergence: clamp01(signals.source_convergence),
    propagation_strength: clamp01(signals.propagation_strength)
  };

  const score = clamp01(
    factors.behavioral_anomaly * 0.20 +
    factors.temporal_sync * 0.25 +
    factors.narrative_similarity * 0.25 +
    factors.source_convergence * 0.15 +
    factors.propagation_strength * 0.15
  );

  const reasons = Object.entries(factors)
    .filter(([, value]) => value >= 0.70)
    .map(([name]) => name);

  const evidenceCount = Object.values(factors).filter((value) => value >= 0.50).length;
  const confidence = clamp01(evidenceCount / 5);

  return {
    priority_score: Number(score.toFixed(4)),
    confidence: Number(confidence.toFixed(4)),
    reasons,
    interpretation: score >= 0.80
      ? 'revue_prioritaire'
      : score >= 0.60
        ? 'revue_recommandee'
        : 'surveillance_analytique'
  };
}

function correlateCampaign(events = [], options = {}) {
  const threshold = clamp01(options.threshold ?? 0.60);
  const byCampaign = new Map();

  for (const event of events) {
    if (!event || typeof event !== 'object') continue;
    const campaignId = event.campaign_id || 'unclassified';
    if (!byCampaign.has(campaignId)) byCampaign.set(campaignId, []);
    byCampaign.get(campaignId).push(event);
  }

  const campaigns = [];
  for (const [campaignId, campaignEvents] of byCampaign.entries()) {
    const aggregate = campaignEvents.reduce((acc, event) => {
      const result = scoreCampaignSignal(event.signals || {});
      acc.behavioral_anomaly += result.priority_score * 0.20;
      acc.temporal_sync += clamp01(event.signals?.temporal_sync);
      acc.narrative_similarity += clamp01(event.signals?.narrative_similarity);
      acc.source_convergence += clamp01(event.signals?.source_convergence);
      acc.propagation_strength += clamp01(event.signals?.propagation_strength);
      return acc;
    }, {
      behavioral_anomaly: 0,
      temporal_sync: 0,
      narrative_similarity: 0,
      source_convergence: 0,
      propagation_strength: 0
    });

    const count = campaignEvents.length || 1;
    const result = scoreCampaignSignal({
      behavioral_anomaly: aggregate.behavioral_anomaly / count,
      temporal_sync: aggregate.temporal_sync / count,
      narrative_similarity: aggregate.narrative_similarity / count,
      source_convergence: aggregate.source_convergence / count,
      propagation_strength: aggregate.propagation_strength / count
    });

    if (result.priority_score >= threshold) {
      campaigns.push({
        campaign_id: campaignId,
        event_count: campaignEvents.length,
        priority_score: result.priority_score,
        confidence: result.confidence,
        reasons: result.reasons,
        interpretation: result.interpretation
      });
    }
  }

  return campaigns.sort((a, b) => b.priority_score - a.priority_score);
}

module.exports = { scoreCampaignSignal, correlateCampaign };
