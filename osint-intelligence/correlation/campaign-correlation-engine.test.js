import assert from 'node:assert/strict';
import { scoreCampaignSignal, correlateCampaign, correlateCandidateCampaigns } from './campaign-correlation-engine.js';
import { findClusters, DEFAULT_LIMITS } from '../coordination/coordination-engine.js';

const strong = scoreCampaignSignal({
  behavioral_anomaly: 0.95,
  temporal_sync: 0.95,
  narrative_similarity: 0.92,
  source_convergence: 0.80,
  propagation_strength: 0.85
});

assert.ok(strong.priority_score >= 0.80);
assert.ok(strong.reasons.includes('temporal_sync'));
assert.ok(strong.reasons.includes('narrative_similarity'));
assert.equal(strong.interpretation, 'revue_prioritaire');

const weak = scoreCampaignSignal({
  behavioral_anomaly: 0.10,
  temporal_sync: 0.20,
  narrative_similarity: 0.15,
  source_convergence: 0.10,
  propagation_strength: 0.20
});
assert.ok(weak.priority_score < 0.60);
assert.equal(weak.interpretation, 'surveillance_analytique');

const events = Array.from({ length: 500 }, (_, index) => ({
  campaign_id: index < 400 ? 'synthetic-coordinated-500' : `noise-${index}`,
  signals: index < 400
    ? { behavioral_anomaly: 0.90, temporal_sync: 0.95, narrative_similarity: 0.92, source_convergence: 0.80, propagation_strength: 0.85 }
    : { behavioral_anomaly: 0.10, temporal_sync: 0.10, narrative_similarity: 0.10, source_convergence: 0.10, propagation_strength: 0.10 }
}));

const campaigns = correlateCampaign(events, { threshold: 0.60 });
assert.equal(campaigns.length, 1);
assert.equal(campaigns[0].campaign_id, 'synthetic-coordinated-500');
assert.equal(campaigns[0].event_count, 400);
assert.ok(campaigns[0].priority_score >= 0.80);
assert.ok(campaigns[0].confidence > 0);

const makeObservedEvent = (id, content = 'Synthetic coordinated narrative') => ({
  observed_at: '2026-09-02T00:00:00Z',
  source: { kind: id.endsWith('-b') ? 'blog' : 'social' },
  entity: { public_id: id, type: 'account' },
  content,
  links: ['https://example.invalid/story'],
  narrative_markers: ['synthetic_story'],
  coordination_features: {
    text_similarity: 0.9,
    time_similarity: 0.95,
    url_overlap: 0.8,
    hashtag_overlap: 0.75
  }
});

const observed = [makeObservedEvent('account-a'), makeObservedEvent('account-b')];
const candidate = correlateCandidateCampaigns(observed, { threshold: 0.60, cluster_threshold: 0.72 });
assert.equal(candidate.length, 1);
assert.equal(candidate[0].event_count, 2);
assert.ok(candidate[0].priority_score >= 0.60);

const duplicateIdEvents = [makeObservedEvent('same'), makeObservedEvent('same'), makeObservedEvent('other')];
const deduped = findClusters(duplicateIdEvents, 0.72);
assert.equal(deduped.evaluated_event_count, 2);
assert.equal(deduped.pair_evaluations, 1);

const oversized = Array.from({ length: DEFAULT_LIMITS.max_events + 1 }, (_, index) => makeObservedEvent(`oversized-${index}`));
const bounded = findClusters(oversized, 0.72);
assert.equal(bounded.truncated, true);
assert.equal(bounded.reason, 'event_limit_exceeded');
assert.equal(bounded.clusters.length, 0);
assert.equal(bounded.pair_evaluations, 0);
assert.equal(correlateCandidateCampaigns(oversized, { cluster_threshold: 0.72 }).length, 0);

console.log('campaign-correlation-engine: OK — 500 synthetic events + resource bounds');
