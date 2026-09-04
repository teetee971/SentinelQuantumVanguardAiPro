#!/usr/bin/env node
/**
 * Sentinel Autonomous Engineering — deterministic diagnostic layer.
 *
 * Consumes the engineering report and the Evidence/Trust decision. This layer
 * never edits source code, secrets, branches or releases and never upgrades
 * evidence beyond what the current execution proves.
 */

import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { evaluateEvidence, hashEvidence } from './evidence-trust.js';

const root = resolve(process.cwd());
const reportDir = resolve(root, 'artifacts', 'autonomous-engineering');
const inputPath = resolve(reportDir, 'latest.json');
const evidencePath = resolve(reportDir, 'evidence.json');
const outputPath = resolve(reportDir, 'diagnosis.json');

mkdirSync(reportDir, { recursive: true });

const FAIL_PATTERNS = [
  ['runner-infrastructure', /runner|provision|hosted runner|queue|resource unavailable|service unavailable/i],
  ['dependency-installation', /npm (?:ci|install)|ERESOLVE|EAI_AGAIN|ECONNRESET|ETIMEDOUT|package-lock/i],
  ['configuration', /invalid (?:yaml|configuration)|workflow syntax|environment variable.*(?:missing|invalid)/i],
  ['build', /vite .*build|build failed|rollup|module not found|cannot find module/i],
  ['test', /test failed|assertion|expected .*received|node:test|fuzz/i],
  ['security-policy', /isolation|supply.?chain|pinning|security gate|security policy|forbidden|public claim/i],
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

function writeDiagnosis(value, exitCode = 0) {
  writeFileSync(outputPath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(value, null, 2));
  process.exitCode = exitCode;
}

if (!existsSync(inputPath)) {
  writeDiagnosis({
    schema_version: 1,
    status: 'NO_EVIDENCE',
    verification_level: 'UNVERIFIED',
    category: 'unknown',
    confidence: 'low',
    action: 'STOP',
    automatic_mutation: false,
    reason: 'No autonomous engineering report was found. Do not mutate the repository.',
  }, 2);
} else {
  let report = null;
  try {
    report = JSON.parse(readFileSync(inputPath, 'utf8'));
  } catch {
    writeDiagnosis({
      schema_version: 1,
      status: 'INVALID',
      verification_level: 'UNVERIFIED',
      category: 'unknown',
      confidence: 'low',
      action: 'STOP',
      automatic_mutation: false,
      reason: 'The autonomous engineering report is malformed. Evidence cannot be trusted.',
    }, 2);
  }

  if (report) {
    let evidence = null;
    if (existsSync(evidencePath)) {
      try {
        evidence = JSON.parse(readFileSync(evidencePath, 'utf8'));
      } catch {
        evidence = null;
      }
    }

    const computed = evaluateEvidence(report);
    const evidenceHash = hashEvidence(report);
    const evidenceMatches = evidence?.evidence_hash === evidenceHash;
    const evidenceLevel = evidenceMatches && typeof evidence?.verification_level === 'string'
      ? evidence.verification_level
      : computed.level;
    const trustBlocked = !evidenceMatches || evidenceLevel === 'UNVERIFIED' || evidenceLevel === 'BLOCKED';

    const failed = Array.isArray(report.checks) ? report.checks.filter((item) => item?.status === 'FAIL') : [];
    const diagnoses = failed.map((item) => {
      const text = `${item?.name ?? ''}\n${item?.stdout ?? ''}\n${item?.stderr ?? ''}`;
      const category = classify(text);
      return {
        check: item?.name ?? 'UNKNOWN_CHECK',
        category,
        confidence: confidence(category),
        exit_code: item?.exit_code ?? null,
        evidence_excerpt: `${(item?.stderr || item?.stdout || '').slice(-4000)}`,
        action: trustBlocked || category === 'unknown' || category === 'runner-infrastructure' ? 'STOP' : 'PLAN_ONLY',
      };
    });

    const overall = trustBlocked
      ? 'BLOCKED'
      : failed.length === 0
        ? 'HEALTHY'
        : diagnoses.some((item) => item.action === 'STOP')
          ? 'BLOCKED'
          : 'REMEDIATION_CANDIDATE';

    writeDiagnosis({
      schema_version: 1,
      mode: 'deterministic-diagnosis',
      generated_at: new Date().toISOString(),
      repository: report.repository ?? 'teetee971/SentinelQuantumVanguardAiPro',
      commit: report.commit ?? 'LOCAL_OR_UNKNOWN',
      overall,
      verification_level: evidenceLevel,
      evidence_status: evidenceMatches ? evidence?.status ?? 'EVALUATED' : 'MISMATCH',
      evidence_reason: evidenceMatches ? evidence?.reason ?? computed.reason : 'EVIDENCE_HASH_MISMATCH',
      evidence_hash: evidenceHash,
      self_modification: false,
      automatic_mutation: false,
      failed_checks: diagnoses,
      policy: {
        unverified_or_blocked_evidence: 'STOP',
        infrastructure_or_unknown_failure: 'STOP',
        known_deterministic_failure: 'PLAN_ONLY',
        automatic_code_edit: 'FORBIDDEN',
        production_verification_inference: 'FORBIDDEN',
      },
    }, trustBlocked ? 2 : 0);
  }
}
