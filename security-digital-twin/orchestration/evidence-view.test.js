import assert from 'node:assert/strict';
import test from 'node:test';
import { buildDigitalTwinEvidenceView } from './evidence-view.js';

const observedAt = '2026-09-05T00:00:00Z';

function entity(id = 'asset-1') {
  return {
    entity_id: id,
    entity_type: 'asset',
    observed_at: observedAt,
    provenance: { source_id: 'telemetry-1', source_type: 'telemetry' },
  };
}

function evidence(id = 'ev-1') {
  return {
    evidence_id: id,
    kind: 'observation',
    observed_at: observedAt,
    source_id: 'telemetry-1',
    confidence: 0.95,
  };
}

test('builds a bounded twin, graph, and trace from observed records', () => {
  const result = buildDigitalTwinEvidenceView({
    entities: [entity()],
    evidence: [evidence()],
    relations: [{ from: 'asset-1', relation: 'observes', to: 'ev-1', confidence: 0.9 }],
    focusNodeId: 'asset-1',
  });

  assert.equal(result.allowed, true);
  assert.equal(result.side_effect_performed, false);
  assert.equal(result.twin.entities.size, 1);
  assert.equal(result.twin.evidence.size, 1);
  assert.equal(result.graph.edges.length, 1);
  assert.deepEqual(result.trace, ['asset-1', 'ev-1']);
});

test('fails closed for unknown relation endpoints', () => {
  const result = buildDigitalTwinEvidenceView({
    entities: [entity()],
    evidence: [evidence()],
    relations: [{ from: 'asset-1', relation: 'supports', to: 'missing', confidence: 0.8 }],
  });

  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'DIGITAL_TWIN_BUILD_REJECTED');
  assert.equal(result.error_code, 'unknown_graph_node');
});

test('preserves the twin rule that hypotheses cannot self-confirm', () => {
  const result = buildDigitalTwinEvidenceView({
    entities: [{
      entity_id: 'actor-1',
      entity_type: 'threat_actor_hypothesis',
      observed_at: observedAt,
      provenance: { source_id: 'osint-1', source_type: 'osint' },
      attributes: { confirmed: true },
    }],
  });

  assert.equal(result.allowed, false);
  assert.equal(result.error_code, 'hypothesis_cannot_be_confirmed_by_twin');
});

test('enforces hard resource limits', () => {
  const result = buildDigitalTwinEvidenceView({
    entities: [entity('asset-1'), entity('asset-2')],
    limits: { max_entities: 1 },
  });

  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'DIGITAL_TWIN_ENTITY_LIMIT_EXCEEDED');
});

test('rejects unknown focus nodes instead of returning an ambiguous trace', () => {
  const result = buildDigitalTwinEvidenceView({
    entities: [entity()],
    focusNodeId: 'missing',
  });

  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'DIGITAL_TWIN_FOCUS_NODE_UNKNOWN');
});
