import assert from 'node:assert/strict';
import test from 'node:test';
import { isModelEligible } from './model-policy.js';

const approvedLocal = {
  approval: { status: 'approved', approved_for_production: true },
  deployment: 'local',
  capabilities: ['text', 'structured_output'],
  allowed_data_classes: ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'HIGHLY_RESTRICTED'],
};

const approvedRemote = {
  approval: { status: 'approved', approved_for_production: true },
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
