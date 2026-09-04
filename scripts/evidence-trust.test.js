import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateEvidence, hashEvidence } from './evidence-trust.js';

const ciEnv = {
  GITHUB_ACTIONS: 'true',
  GITHUB_REPOSITORY: 'teetee971/SentinelQuantumVanguardAiPro',
  GITHUB_SHA: 'abc123',
  GITHUB_WORKFLOW: 'Sentinel Autonomous Maintenance',
  GITHUB_WORKFLOW_REF: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/autonomous-maintenance.yml@refs/heads/main',
  GITHUB_RUN_ID: '100',
  GITHUB_RUN_ATTEMPT: '1',
  GITHUB_REF: 'refs/heads/main',
  GITHUB_EVENT_NAME: 'schedule',
};

const baseReport = {
  repository: 'teetee971/SentinelQuantumVanguardAiPro',
  commit: 'abc123',
  provenance: {
    repository: 'teetee971/SentinelQuantumVanguardAiPro',
    commit: 'abc123',
    workflow: 'Sentinel Autonomous Maintenance',
    workflow_ref: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/autonomous-maintenance.yml@refs/heads/main',
    run_id: '100',
    run_attempt: '1',
    ref: 'refs/heads/main',
    event: 'schedule',
  },
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
  const result = evaluateEvidence({ ...baseReport, checks: [{ name: 'build', status: 'FAIL', exit_code: 1 }] }, ciEnv);
  assert.equal(result.level, 'CI_VERIFIED');
  assert.equal(result.outcome, 'FAILED');
});

test('does not call static evidence CI verified', () => {
  const result = evaluateEvidence(baseReport, {});
  assert.equal(result.level, 'STATIC_VERIFIED');
  assert.equal(result.outcome, 'PASSED');
});

test('requires exact CI provenance binding', () => {
  const result = evaluateEvidence(baseReport, { ...ciEnv, GITHUB_SHA: 'different-commit' });
  assert.equal(result.level, 'STATIC_VERIFIED');
});

test('requires run id binding', () => {
  const result = evaluateEvidence(baseReport, { ...ciEnv, GITHUB_RUN_ID: '101' });
  assert.equal(result.level, 'STATIC_VERIFIED');
  assert.equal(result.reason, 'CI_PROVENANCE_MISMATCH:run_id');
});

test('requires rerun attempt binding', () => {
  const result = evaluateEvidence(baseReport, { ...ciEnv, GITHUB_RUN_ATTEMPT: '2' });
  assert.equal(result.level, 'STATIC_VERIFIED');
  assert.equal(result.reason, 'CI_PROVENANCE_MISMATCH:run_attempt');
});

test('requires workflow/ref/event binding', () => {
  for (const [key, value, expected] of [
    ['GITHUB_WORKFLOW', 'Other workflow', 'workflow'],
    ['GITHUB_WORKFLOW_REF', 'other/ref', 'workflow_ref'],
    ['GITHUB_REF', 'refs/heads/security', 'ref'],
    ['GITHUB_EVENT_NAME', 'push', 'event'],
  ]) {
    const result = evaluateEvidence(baseReport, { ...ciEnv, [key]: value });
    assert.equal(result.level, 'STATIC_VERIFIED');
    assert.equal(result.reason, `CI_PROVENANCE_MISMATCH:${expected}`);
  }
});

test('missing provenance can never become CI verified', () => {
  const result = evaluateEvidence({ ...baseReport, provenance: undefined }, ciEnv);
  assert.equal(result.level, 'STATIC_VERIFIED');
  assert.equal(result.reason, 'CI_RUN_BINDING_INCOMPLETE');
});

test('accepts CI verification only for exact execution identity', () => {
  const result = evaluateEvidence(baseReport, ciEnv);
  assert.equal(result.level, 'CI_VERIFIED');
  assert.equal(result.outcome, 'PASSED');
});

test('never returns production verification from CI evidence', () => {
  const result = evaluateEvidence({ ...baseReport, verification_level: 'PRODUCTION_VERIFIED' }, { ...ciEnv, DEPLOYMENT_VERIFIED: 'true' });
  assert.notEqual(result.level, 'PRODUCTION_VERIFIED');
});

test('incomplete check evidence cannot become CI verified', () => {
  const result = evaluateEvidence({ ...baseReport, checks: [{ name: 'build', status: 'SKIPPED' }] }, ciEnv);
  assert.equal(result.level, 'STATIC_VERIFIED');
  assert.equal(result.outcome, 'INCOMPLETE');
});

test('evidence hash changes when provenance changes', () => {
  assert.notEqual(hashEvidence(baseReport), hashEvidence({ ...baseReport, provenance: { ...baseReport.provenance, run_id: '101' } }));
});
