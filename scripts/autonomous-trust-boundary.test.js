import test from 'node:test';
import assert from 'node:assert/strict';
import { cpSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const repoRoot = resolve(import.meta.dirname, '..');
const ci = (attempt = '1', runId = '123') => ({
  GITHUB_ACTIONS: 'true', GITHUB_REPOSITORY: 'teetee971/SentinelQuantumVanguardAiPro',
  GITHUB_SHA: 'abc123', GITHUB_WORKFLOW: 'Sentinel Autonomous Maintenance',
  GITHUB_WORKFLOW_REF: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/autonomous-maintenance.yml@refs/heads/main',
  GITHUB_RUN_ID: runId, GITHUB_RUN_ATTEMPT: attempt, GITHUB_REF: 'refs/heads/main', GITHUB_EVENT_NAME: 'schedule',
});

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-trust-'));
  mkdirSync(join(root, 'scripts'), { recursive: true });
  mkdirSync(join(root, 'artifacts', 'autonomous-engineering'), { recursive: true });
  for (const file of ['evidence-trust.js', 'autonomous-diagnostic.js', 'autonomous-remediation-plan.js']) {
    cpSync(join(repoRoot, 'scripts', file), join(root, 'scripts', file));
  }
  return root;
}

function run(script, cwd, env) {
  return spawnSync(process.execPath, [join(cwd, 'scripts', script)], { cwd, encoding: 'utf8', env: { ...process.env, ...env } });
}

function writeReport(root, runId = '123', attempt = '1') {
  writeFileSync(join(root, 'artifacts', 'autonomous-engineering', 'latest.json'), JSON.stringify({
    repository: 'teetee971/SentinelQuantumVanguardAiPro', commit: 'abc123',
    provenance: { repository: 'teetee971/SentinelQuantumVanguardAiPro', commit: 'abc123', workflow: 'Sentinel Autonomous Maintenance', workflow_ref: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/autonomous-maintenance.yml@refs/heads/main', run_id: runId, run_attempt: attempt, ref: 'refs/heads/main', event: 'schedule' },
    checks: [{ name: 'build', status: 'FAIL', exit_code: 1 }],
  }));
}

test('forged production verification is blocked and cannot create a remediation plan', () => {
  const root = fixture();
  try {
    writeReport(root);
    assert.equal(run('evidence-trust.js', root, ci()).status, 0);
    const evidencePath = join(root, 'artifacts', 'autonomous-engineering', 'evidence.json');
    const evidence = JSON.parse(readFileSync(evidencePath, 'utf8'));
    writeFileSync(evidencePath, JSON.stringify({ ...evidence, verification_level: 'PRODUCTION_VERIFIED' }));
    assert.equal(run('autonomous-diagnostic.js', root, ci()).status, 2);
    assert.equal(run('autonomous-remediation-plan.js', root, ci()).status, 2);
    assert.deepEqual(JSON.parse(readFileSync(join(root, 'artifacts', 'autonomous-engineering', 'remediation-plan.json'), 'utf8')).plans, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('evidence from a different run is blocked even when the commit matches', () => {
  const root = fixture();
  try {
    writeReport(root);
    assert.equal(run('evidence-trust.js', root, ci()).status, 0);
    assert.equal(run('autonomous-diagnostic.js', root, ci('1', '999')).status, 2);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
