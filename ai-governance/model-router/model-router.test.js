import assert from 'node:assert/strict';
import test from 'node:test';
import { routeModel } from './model-router.js';

const baseEvaluation = { status: 'passed', model_id: 'm-local', model_version: '1.0', suite_version: '1.0' };
const local = {
  model_id: 'm-local', version: '1.0', provider: 'test', deployment: 'local',
  capabilities: ['text', 'reasoning'], allowed_data_classes: ['PUBLIC', 'HIGHLY_RESTRICTED'],
  approval: { status: 'approved', approved_for_production: true }, evaluation: baseEvaluation,
  trust: { score: 0.9 }, performance: { latency_ms: 50, cost: 1 },
};

const remote = {
  ...local,
  model_id: 'm-remote', deployment: 'approved_remote', allowed_data_classes: ['PUBLIC'],
  evaluation: { ...baseEvaluation, model_id: 'm-remote' }, trust: { score: 0.99 },
};

test('routes approved local model for restricted data', () => {
  const result = routeModel([remote, local], { dataClass: 'HIGHLY_RESTRICTED', requiredCapability: 'reasoning' });
  assert.equal(result.allowed, true);
  assert.equal(result.model.model_id, 'm-local');
});

test('fails closed when no model is eligible', () => {
  const result = routeModel([remote], { dataClass: 'HIGHLY_RESTRICTED' });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'NO_ELIGIBLE_MODEL');
});

test('does not route below minimum trust', () => {
  const result = routeModel([{ ...local, trust: { score: 0.4 } }], { dataClass: 'PUBLIC', minimumTrustScore: 0.7 });
  assert.equal(result.allowed, false);
});
