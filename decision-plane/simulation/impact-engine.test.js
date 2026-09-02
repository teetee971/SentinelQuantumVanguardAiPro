import assert from 'node:assert/strict';
import test from 'node:test';
import { simulateImpact } from './impact-engine.js';

test('computes bounded graph impact without execution', () => {
  const result = simulateImpact({
    nodes: [{ id: 'a' }, { id: 'b' }, { id: 'c' }],
    edges: [{ from: 'a', to: 'b' }, { from: 'b', to: 'c' }],
    action: 'contain',
    targetIds: ['a'],
  });
  assert.deepEqual(result.affectedNodes, ['a', 'b', 'c']);
  assert.equal(result.blastRadius, 3);
  assert.equal(result.safe, false);
});

test('fails closed on unknown targets', () => {
  const result = simulateImpact({ nodes: [{ id: 'a' }], edges: [], action: 'block', targetIds: ['x'] });
  assert.equal(result.safe, false);
  assert.equal(result.reason, 'UNKNOWN_TARGET');
});

test('fails closed when graph exceeds limits', () => {
  const result = simulateImpact({ nodes: [{ id: 'a' }, { id: 'b' }], edges: [], action: 'block', targetIds: ['a'] }, { maxNodes: 1 });
  assert.equal(result.reason, 'SIMULATION_LIMIT_EXCEEDED');
});

test('marks critical impact unsafe', () => {
  const result = simulateImpact({
    nodes: [{ id: 'a' }, { id: 'core', critical: true }],
    edges: [{ from: 'a', to: 'core' }], action: 'isolate', targetIds: ['a'],
  });
  assert.equal(result.criticalImpact, true);
  assert.equal(result.safe, false);
});
