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
import { evaluateEvidence, getRuntimeProvenance, hashEvidence } from './evidence-trust.js';

const root = resolve(process.cwd());
const reportDir = resolve(root, 'artifacts', 'autonomous-engineering');
const inputPath = resolve(reportDir, 'latest.json');
const outputPath = resolve(reportDir, 'diagnosis.json');
const evidencePath = resolve(reportDir, 'evidence.json');

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

function writeDiagnosis(diagnosis, exitCode = 0) {
  const unsigned = { ...diagnosis };
  const final = { ...diagnosis, diagnosis_hash: hashEvidence(unsigned) };
  writeFileSync(outputPath, `${JSON.stringify(final, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(final, null, 2));
  process.exitCode = exitCode;
}

if (existsSync(inputPath)) {
  let report;
  let evidence;
  try {
    report = JSON.parse(readFileSync(inputPath, 'utf8'));
    evidence = JSON.parse(readFileSync(evidencePath, 'utf8'));
  } catch {
    writeDiagnosis({
      schema_version: 2, overall: 'BLOCKED', verification_level: 'UNVERIFIED',
      automatic_mutation: false, reason: 'EVIDENCE_ARTIFACT_REQUIRED',
    }, 2);
    process.exit();
  }

  const evaluation = evaluateEvidence(report);
  const provenance = report.provenance;
  const runtime = getRuntimeProvenance();
  const exactProvenance = Boolean(
    provenance
    && evidence.repository === provenance.repository
    && evidence.commit === provenance.commit
    && evidence.workflow === provenance.workflow
    && evidence.workflow_ref === provenance.workflow_ref
    && evidence.workflow_run_id === provenance.run_id
    && evidence.run_attempt === provenance.run_attempt
    && evidence.ref === provenance.ref
    && evidence.event === provenance.event,
  );
  const ciBound = !runtime.repository || evaluation.level === 'CI_VERIFIED';
  const trusted = evaluation.valid
    && ciBound
    && evidence.evidence_hash === hashEvidence(report)
    && evidence.verification_level === evaluation.level
    && exactProvenance;
  if (!trusted) {
    writeDiagnosis({
      schema_version: 2, overall: 'BLOCKED', verification_level: 'UNVERIFIED',
      automatic_mutation: false, reason: 'EVIDENCE_TRUST_BLOCKED',
    }, 2);
    process.exit();
  }
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
    schema_version: 2,
    mode: 'deterministic-diagnosis',
    generated_at: new Date().toISOString(),
    repository: report.repository ?? 'teetee971/SentinelQuantumVanguardAiPro',
    commit: report.commit ?? 'LOCAL_OR_UNKNOWN',
    provenance,
    verification_level: evaluation.level,
    evidence_hash: hashEvidence(report),
    overall: failed.length === 0 ? 'HEALTHY' : diagnoses.some((item) => item.action === 'STOP') ? 'BLOCKED' : 'REMEDIATION_CANDIDATE',
    self_modification: false,
    automatic_mutation: false,
    failed_checks: diagnoses,
    policy: {
      unverified_or_mismatched_evidence: 'STOP',
      infrastructure_or_unknown_failure: 'STOP',
      known_deterministic_failure: 'PLAN_ONLY',
      automatic_code_edit: 'FORBIDDEN',
    },
  };

  writeDiagnosis(diagnosis);
}
