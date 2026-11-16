import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateModelForApproval } from './model-evaluation-gate.js';

const model = Object.freeze({ model_id: 'model-a', version: '1.0.0' });
const evidence = Object.freeze([{ id: 'e1', content: 'trusted observation' }]);

function passingInput(overrides = {}) {
  return {
    output: {
      decision: 'allow',
      evidence_ids: ['e1'],
      action: 'allow',
    },
    evidence,
    untrusted_evidence: [],
    input: { request_id: 'r1' },
    baseline_score: 1,
    suite_version: '1.0.0',
    ...overrides,
  };
}

test('connects evaluation to approval without performing a production side effect', () => {
  const result = evaluateModelForApproval({ model, evaluationInput: passingInput() });
  assert.equal(result.allowed, true);
  assert.equal(result.reason, 'MODEL_EVALUATION_APPROVED');
  assert.equal(result.evaluation.model_id, model.model_id);
  assert.equal(result.evaluation.version, model.version);
  assert.equal(result.production_side_effect_performed, false);
});

test('fails closed when model binding is missing', () => {
  const result = evaluateModelForApproval({ model: {}, evaluationInput: passingInput() });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'MODEL_BINDING_REQUIRED');
});

test('fails closed when evaluation input is missing', () => {
  const result = evaluateModelForApproval({ model, evaluationInput: null });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'EVALUATION_INPUT_REQUIRED');
});

test('denies approval when a mandatory evaluation dimension fails', () => {
  const result = evaluateModelForApproval({
    model,
    evaluationInput: passingInput({
      output: { decision: 'allow', evidence_ids: ['unknown-evidence'], action: 'allow' },
    }),
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'EVALUATION_FAILED');
  assert.equal(result.evaluation.overall.passed, false);
});

test('caller cannot substitute another model binding through evaluation input', () => {
  const result = evaluateModelForApproval({
    model,
    evaluationInput: passingInput({ model_id: 'attacker-model', version: '9.9.9' }),
  });
  assert.equal(result.allowed, true);
  assert.equal(result.evaluation.model_id, 'model-a');
  assert.equal(result.evaluation.version, '1.0.0');
});
