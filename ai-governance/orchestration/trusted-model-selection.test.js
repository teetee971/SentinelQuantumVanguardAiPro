import test from 'node:test';
import assert from 'node:assert/strict';
import { selectTrustedModel } from './trusted-model-selection.js';

function approvedModel(overrides = {}) {
  return {
    model_id: 'm-local',
    version: '1.0',
    provider: 'test',
    deployment: 'local',
    capabilities: ['reasoning'],
    allowed_data_classes: ['PUBLIC', 'HIGHLY_RESTRICTED'],
    approval: { status: 'approved', approved_for_production: true },
    evaluation: { status: 'passed', model_id: 'm-local', model_version: '1.0', suite_version: '1.0' },
    performance: { latency_ms: 50, cost: 1 },
    trust_signals: {
      sourceReliability: 0.95,
      evidenceConfidence: 0.95,
      modelReliability: 0.95,
      provenanceIntegrity: 0.95,
      uncertainty: 0.1,
    },
    ...overrides,
  };
}

test('routes only a trust-supported and policy-eligible model', () => {
  const result = selectTrustedModel({
    models: [approvedModel()],
    routing: { dataClass: 'HIGHLY_RESTRICTED', requiredCapability: 'reasoning', locality: 'local' },
    trustPolicy: { minimumScore: 0.8, maximumUncertainty: 0.2 },
  });

  assert.equal(result.allowed, true);
  assert.equal(result.reason, 'TRUSTED_MODEL_ROUTED');
  assert.equal(result.model.model_id, 'm-local');
  assert.equal(result.side_effect_performed, false);
  assert.ok(result.model.trust.score >= 0.8);
});

test('fails closed when explicit trust signals are missing', () => {
  const model = approvedModel();
  delete model.trust_signals;
  const result = selectTrustedModel({ models: [model], routing: { dataClass: 'PUBLIC' } });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'NO_TRUST_SUPPORTED_MODEL');
  assert.equal(result.assessments[0].reason, 'TRUST_SIGNALS_REQUIRED');
});

test('fails closed when trust policy is not satisfied', () => {
  const result = selectTrustedModel({
    models: [approvedModel({ trust_signals: {
      sourceReliability: 0.4,
      evidenceConfidence: 0.4,
      modelReliability: 0.4,
      provenanceIntegrity: 0.4,
      uncertainty: 0.8,
    } })],
    routing: { dataClass: 'PUBLIC' },
    trustPolicy: { minimumScore: 0.7, maximumUncertainty: 0.3 },
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'NO_TRUST_SUPPORTED_MODEL');
});

test('does not let trust assessment bypass model approval policy', () => {
  const result = selectTrustedModel({
    models: [approvedModel({ approval: { status: 'pending', approved_for_production: false } })],
    routing: { dataClass: 'PUBLIC', locality: 'local' },
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'NO_ELIGIBLE_MODEL');
  assert.equal(result.candidates[0].reason, 'MODEL_NOT_APPROVED');
});

test('preserves restricted-data locality enforcement', () => {
  const remote = approvedModel({
    model_id: 'm-remote',
    deployment: 'approved_remote',
    allowed_data_classes: ['PUBLIC'],
    evaluation: { status: 'passed', model_id: 'm-remote', model_version: '1.0', suite_version: '1.0' },
  });
  const result = selectTrustedModel({
    models: [remote],
    routing: { dataClass: 'HIGHLY_RESTRICTED', locality: 'approved_remote' },
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'NO_ELIGIBLE_MODEL');
});
