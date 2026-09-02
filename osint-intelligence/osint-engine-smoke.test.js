import assert from 'node:assert/strict';
import { scorePair, findClusters } from './coordination/coordination-engine.js';
import { normalizeText, similarity, extractNarrative } from './narratives/narrative-engine.js';
import { buildPropagationGraph, propagationTimeline } from './graph/propagation-graph.js';

const makeEvent = (id, textSimilarity = 0.9) => ({
  observed_at: '2026-09-02T00:00:00Z',
  entity: { public_id: id, type: 'account', platform: 'synthetic' },
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
assert.ok(similarity('Synthetic coordinated narrative', 'Synthetic coordinated narrative') === 1);
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

console.log('osint-engine-smoke: OK');
