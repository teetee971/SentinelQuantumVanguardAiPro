import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, cpSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const repoRoot = resolve(import.meta.dirname, '..');
function run(script, cwd, env = {}) {
  return spawnSync(process.execPath, [join(cwd, 'scripts', script)], { cwd, encoding: 'utf8', env: { ...process.env, ...env } });
}
const ci = (attempt = '1', runId = '123') => ({
  GITHUB_ACTIONS: 'true', GITHUB_REPOSITORY: 'teetee971/SentinelQuantumVanguardAiPro', GITHUB_SHA: 'abc123',
  GITHUB_WORKFLOW: 'Sentinel Autonomous Maintenance',
  GITHUB_WORKFLOW_REF: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/autonomous-maintenance.yml@refs/heads/main',
  GITHUB_RUN_ID: runId, GITHUB_RUN_ATTEMPT: attempt, GITHUB_REF: 'refs/heads/main', GITHUB_EVENT_NAME: 'schedule',
});
function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-trust-'));
  mkdirSync(join(root, 'scripts'), { recursive: true }); mkdirSync(join(root, 'artifacts', 'autonomous-engineering'), { recursive: true });
  for (const file of ['evidence-trust.js', 'autonomous-diagnostic.js', 'autonomous-remediation-plan.js']) cpSync(join(repoRoot, 'scripts', file), join(root, 'scripts', file));
  return root;
}
function writeReport(root, runId = '123', attempt = '1') {
  writeFileSync(join(root, 'artifacts', 'autonomous-engineering', 'latest.json'), JSON.stringify({
    repository: 'teetee971/SentinelQuantumVanguardAiPro', commit: 'abc123',
    provenance: {
      repository: 'teetee971/SentinelQuantumVanguardAiPro', commit: 'abc123', workflow: 'Sentinel Autonomous Maintenance',
      workflow_ref: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/autonomous-maintenance.yml@refs/heads/main',
      run_id: runId, run_attempt: attempt, ref: 'refs/heads/main', event: 'schedule',
    }, checks: [{ name: 'build', status: 'FAIL', exit_code: 1, stderr: 'build failed' }],
  }, null, 2));
}

test('adversarial trust boundary rejects forged verification and permits only plan-only trusted failure handling', () => {
  const root = fixture();
  try {
    writeReport(root);
    const evidenceRun = run('evidence-trust.js', root, ci()); assert.equal(evidenceRun.status, 0, evidenceRun.stderr);
    const evidencePath = join(root, 'artifacts', 'autonomous-engineering', 'evidence.json');
    const evidence = JSON.parse(readFileSync(evidencePath, 'utf8')); assert.equal(evidence.verification_level, 'CI_VERIFIED'); assert.equal(evidence.outcome, 'FAILED');
    const diagnosticRun = run('autonomous-diagnostic.js', root, ci()); assert.equal(diagnosticRun.status, 0, diagnosticRun.stderr);
    const diagnosisPath = join(root, 'artifacts', 'autonomous-engineering', 'diagnosis.json'); const diagnosis = JSON.parse(readFileSync(diagnosisPath, 'utf8'));
    assert.equal(diagnosis.verification_level, 'CI_VERIFIED'); assert.equal(diagnosis.overall, 'REMEDIATION_CANDIDATE'); assert.equal(diagnosis.failed_checks[0].action, 'PLAN_ONLY');
    writeFileSync(evidencePath, JSON.stringify({ ...evidence, verification_level: 'PRODUCTION_VERIFIED' }, null, 2));
    const forgedDiagnostic = run('autonomous-diagnostic.js', root, ci()); assert.equal(forgedDiagnostic.status, 2);
    const blockedDiagnosis = JSON.parse(readFileSync(diagnosisPath, 'utf8')); assert.equal(blockedDiagnosis.verification_level, 'UNVERIFIED'); assert.equal(blockedDiagnosis.overall, 'BLOCKED'); assert.equal(blockedDiagnosis.evidence_reason, 'EVIDENCE_LEVEL_MISMATCH');
    const plannerRun = run('autonomous-remediation-plan.js', root, ci()); assert.equal(plannerRun.status, 2);
    const plan = JSON.parse(readFileSync(join(root, 'artifacts', 'autonomous-engineering', 'remediation-plan.json'), 'utf8')); assert.equal(plan.status, 'BLOCKED'); assert.deepEqual(plan.plans, []); assert.equal(plan.automatic_mutation, false);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('replay from another run id is blocked even when commit is identical', () => {
  const root = fixture();
  try {
    writeReport(root, '123', '1'); assert.equal(run('evidence-trust.js', root, ci('1', '123')).status, 0);
    const replay = run('autonomous-diagnostic.js', root, ci('1', '999')); assert.equal(replay.status, 2);
    const diagnosis = JSON.parse(readFileSync(join(root, 'artifacts', 'autonomous-engineering', 'diagnosis.json'), 'utf8')); assert.equal(diagnosis.overall, 'BLOCKED');
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('replay from another rerun attempt is blocked', () => {
  const root = fixture();
  try {
    writeReport(root, '123', '1'); assert.equal(run('evidence-trust.js', root, ci('1', '123')).status, 0);
    const replay = run('autonomous-diagnostic.js', root, ci('2', '123')); assert.equal(replay.status, 2);
    const diagnosis = JSON.parse(readFileSync(join(root, 'artifacts', 'autonomous-engineering', 'diagnosis.json'), 'utf8')); assert.equal(diagnosis.overall, 'BLOCKED');
  } finally { rmSync(root, { recursive: true, force: true }); }
});
