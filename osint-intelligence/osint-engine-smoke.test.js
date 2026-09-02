import assert from 'node:assert/strict';
import { scorePair, findClusters } from './coordination/coordination-engine.js';
import { normalizeText, similarity, extractNarrative } from './narratives/narrative-engine.js';
import { buildPropagationGraph, propagationTimeline } from './graph/propagation-graph.js';
import { correlateCandidateCampaigns, scoreCampaignSignal } from './correlation/campaign-correlation-engine.js';

const makeEvent = (id, textSimilarity = 0.9, content = 'Synthetic coordinated narrative') => ({
  observed_at: '2026-09-02T00:00:00Z',
  source: { kind: 'social', uri: `https://example.invalid/${id}`, retrieved_at: '2026-09-02T00:00:01Z' },
  entity: { public_id: id, type: 'account', platform: 'synthetic' },
  content,
  links: ['https://example.invalid/story'],
  narrative_markers: ['synthetic_story'],
  coordination_features: {
    text_similarity: textSimilarity,
    time_similarity: 0.95,
    url_overlap: 0.8,
    hashtag_overlap: 0.75
  }
});

assert.equal(normalizeText('  Bonjour, TEST! https://example.invalid/x  '), 'bonjour test URL');
assert.equal(similarity('Synthetic coordinated narrative', 'Synthetic coordinated narrative'), 1);
const narrative = extractNarrative('Synthetic synthetic narrative narrative', 'en');
assert.ok(narrative.markers.includes('synthetic'));

const a = makeEvent('account-a');
const b = makeEvent('account-b');
const pair = scorePair(a, b);
assert.ok(pair.score >= 0.8);
assert.ok(pair.reasons.includes('forte_synchronisation_temporelle'));

const clusters = findClusters([a, b], 0.72);
assert.equal(clusters.clusters.length, 1);
assert.equal(clusters.clusters[0].length, 2);

const graph = buildPropagationGraph([a, b]);
assert.equal(graph.nodes.length, 4);
assert.equal(graph.edges.length, 4);
assert.equal(propagationTimeline(graph).length, 4);

const signal = scoreCampaignSignal({
  behavioral_anomaly: 0.9,
  temporal_sync: 0.95,
  narrative_similarity: 0.95,
  source_convergence: 0.8,
  propagation_strength: 0.85
});
assert.ok(signal.priority_score >= 0.8);
assert.equal(signal.interpretation, 'revue_prioritaire');

const candidates = correlateCandidateCampaigns([a, b], { threshold: 0.6, cluster_threshold: 0.72 });
assert.equal(candidates.length, 1);
assert.equal(candidates[0].event_count, 2);
assert.equal(candidates[0].entity_ids.length, 2);
assert.ok(candidates[0].priority_score >= 0.6);
assert.ok(candidates[0].candidate_campaign_id.startsWith('candidate-'));

console.log('osint-engine-smoke: OK');
