import assert from 'node:assert/strict';
import test from 'node:test';
import { analyzeObservedCampaigns, DEFAULT_MAX_EVENTS } from './campaign-analysis.js';

function event(id, observedAt) {
  return {
    entity: { public_id: id, type: 'public_account', platform: 'test-platform' },
    observed_at: observedAt,
    source: { kind: 'public_web' },
    content: 'same observed narrative marker',
    links: ['https://example.test/story'],
    narrative_markers: ['shared-narrative'],
    coordination_features: {
      text_similarity: 1,
      time_similarity: 1,
      url_overlap: 1,
      hashtag_overlap: 1,
    },
  };
}

test('connects propagation graph and candidate correlation without performing attribution or side effects', () => {
  const result = analyzeObservedCampaigns({
    events: [
      event('entity-1', '2026-09-05T10:00:00.000Z'),
      event('entity-2', '2026-09-05T10:00:05.000Z'),
    ],
    correlationOptions: { threshold: 0.5, cluster_threshold: 0.7 },
  });

  assert.equal(result.valid, true);
  assert.equal(result.reason, 'OSINT_ANALYSIS_READY_FOR_REVIEW');
  assert.equal(result.graph.nodes.length >= 4, true);
  assert.equal(result.graph.edges.length, 4);
  assert.equal(result.timeline.length, 4);
  assert.equal(result.candidates.length, 1);
  assert.deepEqual(result.candidates[0].entity_ids, ['entity-1', 'entity-2']);
  assert.equal(result.analyst_review_required, true);
  assert.equal(result.attribution_performed, false);
  assert.equal(result.side_effect_performed, false);
});

test('fails closed when the event resource bound is exceeded', () => {
  const result = analyzeObservedCampaigns({
    events: Array.from({ length: 3 }, (_, index) => event(`entity-${index}`, '2026-09-05T10:00:00.000Z')),
    maxEvents: 2,
  });

  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OSINT_EVENT_LIMIT_EXCEEDED');
  assert.equal(result.max_events, 2);
  assert.equal(result.side_effect_performed, false);
});

test('never permits a caller to raise the orchestration event bound above the engine default', () => {
  const events = Array.from({ length: DEFAULT_MAX_EVENTS + 1 }, (_, index) => ({
    entity: { public_id: `entity-${index}` },
  }));
  const result = analyzeObservedCampaigns({ events, maxEvents: DEFAULT_MAX_EVENTS + 1000 });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OSINT_EVENT_LIMIT_EXCEEDED');
  assert.equal(result.max_events, DEFAULT_MAX_EVENTS);
});

test('rejects malformed event containers before downstream analysis', () => {
  const result = analyzeObservedCampaigns({ events: [null] });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OSINT_EVENT_INVALID');
});
