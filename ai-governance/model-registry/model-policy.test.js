import assert from 'node:assert/strict';
import test from 'node:test';
import { isModelEligible } from './model-policy.js';

const approvedLocal = {
  model_id: 'sentinel-test-model',
  version: '1.0.0',
  approval: { status: 'approved', approved_for_production: true },
  evaluation: { status: 'passed', suite_version: '1.0.0', model_id: 'sentinel-test-model', model_version: '1.0.0' },
  deployment: 'local',
  capabilities: ['text', 'structured_output'],
  allowed_data_classes: ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'HIGHLY_RESTRICTED'],
};

const approvedRemote = {
  ...approvedLocal,
  deployment: 'approved_remote',
  capabilities: ['text'],
  allowed_data_classes: ['PUBLIC', 'INTERNAL'],
};

test('allows approved local model for restricted data', () => {
  assert.deepEqual(isModelEligible(approvedLocal, 'HIGHLY_RESTRICTED', { required_capability: 'text' }), {
    allowed: true,
    reason: 'POLICY_ALLOW',
  });
});

test('rejects remote model for confidential data', () => {
  assert.equal(isModelEligible(approvedRemote, 'CONFIDENTIAL').reason, 'DATA_CLASS_NOT_ALLOWED');
});

test('rejects unapproved model', () => {
  assert.equal(isModelEligible({ ...approvedLocal, approval: { status: 'pending', approved_for_production: false } }, 'PUBLIC').reason, 'MODEL_NOT_APPROVED');
});

test('rejects unsupported capability', () => {
  assert.equal(isModelEligible(approvedLocal, 'INTERNAL', { required_capability: 'vision' }).reason, 'CAPABILITY_NOT_SUPPORTED');
});

test('rejects production model without a passed evaluation', () => {
  const model = { ...approvedLocal, evaluation: { ...approvedLocal.evaluation, status: 'failed' } };
  assert.equal(isModelEligible(model, 'PUBLIC').reason, 'EVALUATION_REQUIRED');
});

test('rejects an evaluation bound to a different model version', () => {
  const model = { ...approvedLocal, evaluation: { ...approvedLocal.evaluation, model_version: '2.0.0' } };
  assert.equal(isModelEligible(model, 'PUBLIC').reason, 'EVALUATION_BINDING_MISMATCH');
});
