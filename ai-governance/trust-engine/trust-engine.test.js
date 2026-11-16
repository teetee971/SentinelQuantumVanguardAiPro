import assert from 'node:assert/strict';
import test from 'node:test';
import { assessTrust, canSupportDecision } from './trust-engine.js';

test('trust score is bounded and deterministic', () => {
  const result = assessTrust({
    sourceReliability: 1,
    evidenceConfidence: 1,
    modelReliability: 1,
    provenanceIntegrity: 1,
    uncertainty: 0,
  });
  assert.equal(result.score, 1);
  assert.equal(result.uncertainty, 0);
});

test('uncertainty reduces trust', () => {
  const low = assessTrust({ uncertainty: 0 });
  const high = assessTrust({ uncertainty: 1 });
  assert.ok(low.score > high.score);
});

test('decision support fails closed below thresholds', () => {
  assert.equal(canSupportDecision({ score: 0.69, uncertainty: 0.1 }), false);
  assert.equal(canSupportDecision({ score: 0.9, uncertainty: 0.31 }), false);
  assert.equal(canSupportDecision({ score: 0.9, uncertainty: 0.2 }), true);
  assert.equal(canSupportDecision(null), false);
});
