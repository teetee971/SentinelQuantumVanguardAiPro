import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, cpSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const repoRoot = resolve(import.meta.dirname, '..');

function run(script, cwd, env = {}) {
  return spawnSync(process.execPath, [join(cwd, 'scripts', script)], {
    cwd,
    encoding: 'utf8',
    env: { ...process.env, ...env },
  });
}

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-trust-'));
  mkdirSync(join(root, 'scripts'), { recursive: true });
  mkdirSync(join(root, 'artifacts', 'autonomous-engineering'), { recursive: true });
  for (const file of ['evidence-trust.js', 'autonomous-diagnostic.js', 'autonomous-remediation-plan.js']) {
    cpSync(join(repoRoot, 'scripts', file), join(root, 'scripts', file));
  }
  return root;
}

function writeReport(root) {
  writeFileSync(join(root, 'artifacts', 'autonomous-engineering', 'latest.json'), JSON.stringify({
    repository: 'teetee971/SentinelQuantumVanguardAiPro',
    commit: 'abc123',
    checks: [{ name: 'build', status: 'FAIL', exit_code: 1, stderr: 'build failed' }],
  }, null, 2));
}

test('adversarial trust boundary rejects forged verification and permits only plan-only trusted failure handling', () => {
  const root = fixture();
  try {
    writeReport(root);

    const evidenceRun = run('evidence-trust.js', root, {
      GITHUB_ACTIONS: 'true',
      GITHUB_SHA: 'abc123',
      GITHUB_RUN_ID: '123',
    });
    assert.equal(evidenceRun.status, 0, evidenceRun.stderr);
    const evidencePath = join(root, 'artifacts', 'autonomous-engineering', 'evidence.json');
    const evidence = JSON.parse(readFileSync(evidencePath, 'utf8'));
    assert.equal(evidence.verification_level, 'CI_VERIFIED');
    assert.equal(evidence.outcome, 'FAILED');

    const diagnosticRun = run('autonomous-diagnostic.js', root, {
      GITHUB_ACTIONS: 'true',
      GITHUB_SHA: 'abc123',
    });
    assert.equal(diagnosticRun.status, 0, diagnosticRun.stderr);
    const diagnosisPath = join(root, 'artifacts', 'autonomous-engineering', 'diagnosis.json');
    const diagnosis = JSON.parse(readFileSync(diagnosisPath, 'utf8'));
    assert.equal(diagnosis.verification_level, 'CI_VERIFIED');
    assert.equal(diagnosis.overall, 'REMEDIATION_CANDIDATE');
    assert.equal(diagnosis.failed_checks[0].action, 'PLAN_ONLY');

    const forged = { ...evidence, verification_level: 'PRODUCTION_VERIFIED' };
    writeFileSync(evidencePath, JSON.stringify(forged, null, 2));
    const forgedDiagnostic = run('autonomous-diagnostic.js', root, {
      GITHUB_ACTIONS: 'true',
      GITHUB_SHA: 'abc123',
    });
    assert.equal(forgedDiagnostic.status, 2);
    const blockedDiagnosis = JSON.parse(readFileSync(diagnosisPath, 'utf8'));
    assert.equal(blockedDiagnosis.verification_level, 'UNVERIFIED');
    assert.equal(blockedDiagnosis.overall, 'BLOCKED');
    assert.equal(blockedDiagnosis.evidence_reason, 'EVIDENCE_LEVEL_MISMATCH');

    const plannerRun = run('autonomous-remediation-plan.js', root, {
      GITHUB_ACTIONS: 'true',
      GITHUB_SHA: 'abc123',
    });
    assert.equal(plannerRun.status, 2);
    const plan = JSON.parse(readFileSync(join(root, 'artifacts', 'autonomous-engineering', 'remediation-plan.json'), 'utf8'));
    assert.equal(plan.status, 'BLOCKED');
    assert.deepEqual(plan.plans, []);
    assert.equal(plan.automatic_mutation, false);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
