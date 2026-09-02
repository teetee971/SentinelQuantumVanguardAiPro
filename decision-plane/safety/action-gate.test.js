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

test('denies invalid evidence integrity', () => {
  const result = evaluateActionGate({ ...common, action: 'block', evidenceIntegrity: false, targetAuthorized: true, humanValidated: true });
  assert.equal(result.allowed, false);
});

test('denies unsafe simulation', () => {
  const result = evaluateActionGate({ ...common, action: 'block', simulation: { safe: false }, targetAuthorized: true, humanValidated: true });
  assert.equal(result.allowed, false);
});
