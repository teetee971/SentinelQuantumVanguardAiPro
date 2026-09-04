import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateEvidence, hashEvidence } from './evidence-trust.js';

const baseReport = {
  repository: 'teetee971/SentinelQuantumVanguardAiPro',
  commit: 'abc123',
  checks: [{ name: 'build', status: 'PASS', exit_code: 0 }],
};

test('rejects missing evidence', () => {
  const result = evaluateEvidence(null, {});
  assert.equal(result.level, 'UNVERIFIED');
  assert.equal(result.outcome, 'UNKNOWN');
});

test('rejects unknown commit', () => {
  const result = evaluateEvidence({ ...baseReport, commit: 'LOCAL_OR_UNKNOWN' }, {});
  assert.equal(result.level, 'UNVERIFIED');
});

test('failed checks affect outcome, not provenance trust', () => {
  const result = evaluateEvidence({
    ...baseReport,
    checks: [{ name: 'build', status: 'FAIL', exit_code: 1 }],
  }, { GITHUB_ACTIONS: 'true', GITHUB_SHA: 'abc123' });
  assert.equal(result.level, 'CI_VERIFIED');
  assert.equal(result.outcome, 'FAILED');
});

test('does not call static evidence CI verified', () => {
  const result = evaluateEvidence(baseReport, {});
  assert.equal(result.level, 'STATIC_VERIFIED');
  assert.equal(result.outcome, 'PASSED');
});

test('requires commit binding for CI verification', () => {
  const result = evaluateEvidence(baseReport, {
    GITHUB_ACTIONS: 'true',
    GITHUB_SHA: 'different-commit',
  });
  assert.equal(result.level, 'STATIC_VERIFIED');
});

test('accepts CI verification only when execution and commit are bound', () => {
  const result = evaluateEvidence(baseReport, {
    GITHUB_ACTIONS: 'true',
    GITHUB_SHA: 'abc123',
  });
  assert.equal(result.level, 'CI_VERIFIED');
  assert.equal(result.outcome, 'PASSED');
});

test('never returns production verification from CI evidence', () => {
  const result = evaluateEvidence(baseReport, {
    GITHUB_ACTIONS: 'true',
    GITHUB_SHA: 'abc123',
    DEPLOYMENT_VERIFIED: 'true',
  });
  assert.notEqual(result.level, 'PRODUCTION_VERIFIED');
});

test('incomplete check evidence cannot become CI verified', () => {
  const result = evaluateEvidence({
    ...baseReport,
    checks: [{ name: 'build', status: 'SKIPPED' }],
  }, { GITHUB_ACTIONS: 'true', GITHUB_SHA: 'abc123' });
  assert.equal(result.level, 'STATIC_VERIFIED');
  assert.equal(result.outcome, 'INCOMPLETE');
});

test('evidence hash changes when evidence changes', () => {
  assert.notEqual(
    hashEvidence(baseReport),
    hashEvidence({ ...baseReport, checks: [{ name: 'build', status: 'PASS', exit_code: 0, duration_ms: 1 }] }),
  );
});
