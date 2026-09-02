import assert from 'node:assert/strict';
import test from 'node:test';
import { canApproveForProduction } from './approval-gate.js';

const model = { model_id: 'sentinel-test-model', version: '1.0.0' };
const evaluation = {
  model_id: 'sentinel-test-model',
  version: '1.0.0',
  suite_version: '1.0.0',
  evaluations: [{ test_id: 'AI-01', dimension: 'grounding', passed: true, score: 1 }],
  overall: { passed: true, score: 1, failed_dimensions: [] },
  binding: { model_id: 'sentinel-test-model', version: '1.0.0', suite_version: '1.0.0' },
};

test('allows production approval for a passing, correctly bound evaluation', () => {
  assert.deepEqual(canApproveForProduction(model, evaluation), {
    allowed: true,
    reason: 'EVALUATION_APPROVAL_ALLOW',
  });
});

test('rejects failed evaluations', () => {
  assert.equal(canApproveForProduction(model, { ...evaluation, overall: { ...evaluation.overall, passed: false } }).reason, 'EVALUATION_FAILED');
});

test('rejects evaluation/model binding mismatch', () => {
  assert.equal(canApproveForProduction(model, { ...evaluation, version: '2.0.0' }).reason, 'EVALUATION_BINDING_MISMATCH');
});

test('rejects incomplete evaluation records', () => {
  assert.equal(canApproveForProduction(model, { ...evaluation, evaluations: [] }).reason, 'EVALUATION_RECORD_INCOMPLETE');
});
