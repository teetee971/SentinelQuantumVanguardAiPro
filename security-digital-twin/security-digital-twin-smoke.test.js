import assert from 'node:assert/strict';
import { createTwin, addEntity, addEvidence } from './core/digital-twin.js';
import { createEvidenceGraph, addNode, addEdge, traceEvidence } from './core/evidence-graph.js';

const observedAt = '2026-09-02T00:00:00Z';
let twin = createTwin();
twin = addEntity(twin, {
  entity_id: 'asset-1', entity_type: 'asset', observed_at: observedAt,
  provenance: { source_id: 'telemetry-1', source_type: 'telemetry' }
});
twin = addEvidence(twin, {
  evidence_id: 'ev-1', kind: 'observation', observed_at: observedAt,
  source_id: 'telemetry-1', confidence: 0.99
});
assert.equal(twin.entities.size, 1);
assert.equal(twin.evidence.get('ev-1').kind, 'observation');
assert.throws(() => addEntity(twin, {
  entity_id: 'actor-1', entity_type: 'threat_actor_hypothesis', observed_at: observedAt,
  provenance: { source_id: 'osint-1', source_type: 'osint' }, attributes: { confirmed: true }
}), /hypothesis_cannot_be_confirmed/);

let graph = createEvidenceGraph();
graph = addNode(graph, { id: 'decision-1', kind: 'decision' });
graph = addNode(graph, { id: 'ev-1', kind: 'observation' });
graph = addNode(graph, { id: 'src-1', kind: 'source' });
graph = addEdge(graph, 'decision-1', 'supports', 'ev-1', 0.9);
graph = addEdge(graph, 'ev-1', 'derived_from', 'src-1', 1);
assert.deepEqual(traceEvidence(graph, 'decision-1'), ['decision-1', 'ev-1', 'src-1']);
console.log('Security Digital Twin smoke tests: PASS');
