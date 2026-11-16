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

test('rejects missing evidence', () => assert.equal(evaluateEvidence(null, {}).level, 'UNVERIFIED'));
test('requires exact CI provenance binding', () => assert.equal(evaluateEvidence(baseReport, { ...ciEnv, GITHUB_SHA: 'other' }).level, 'STATIC_VERIFIED'));
test('missing provenance cannot become CI verified', () => assert.equal(evaluateEvidence({ ...baseReport, provenance: undefined }, ciEnv).level, 'STATIC_VERIFIED'));
test('accepts only an exact CI execution identity', () => assert.equal(evaluateEvidence(baseReport, ciEnv).level, 'CI_VERIFIED'));
test('failed checks retain provenance but report a failed outcome', () => {
  const result = evaluateEvidence({ ...baseReport, checks: [{ name: 'build', status: 'FAIL', exit_code: 1 }] }, ciEnv);
  assert.equal(result.level, 'CI_VERIFIED');
  assert.equal(result.outcome, 'FAILED');
});
test('never infers production verification from CI', () => assert.notEqual(evaluateEvidence({ ...baseReport, verification_level: 'PRODUCTION_VERIFIED' }, ciEnv).level, 'PRODUCTION_VERIFIED'));
test('evidence hash covers provenance', () => assert.notEqual(hashEvidence(baseReport), hashEvidence({ ...baseReport, provenance: { ...baseReport.provenance, run_id: '101' } })));
