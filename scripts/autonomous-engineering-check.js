#!/usr/bin/env node
/**
 * Sentinel Autonomous Engineering — safe maintenance loop.
 *
 * This layer is intentionally non-self-modifying: it observes and validates
 * the repository, but never edits source code, secrets, branches or releases.
 * Future repair agents may consume its evidence without receiving authority
 * to bypass the existing security gates.
 */

import { appendFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { getRuntimeProvenance } from './evidence-trust.js';

const root = resolve(process.cwd());
const reportDir = resolve(root, 'artifacts', 'autonomous-engineering');
mkdirSync(reportDir, { recursive: true });

const checks = [
  ['isolation', 'npm', ['run', 'test:isolation']],
  ['actions-pinning', 'npm', ['run', 'test:ci-supply-chain']],
  ['static-links', 'npm', ['run', 'test:static-links']],
  ['public-claims', 'npm', ['run', 'test:public-claims']],
  ['client-security', 'npm', ['run', 'test:client-security']],
  ['android-manifest', 'npm', ['run', 'test:android-manifest']],
  ['security-health-inventory', 'npm', ['run', 'test:security-health']],
  ['build', 'npm', ['run', 'build']],
];

const startedAt = new Date().toISOString();
const results = [];

for (const [name, command, args] of checks) {
  const started = Date.now();
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, CI: 'true' },
  });
  const entry = {
    name,
    command: [command, ...args].join(' '),
    exit_code: result.status,
    signal: result.signal ?? null,
    duration_ms: Date.now() - started,
    status: result.status === 0 ? 'PASS' : 'FAIL',
    stdout: result.stdout?.slice(-12000) ?? '',
    stderr: result.stderr?.slice(-12000) ?? '',
  };
  results.push(entry);
  console.log(`[${entry.status}] ${entry.name} (${entry.duration_ms} ms)`);
}

const report = {
  schema_version: 1,
  mode: 'observe-and-validate',
  self_modification: false,
  started_at: startedAt,
  completed_at: new Date().toISOString(),
  repository: 'teetee971/SentinelQuantumVanguardAiPro',
  commit: process.env.GITHUB_SHA ?? 'LOCAL_OR_UNKNOWN',
  provenance: getRuntimeProvenance(),
  checks: results,
  summary: {
    passed: results.filter((item) => item.status === 'PASS').length,
    failed: results.filter((item) => item.status === 'FAIL').length,
  },
};

const reportPath = resolve(reportDir, 'latest.json');
writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
appendFileSync(resolve(reportDir, 'history.ndjson'), `${JSON.stringify(report)}\n`, 'utf8');

if (process.env.GITHUB_STEP_SUMMARY) {
  const lines = [
    '## Sentinel Autonomous Engineering',
    '',
    `Mode: \`${report.mode}\``,
    `Self-modification: \`${report.self_modification}\``,
    `Passed: **${report.summary.passed}**`,
    `Failed: **${report.summary.failed}**`,
    '',
    '| Check | Status | Duration |',
    '|---|---:|---:|',
    ...results.map((item) => `| ${item.name} | ${item.status} | ${item.duration_ms} ms |`),
  ];
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${lines.join('\n')}\n`, 'utf8');
}

process.exitCode = report.summary.failed === 0 ? 0 : 1;
