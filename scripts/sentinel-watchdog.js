#!/usr/bin/env node
/**
 * Sentinel Watchdog — lightweight 24/7 read-only integrity signal.
 * No source mutation, dependency installation, secret access, network call,
 * deployment, or privileged action is permitted in this layer.
 */
import { existsSync, lstatSync, readFileSync, readdirSync, statSync, writeFileSync, mkdirSync, appendFileSync } from 'node:fs';
import { resolve, relative } from 'node:path';
import { createHash } from 'node:crypto';

const root = resolve(process.cwd());
const artifactDir = resolve(root, 'artifacts', 'watchdog');
mkdirSync(artifactDir, { recursive: true });
const checks = [];
function check(name, passed, detail) { checks.push({ name, status: passed ? 'PASS' : 'FAIL', detail }); }

const requiredFiles = [
  'package.json', 'package-lock.json', '.node-version', 'index.html',
  '.github/workflows/autonomous-maintenance.yml',
  'scripts/autonomous-engineering-check.js', 'scripts/autonomous-diagnostic.js',
  'scripts/check-sentinel-isolation.js', 'decision-plane/safety/action-gate.js',
];

for (const file of requiredFiles) {
  const path = resolve(root, file);
  const exists = existsSync(path);
  check(`required:${file}`, exists, exists ? 'present' : 'missing');
  if (exists) {
    try { check(`not-symlink:${file}`, !lstatSync(path).isSymbolicLink(), 'control file must not be a symlink'); }
    catch (error) { check(`stat:${file}`, false, `inspection failure: ${error.message}`); }
  }
}

try {
  const pkg = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8'));
  check('package-integrity', pkg.type === 'module' && typeof pkg.scripts?.['test:autonomous-engineering'] === 'string' && typeof pkg.scripts?.['test:autonomous-diagnostic'] === 'string', 'package.json is valid and exposes autonomous controls');
} catch (error) { check('package-integrity', false, `package.json parse failure: ${error.message}`); }

try {
  const version = readFileSync(resolve(root, '.node-version'), 'utf8').trim();
  check('node-version-format', /^v?\d+\.\d+(\.\d+)?$/.test(version), `Node version: ${version || 'empty'}`);
} catch (error) { check('node-version-format', false, `read failure: ${error.message}`); }

for (const tree of ['scripts', 'security', 'decision-plane', 'config', '.github/workflows']) {
  const dir = resolve(root, tree);
  if (!existsSync(dir)) { check(`tree:${tree}`, false, 'critical tree missing'); continue; }
  try {
    const entries = readdirSync(dir, { withFileTypes: true });
    const symlinks = entries.filter((entry) => entry.isSymbolicLink()).map((entry) => relative(root, resolve(dir, entry.name)));
    check(`symlinks:${tree}`, symlinks.length === 0, symlinks.length ? `symlinks: ${symlinks.join(', ')}` : 'none at top level');
  } catch (error) { check(`tree-scan:${tree}`, false, `scan failure: ${error.message}`); }
}

const failed = checks.filter((item) => item.status === 'FAIL');
const controlHashes = {};
for (const file of requiredFiles) {
  const path = resolve(root, file);
  if (existsSync(path) && !lstatSync(path).isSymbolicLink() && statSync(path).isFile()) controlHashes[file] = createHash('sha256').update(readFileSync(path)).digest('hex');
}
const now = new Date().toISOString();
const report = {
  schema_version: 1, mode: 'watchdog-read-only', self_modification: false,
  mutation_performed: false, network_action: false, secret_access: false,
  started_at: now, completed_at: new Date().toISOString(),
  repository: 'teetee971/SentinelQuantumVanguardAiPro',
  commit: process.env.GITHUB_SHA ?? 'LOCAL_OR_UNKNOWN', healthy: failed.length === 0,
  checks, summary: { passed: checks.length - failed.length, failed: failed.length },
  control_hashes: controlHashes,
};
writeFileSync(resolve(artifactDir, 'latest.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(report, null, 2));
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Sentinel Watchdog\n\nStatus: **${report.healthy ? 'HEALTHY' : 'BLOCKED'}**\n\nPassed: **${report.summary.passed}**\nFailed: **${report.summary.failed}**\n`, 'utf8');
process.exitCode = report.healthy ? 0 : 1;
