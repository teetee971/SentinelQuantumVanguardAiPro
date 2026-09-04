#!/usr/bin/env node
/**
 * Sentinel bounded remediation planner.
 *
 * PLAN_ONLY by design: it proposes deterministic next actions from a
 * diagnostic report but never edits source, secrets, branches or releases.
 */
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd());
const dir = resolve(root, 'artifacts', 'autonomous-engineering');
const input = resolve(dir, 'diagnosis.json');
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

if (!existsSync(input)) {
  const result = { schema_version: 1, mode: 'PLAN_ONLY', status: 'NO_EVIDENCE', plans: [], automatic_mutation: false };
  writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = 2;
} else {
  const diagnosis = JSON.parse(readFileSync(input, 'utf8'));
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
    schema_version: 1,
    mode: 'PLAN_ONLY',
    generated_at: new Date().toISOString(),
    repository: diagnosis.repository ?? 'teetee971/SentinelQuantumVanguardAiPro',
    commit: diagnosis.commit ?? 'LOCAL_OR_UNKNOWN',
    source_overall: diagnosis.overall ?? 'UNKNOWN',
    automatic_mutation: false,
    plans,
    policy: {
      source_edit: 'FORBIDDEN',
      secret_change: 'FORBIDDEN',
      release_or_deploy: 'FORBIDDEN',
      unknown_or_infrastructure: 'STOP',
    },
  };
  writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(result, null, 2));
}
