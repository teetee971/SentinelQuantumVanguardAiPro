import { findClusters, scorePair } from '../coordination/coordination-engine.js';
import { similarity } from '../narratives/narrative-engine.js';

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

function aggregateSignals(campaignEvents) {
  const count = campaignEvents.length || 1;
  const sums = {
    behavioral_anomaly: 0,
    temporal_sync: 0,
    narrative_similarity: 0,
    source_convergence: 0,
    propagation_strength: 0
  };

  for (const event of campaignEvents) {
    const signals = event.signals || {};
    for (const key of Object.keys(sums)) sums[key] += clamp01(signals[key]);
  }

  return Object.fromEntries(Object.entries(sums).map(([key, value]) => [key, value / count]));
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
    const result = scoreCampaignSignal(aggregateSignals(campaignEvents));
    if (result.priority_score >= threshold) {
      campaigns.push({ campaign_id: campaignId, event_count: campaignEvents.length, ...result });
    }
  }
  return campaigns.sort((a, b) => b.priority_score - a.priority_score);
}

/**
 * Builds candidate groups from observed behavioral/narrative signals without
 * requiring an upstream campaign_id. Candidate grouping is an analytic aid;
 * it is not attribution and must be reviewed by an analyst.
 */
function correlateCandidateCampaigns(events = [], options = {}) {
  const threshold = clamp01(options.threshold ?? 0.60);
  const clusterThreshold = clamp01(options.cluster_threshold ?? 0.72);
  const validEvents = events.filter((event) => event?.entity?.public_id);
  const clusterResult = findClusters(validEvents, clusterThreshold);
  const byEntity = new Map(validEvents.map((event) => [event.entity.public_id, event]));

  const candidates = [];
  for (const [index, memberIds] of clusterResult.clusters.entries()) {
    const members = memberIds.map((id) => byEntity.get(id)).filter(Boolean);
    if (members.length < 2) continue;

    let pairCount = 0;
    let coordinationTotal = 0;
    let temporalTotal = 0;
    let narrativeTotal = 0;
    for (let i = 0; i < members.length; i += 1) {
      for (let j = i + 1; j < members.length; j += 1) {
        const pair = scorePair(members[i], members[j]);
        const temporal = clamp01((members[i].coordination_features?.time_similarity + members[j].coordination_features?.time_similarity) / 2);
        const narrative = similarity(members[i].content || members[i].text || '', members[j].content || members[j].text || '');
        coordinationTotal += pair.score;
        temporalTotal += temporal;
        narrativeTotal += narrative;
        pairCount += 1;
      }
    }

    const sourceKinds = new Set(members.map((event) => event.source?.kind).filter(Boolean));
    const linkCount = members.reduce((total, event) => total + (Array.isArray(event.links) ? event.links.length : 0), 0);
    const markerCount = members.reduce((total, event) => total + (Array.isArray(event.narrative_markers) ? event.narrative_markers.length : 0), 0);
    const signals = {
      behavioral_anomaly: coordinationTotal / Math.max(1, pairCount),
      temporal_sync: temporalTotal / Math.max(1, pairCount),
      narrative_similarity: narrativeTotal / Math.max(1, pairCount),
      source_convergence: clamp01(sourceKinds.size / Math.max(2, members.length)),
      propagation_strength: clamp01((linkCount + markerCount) / Math.max(1, members.length * 2))
    };
    const result = scoreCampaignSignal(signals);

    if (result.priority_score >= threshold) {
      candidates.push({
        candidate_campaign_id: `candidate-${index + 1}`,
        event_count: members.length,
        entity_ids: memberIds,
        ...result
      });
    }
  }

  return candidates.sort((a, b) => b.priority_score - a.priority_score);
}

export { scoreCampaignSignal, correlateCampaign, correlateCandidateCampaigns };
