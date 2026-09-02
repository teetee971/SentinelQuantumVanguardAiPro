import assert from 'node:assert/strict';
import test from 'node:test';
import { evaluateActionGate } from './action-gate.js';

const safe = { score: 0.9, uncertainty: 0.1 };
const simulation = { safe: true };

const common = {
  evidenceIntegrity: true,
  trust: safe,
  policyDecision: 'allow',
  simulation,
};

test('allows a validated critical action', () => {
  const result = evaluateActionGate({ ...common, action: 'contain', targetAuthorized: true, humanValidated: true });
  assert.equal(result.allowed, true);
});

test('denies critical action without human validation', () => {
  const result = evaluateActionGate({ ...common, action: 'contain', targetAuthorized: true });
  assert.equal(result.allowed, false);
});

test('denies critical action when casing or surrounding whitespace is altered', () => {
  for (const action of ['BLOCK', ' Block ', '\tCoNtAiN\n', ' isolate ']) {
    const result = evaluateActionGate({ ...common, action, targetAuthorized: true });
    assert.equal(result.allowed, false, `expected denial for ${JSON.stringify(action)}`);
    assert.equal(result.reason, 'AUTHORIZATION_AND_HUMAN_VALIDATION_REQUIRED');
  }
});

test('allows canonicalized critical action only with all required approvals', () => {
  const result = evaluateActionGate({ ...common, action: '  CoNtAiN  ', targetAuthorized: true, humanValidated: true });
  assert.equal(result.allowed, true);
});

test('denies empty or overlong actions', () => {
  for (const action of ['', '   ', 'x'.repeat(129)]) {
    const result = evaluateActionGate({ ...common, action });
    assert.equal(result.allowed, false);
    assert.equal(result.reason, 'INVALID_ACTION');
  }
});

test('denies invalid evidence integrity', () => {
  const result = evaluateActionGate({ ...common, action: 'block', evidenceIntegrity: false, targetAuthorized: true, humanValidated: true });
  assert.equal(result.allowed, false);
});

test('denies unsafe simulation', () => {
  const result = evaluateActionGate({ ...common, action: 'block', simulation: { safe: false }, targetAuthorized: true, humanValidated: true });
  assert.equal(result.allowed, false);
});

test('denies action when trust uncertainty is missing', () => {
  const result = evaluateActionGate({
    ...common,
    action: 'observe',
    trust: { score: 0.9 },
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'TRUST_THRESHOLD_FAILED');
});

test('denies non-finite or out-of-range trust uncertainty', () => {
  for (const uncertainty of [Number.NaN, Number.POSITIVE_INFINITY, -0.1, 1]) {
    const result = evaluateActionGate({ ...common, action: 'observe', trust: { score: 0.9, uncertainty } });
    assert.equal(result.allowed, false);
    assert.equal(result.reason, 'TRUST_THRESHOLD_FAILED');
  }
});
