#!/usr/bin/env node
/**
 * Sentinel Autonomous Engineering — deterministic diagnostic layer.
 *
 * Trust is bound to the exact current CI execution. A report/evidence pair
 * from an earlier run, rerun attempt, workflow, ref or event is not actionable.
 */

import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { evaluateEvidence, getRuntimeProvenance, hashEvidence } from './evidence-trust.js';

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
  for (const [category, pattern] of FAIL_PATTERNS) if (pattern.test(text)) return category;
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

function evidenceProvenanceMatches(evidence, report) {
  const p = report?.provenance;
  return Boolean(
    p &&
    evidence?.repository === p.repository &&
    evidence?.commit === p.commit &&
    evidence?.workflow === p.workflow &&
    evidence?.workflow_ref === p.workflow_ref &&
    evidence?.workflow_run_id === p.run_id &&
    evidence?.run_attempt === p.run_attempt &&
    evidence?.ref === p.ref &&
    evidence?.event === p.event
  );
}

if (!existsSync(inputPath)) {
  writeDiagnosis({ schema_version: 2, status: 'NO_EVIDENCE', verification_level: 'UNVERIFIED', category: 'unknown', confidence: 'low', action: 'STOP', automatic_mutation: false, reason: 'No autonomous engineering report was found. Do not mutate the repository.' }, 2);
} else {
  let report = null;
  try {
    report = JSON.parse(readFileSync(inputPath, 'utf8'));
  } catch {
    writeDiagnosis({ schema_version: 2, status: 'INVALID', verification_level: 'UNVERIFIED', category: 'unknown', confidence: 'low', action: 'STOP', automatic_mutation: false, reason: 'The autonomous engineering report is malformed. Evidence cannot be trusted.' }, 2);
  }

  if (report) {
    let evidence = null;
    if (existsSync(evidencePath)) {
      try { evidence = JSON.parse(readFileSync(evidencePath, 'utf8')); } catch { evidence = null; }
    }

    const computed = evaluateEvidence(report);
    const runtime = getRuntimeProvenance();
    const evidenceHash = hashEvidence(report);
    const evidenceMatches = evidence?.evidence_hash === evidenceHash;
    const evidenceLevelMatches = evidence?.verification_level === computed.level;
    const evidenceRepositoryMatches = evidence?.repository === report.repository;
    const evidenceCommitMatches = evidence?.commit === report.commit;
    const evidenceProvenanceConsistent = evidenceProvenanceMatches(evidence, report);
    const ciExecutionFullyBound = runtime.repository
      ? computed.level === 'CI_VERIFIED'
      : true;
    const attestationConsistent = evidenceMatches && evidenceLevelMatches && evidenceRepositoryMatches && evidenceCommitMatches && evidenceProvenanceConsistent && computed.valid && ciExecutionFullyBound;
    const evidenceLevel = attestationConsistent ? computed.level : 'UNVERIFIED';
    const trustBlocked = !attestationConsistent || !computed.valid || evidenceLevel === 'UNVERIFIED' || evidenceLevel === 'BLOCKED';

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

    let reason = 'EVIDENCE_TRUST_BLOCKED';
    if (!computed.valid) reason = computed.reason;
    else if (!ciExecutionFullyBound) reason = computed.reason;
    else if (!evidenceMatches) reason = 'EVIDENCE_HASH_MISMATCH';
    else if (!evidenceProvenanceConsistent) reason = 'EVIDENCE_PROVENANCE_MISMATCH';
    else if (!evidenceLevelMatches) reason = 'EVIDENCE_LEVEL_MISMATCH';
    else if (!evidenceRepositoryMatches) reason = 'EVIDENCE_REPOSITORY_MISMATCH';
    else if (!evidenceCommitMatches) reason = 'EVIDENCE_COMMIT_MISMATCH';

    writeDiagnosis({
      schema_version: 2,
      mode: 'deterministic-diagnosis',
      generated_at: new Date().toISOString(),
      repository: report.repository ?? 'teetee971/SentinelQuantumVanguardAiPro',
      commit: report.commit ?? 'LOCAL_OR_UNKNOWN',
      provenance: report.provenance ?? null,
      overall,
      verification_level: evidenceLevel,
      evidence_status: attestationConsistent ? evidence?.status ?? 'EVALUATED' : 'MISMATCH',
      evidence_reason: attestationConsistent ? evidence?.reason ?? computed.reason : reason,
      evidence_hash: evidenceHash,
      evidence_outcome: computed.outcome,
      self_modification: false,
      automatic_mutation: false,
      failed_checks: diagnoses,
      policy: {
        unverified_or_blocked_evidence: 'STOP',
        provenance_mismatch: 'STOP',
        infrastructure_or_unknown_failure: 'STOP',
        known_deterministic_failure: 'PLAN_ONLY',
        automatic_code_edit: 'FORBIDDEN',
        production_verification_inference: 'FORBIDDEN',
      },
    }, trustBlocked ? 2 : 0);
  }
}
