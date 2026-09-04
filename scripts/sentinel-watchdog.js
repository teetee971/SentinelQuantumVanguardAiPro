#!/usr/bin/env node
/**
 * Sentinel Watchdog — lightweight 24/7 health signal.
 *
 * Intentionally read-only: no source mutation, no secret access, no deploys.
 * It verifies only local invariants that are cheap enough for frequent runs.
 */

import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd());
const startedAt = new Date().toISOString();
const checks = [];

function check(name, passed, detail) {
  checks.push({ name, status: passed ? 'PASS' : 'FAIL', detail });
}

check('package-json', existsSync(resolve(root, 'package.json')), 'package.json must exist');
check('lockfile', existsSync(resolve(root, 'package-lock.json')), 'package-lock.json must exist');
check('node-version', existsSync(resolve(root, '.node-version')), '.node-version must exist');
check('autonomous-check', existsSync(resolve(root, 'scripts/autonomous-engineering-check.js')), 'autonomous engineering check must exist');
check('isolation-check', existsSync(resolve(root, 'scripts/check-sentinel-isolation.js')), 'Sentinel isolation check must exist');
check('security-policy', existsSync(resolve(root, 'decision-plane/safety/action-gate.js')), 'action gate must exist');

let packageValid = false;
try {
  const pkg = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8'));
  packageValid = pkg.type === 'module' && typeof pkg.scripts?.['test:autonomous-engineering'] === 'string';
  check('package-integrity', packageValid, 'package.json is valid and exposes the autonomous engineering check');
} catch (error) {
  check('package-integrity', false, `package.json parse failure: ${error.message}`);
}

const failed = checks.filter((item) => item.status === 'FAIL');
const report = {
  schema_version: 1,
  mode: 'watchdog-read-only',
  self_modification: false,
  started_at: startedAt,
  completed_at: new Date().toISOString(),
  repository: 'teetee971/SentinelQuantumVanguardAiPro',
  commit: process.env.GITHUB_SHA ?? 'LOCAL_OR_UNKNOWN',
  checks,
  summary: { passed: checks.length - failed.length, failed: failed.length },
};

console.log(JSON.stringify(report, null, 2));
if (process.env.GITHUB_STEP_SUMMARY) {
  const lines = [
    '## Sentinel Watchdog',
    '',
    `Status: **${failed.length === 0 ? 'HEALTHY' : 'DEGRADED'}**`,
    `Passed: **${report.summary.passed}**`,
    `Failed: **${report.summary.failed}**`,
  ];
  require('node:fs').appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${lines.join('\n')}\n`, 'utf8');
}

process.exitCode = failed.length === 0 ? 0 : 1;
