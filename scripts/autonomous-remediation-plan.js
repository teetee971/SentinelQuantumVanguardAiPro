#!/usr/bin/env node
/**
 * Sentinel bounded remediation planner.
 *
 * PLAN_ONLY by design: it proposes deterministic next actions from a
 * diagnostic report but never edits source, secrets, branches or releases.
 */
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { evaluateEvidence, hashEvidence } from './evidence-trust.js';

const root = resolve(process.cwd());
const dir = resolve(root, 'artifacts', 'autonomous-engineering');
const input = resolve(dir, 'diagnosis.json');
const evidenceInput = resolve(dir, 'evidence.json');
const reportInput = resolve(dir, 'latest.json');
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

function blocked(status, reason) {
  const result = { schema_version: 2, mode: 'PLAN_ONLY', status, reason, plans: [], automatic_mutation: false };
  writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = 2;
}

if (!existsSync(input) || !existsSync(evidenceInput) || !existsSync(reportInput)) {
  const result = { schema_version: 1, mode: 'PLAN_ONLY', status: 'NO_EVIDENCE', plans: [], automatic_mutation: false };
  writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = 2;
} else {
  let diagnosis;
  let evidence;
  let report;
  try {
    diagnosis = JSON.parse(readFileSync(input, 'utf8'));
    evidence = JSON.parse(readFileSync(evidenceInput, 'utf8'));
    report = JSON.parse(readFileSync(reportInput, 'utf8'));
  } catch {
    blocked('INVALID', 'MALFORMED_EVIDENCE_OR_DIAGNOSIS');
    process.exit();
  }
  const unsignedDiagnosis = { ...diagnosis };
  delete unsignedDiagnosis.diagnosis_hash;
  const evaluation = evaluateEvidence(report);
  const trusted = evaluation.valid
    && diagnosis.diagnosis_hash === hashEvidence(unsignedDiagnosis)
    && diagnosis.evidence_hash === hashEvidence(report)
    && evidence.evidence_hash === hashEvidence(report)
    && evidence.verification_level === evaluation.level
    && diagnosis.verification_level === evaluation.level;
  if (!trusted) {
    blocked('BLOCKED', 'ARTIFACT_CHAIN_MISMATCH');
    process.exit();
  }
  const entries = Array.isArray(diagnosis.failed_checks) ? diagnosis.failed_checks : [];
  const plans = entries.map((entry) => {
    const rule = catalog[entry.category] ?? catalog.unknown;
    return {
      check: entry.check,
      category: entry.category,
      catalog_id: rule.id,
      proposed_action: rule.action,
      risk: rule.risk,
      preconditions: ['fresh diagnostic evidence', 'authorized workflow context', 'deterministic check available'],
      requires_human_approval: rule.risk !== 'LOW',
      mutation_permitted: false,
      rollback: 'No repository mutation is performed by this planner.',
    };
  });
  const result = {
    schema_version: 2,
    mode: 'PLAN_ONLY',
    generated_at: new Date().toISOString(),
    repository: diagnosis.repository ?? 'teetee971/SentinelQuantumVanguardAiPro',
    commit: diagnosis.commit ?? 'LOCAL_OR_UNKNOWN',
    source_overall: diagnosis.overall ?? 'UNKNOWN',
    verification_level: evaluation.level,
    evidence_hash: hashEvidence(report),
    automatic_mutation: false,
    plans,
    policy: {
      source_edit: 'FORBIDDEN',
      secret_change: 'FORBIDDEN',
      release_or_deploy: 'FORBIDDEN',
      unknown_or_infrastructure: 'STOP',
      artifact_chain_mismatch: 'STOP',
    },
  };
  writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(result, null, 2));
}
