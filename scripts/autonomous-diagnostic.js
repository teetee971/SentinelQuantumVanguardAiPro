#!/usr/bin/env node
/**
 * Sentinel Autonomous Engineering — deterministic diagnostic layer.
 *
 * Classifies evidence produced by autonomous-engineering-check.js. This layer
 * never edits source code, secrets, branches or releases and never treats an
 * infrastructure failure as a code defect.
 */

import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd());
const reportDir = resolve(root, 'artifacts', 'autonomous-engineering');
const inputPath = resolve(reportDir, 'latest.json');
const outputPath = resolve(reportDir, 'diagnosis.json');

mkdirSync(reportDir, { recursive: true });

const FAIL_PATTERNS = [
  ['runner-infrastructure', /runner|provision|hosted runner|queue|resource unavailable|service unavailable/i],
  ['dependency-installation', /npm ci|npm install|ERESOLVE|EAI_AGAIN|ECONNRESET|ETIMEDOUT|package-lock/i],
  ['configuration', /configuration|config|environment variable|invalid yaml|workflow syntax/i],
  ['build', /vite|build failed|rollup|module not found|cannot find module/i],
  ['test', /test failed|assertion|expected .* received|node:test|fuzz/i],
  ['security-policy', /isolation|supply.?chain|pinning|security gate|policy|forbidden|public claim/i],
];

function classify(text) {
  for (const [category, pattern] of FAIL_PATTERNS) {
    if (pattern.test(text)) return category;
  }
  return 'unknown';
}

function confidence(category) {
  return category === 'unknown' ? 'low' : category === 'runner-infrastructure' ? 'medium' : 'high';
}

if (!existsSync(inputPath)) {
  const diagnosis = {
    schema_version: 1,
    status: 'NO_EVIDENCE',
    category: 'unknown',
    confidence: 'low',
    action: 'STOP',
    reason: 'No autonomous engineering report was found. Do not mutate the repository.',
  };
  writeFileSync(outputPath, `${JSON.stringify(diagnosis, null, 2)}\n`, 'utf8');
  process.exitCode = 2;
}

if (existsSync(inputPath)) {
  const report = JSON.parse(readFileSync(inputPath, 'utf8'));
  const failed = Array.isArray(report.checks) ? report.checks.filter((item) => item.status === 'FAIL') : [];

  const diagnoses = failed.map((item) => {
    const evidence = `${item.name}\n${item.stdout ?? ''}\n${item.stderr ?? ''}`;
    const category = classify(evidence);
    return {
      check: item.name,
      category,
      confidence: confidence(category),
      exit_code: item.exit_code,
      evidence_excerpt: `${(item.stderr || item.stdout || '').slice(-4000)}`,
      action: category === 'unknown' || category === 'runner-infrastructure' ? 'STOP' : 'PLAN_ONLY',
    };
  });

  const diagnosis = {
    schema_version: 1,
    mode: 'deterministic-diagnosis',
    generated_at: new Date().toISOString(),
    repository: report.repository ?? 'teetee971/SentinelQuantumVanguardAiPro',
    commit: report.commit ?? 'LOCAL_OR_UNKNOWN',
    overall: failed.length === 0 ? 'HEALTHY' : diagnoses.some((item) => item.action === 'STOP') ? 'BLOCKED' : 'REMEDIATION_CANDIDATE',
    self_modification: false,
    automatic_mutation: false,
    failed_checks: diagnoses,
    policy: {
      infrastructure_or_unknown_failure: 'STOP',
      known_deterministic_failure: 'PLAN_ONLY',
      automatic_code_edit: 'FORBIDDEN',
    },
  };

  writeFileSync(outputPath, `${JSON.stringify(diagnosis, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(diagnosis, null, 2));
  process.exitCode = 0;
}
