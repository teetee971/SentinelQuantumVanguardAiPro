#!/usr/bin/env node
/**
 * Sentinel bounded remediation planner.
 *
 * PLAN_ONLY by design: it proposes deterministic next actions from a
 * diagnostic report but never edits source, secrets, branches or releases.
 * Unverified or blocked evidence produces no actionable plan.
 */
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { hashEvidence } from './evidence-trust.js';

const root = resolve(process.cwd());
const dir = resolve(root, 'artifacts', 'autonomous-engineering');
const input = resolve(dir, 'diagnosis.json');
const evidenceInput = resolve(dir, 'evidence.json');
const latestInput = resolve(dir, 'latest.json');
const output = resolve(dir, 'remediation-plan.json');
mkdirSync(dir, { recursive: true });

const catalog = Object.freeze({
  'runner-infrastructure': { id: 'R001', action: 'RETRY_OBSERVATION', risk: 'LOW', mutation: false },
  'dependency-installation': { id: 'R002', action: 'REINSTALL_LOCKED_DEPENDENCIES', risk: 'LOW', mutation: false },
  configuration: { id: 'R003', action: 'VALIDATE_CONFIGURATION', risk: 'LOW', mutation: false },
  build: { id: 'R004', action: 'REBUILD_AND_CAPTURE_EVIDENCE', risk: 'LOW', mutation: false },
  test: { id: 'R005', action: 'RERUN_FAILED_DETERMINISTIC_TEST', risk: 'LOW', mutation: false },
  'security-policy': { id: 'R006', action: 'STOP_AND_SECURITY_REVIEW', risk: 'HIGH', mutation: false },
  unknown: { id: 'R999', action: 'STOP_AND_ESCALATE', risk: 'CRITICAL', mutation: false },
});

function writeResult(result, exitCode = 0) {
  writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = exitCode;
}

function blockedResult(status, reason, verificationLevel = 'UNVERIFIED', evidenceHash = null) {
  return {
    schema_version: 1,
    mode: 'PLAN_ONLY',
    status,
    verification_level: verificationLevel,
    evidence_hash: evidenceHash,
    evidence_reason: reason,
    plans: [],
    automatic_mutation: false,
    policy: {
      source_edit: 'FORBIDDEN',
      secret_change: 'FORBIDDEN',
      release_or_deploy: 'FORBIDDEN',
      unverified_or_blocked_evidence: 'STOP',
    },
  };
}

if (!existsSync(input)) {
  writeResult(blockedResult('NO_EVIDENCE', 'NO_DIAGNOSIS'), 2);
} else if (!existsSync(evidenceInput) || !existsSync(latestInput)) {
  writeResult(blockedResult('NO_EVIDENCE', 'EVIDENCE_ARTIFACT_REQUIRED'), 2);
} else {
  let diagnosis = null;
  let evidence = null;
  let latest = null;
  try {
    diagnosis = JSON.parse(readFileSync(input, 'utf8'));
    evidence = JSON.parse(readFileSync(evidenceInput, 'utf8'));
    latest = JSON.parse(readFileSync(latestInput, 'utf8'));
  } catch {
    writeResult(blockedResult('INVALID', 'MALFORMED_EVIDENCE_OR_DIAGNOSIS'), 2);
  }

  if (diagnosis && evidence && latest) {
    const computedHash = hashEvidence(latest);
    const evidenceHash = evidence.evidence_hash;
    const verificationLevel = evidence.verification_level;
    const hashMatches = typeof evidenceHash === 'string' && evidenceHash === computedHash;
    const diagnosisHashMatches = diagnosis.evidence_hash === computedHash;
    const allowedLevel = verificationLevel === 'STATIC_VERIFIED' || verificationLevel === 'CI_VERIFIED';

    if (!hashMatches || !diagnosisHashMatches) {
      writeResult(blockedResult('BLOCKED', 'EVIDENCE_HASH_MISMATCH', 'UNVERIFIED', computedHash), 2);
    } else if (!allowedLevel || verificationLevel === 'BLOCKED' || verificationLevel === 'UNVERIFIED') {
      writeResult(blockedResult('BLOCKED', evidence.reason ?? 'INSUFFICIENT_EVIDENCE', verificationLevel ?? 'UNVERIFIED', computedHash), 2);
    } else {
      const entries = Array.isArray(diagnosis.failed_checks) ? diagnosis.failed_checks : [];
      const plans = entries.map((entry) => {
        const rule = catalog[entry.category] ?? catalog.unknown;
        const infrastructure = entry.category === 'runner-infrastructure';
        return {
          check: entry.check,
          category: entry.category,
          catalog_id: infrastructure ? 'R001' : rule.id,
          proposed_action: infrastructure ? 'STOP_AND_RECOLLECT_EXECUTABLE_EVIDENCE' : rule.action,
          risk: infrastructure ? 'HIGH' : rule.risk,
          verification_level: verificationLevel,
          evidence_hash: computedHash,
          evidence_reason: evidence.reason ?? null,
          preconditions: ['fresh diagnostic evidence', 'authorized workflow context', 'deterministic check available'],
          requires_human_approval: infrastructure || rule.risk !== 'LOW',
          mutation_permitted: false,
          rollback: 'No repository mutation is performed by this planner.',
        };
      });

      writeResult({
        schema_version: 1,
        mode: 'PLAN_ONLY',
        generated_at: new Date().toISOString(),
        repository: diagnosis.repository ?? latest.repository ?? 'teetee971/SentinelQuantumVanguardAiPro',
        commit: diagnosis.commit ?? latest.commit ?? 'LOCAL_OR_UNKNOWN',
        source_overall: diagnosis.overall ?? 'UNKNOWN',
        verification_level: verificationLevel,
        evidence_hash: computedHash,
        evidence_reason: evidence.reason ?? null,
        automatic_mutation: false,
        plans,
        policy: {
          source_edit: 'FORBIDDEN',
          secret_change: 'FORBIDDEN',
          release_or_deploy: 'FORBIDDEN',
          unknown_or_infrastructure: 'STOP',
          production_verification_inference: 'FORBIDDEN',
        },
      }, 0);
    }
  }
}
