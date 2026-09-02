const clamp01 = (value) => Math.max(0, Math.min(1, Number(value) || 0));

/**
 * Correlates already-observed, public/authorized events into analyst-review
 * priorities. It does not infer identity, guilt, intent, or maliciousness.
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
    factors.behavioral_anomaly * 0.20 + factors.temporal_sync * 0.25 +
    factors.narrative_similarity * 0.25 + factors.source_convergence * 0.15 +
    factors.propagation_strength * 0.15
  );
  const reasons = Object.entries(factors).filter(([, value]) => value >= 0.70).map(([name]) => name);
  const evidenceCount = Object.values(factors).filter((value) => value >= 0.50).length;
  return {
    priority_score: Number(score.toFixed(4)),
    confidence: Number(clamp01(evidenceCount / 5).toFixed(4)),
    reasons,
    interpretation: score >= 0.80 ? 'revue_prioritaire' : score >= 0.60 ? 'revue_recommandee' : 'surveillance_analytique'
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
    const count = campaignEvents.length || 1;
    const averages = campaignEvents.reduce((acc, event) => {
      const signals = event.signals || {};
      for (const key of Object.keys(acc)) acc[key] += clamp01(signals[key]);
      return acc;
    }, { behavioral_anomaly: 0, temporal_sync: 0, narrative_similarity: 0, source_convergence: 0, propagation_strength: 0 });

    const result = scoreCampaignSignal({
      behavioral_anomaly: averages.behavioral_anomaly / count,
      temporal_sync: averages.temporal_sync / count,
      narrative_similarity: averages.narrative_similarity / count,
      source_convergence: averages.source_convergence / count,
      propagation_strength: averages.propagation_strength / count
    });

    if (result.priority_score >= threshold) {
      campaigns.push({ campaign_id: campaignId, event_count: campaignEvents.length, ...result });
    }
  }
  return campaigns.sort((a, b) => b.priority_score - a.priority_score);
}

export { scoreCampaignSignal, correlateCampaign };
