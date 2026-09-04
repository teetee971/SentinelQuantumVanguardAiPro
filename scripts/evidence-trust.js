#!/usr/bin/env node
/** Sentinel Evidence/Trust Layer. CI trust requires exact execution binding. */
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

export const VERIFICATION_LEVELS = Object.freeze(['UNVERIFIED','STATIC_VERIFIED','CI_VERIFIED','PRODUCTION_VERIFIED','BLOCKED']);
const PROVENANCE_FIELDS = Object.freeze(['repository','commit','workflow','workflow_ref','run_id','run_attempt','ref','event']);
function stableJson(value) {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
  return JSON.stringify(value);
}
export function hashEvidence(value) { return createHash('sha256').update(stableJson(value), 'utf8').digest('hex'); }
export function getRuntimeProvenance(env = process.env) {
  return { repository: env.GITHUB_REPOSITORY ?? null, commit: env.GITHUB_SHA ?? null, workflow: env.GITHUB_WORKFLOW ?? null, workflow_ref: env.GITHUB_WORKFLOW_REF ?? null, run_id: env.GITHUB_RUN_ID ?? null, run_attempt: env.GITHUB_RUN_ATTEMPT ?? null, ref: env.GITHUB_REF ?? null, event: env.GITHUB_EVENT_NAME ?? null };
}
function reportProvenance(report) { return report?.provenance && typeof report.provenance === 'object' && !Array.isArray(report.provenance) ? report.provenance : null; }
export function validateProvenance(report, env = process.env) {
  const provenance = reportProvenance(report); if (!provenance) return 'CI_RUN_BINDING_INCOMPLETE';
  const runtime = getRuntimeProvenance(env);
  for (const field of PROVENANCE_FIELDS) {
    if (typeof provenance[field] !== 'string' || !provenance[field]) return `CI_PROVENANCE_FIELD_MISSING:${field}`;
    if (provenance[field] !== runtime[field]) return `CI_PROVENANCE_MISMATCH:${field}`;
  }
  return null;
}
export function evaluateEvidence(report, env = process.env) {
  if (!report || typeof report !== 'object' || Array.isArray(report)) return { valid:false, level:'UNVERIFIED', reason:'INVALID_REPORT', outcome:'UNKNOWN' };
  if (typeof report.repository !== 'string' || !report.repository) return { valid:false, level:'UNVERIFIED', reason:'REPOSITORY_REQUIRED', outcome:'UNKNOWN' };
  if (typeof report.commit !== 'string' || !report.commit || report.commit === 'LOCAL_OR_UNKNOWN') return { valid:false, level:'UNVERIFIED', reason:'COMMIT_REQUIRED', outcome:'UNKNOWN' };
  if (!Array.isArray(report.checks)) return { valid:false, level:'UNVERIFIED', reason:'CHECKS_REQUIRED', outcome:'UNKNOWN' };
  const failures = report.checks.filter((check) => check?.status === 'FAIL');
  const incomplete = !report.checks.length || report.checks.some((check) => check?.status !== 'PASS' && check?.status !== 'FAIL');
  const outcome = failures.length ? 'FAILED' : incomplete ? 'INCOMPLETE' : 'PASSED';
  if (incomplete) return { valid:true, level:'STATIC_VERIFIED', reason:'INCOMPLETE_CHECK_EVIDENCE', outcome };
  if (env.GITHUB_ACTIONS !== 'true') return { valid:true, level:'STATIC_VERIFIED', reason:'CI_PROVENANCE_NOT_BOUND', outcome };
  const mismatch = validateProvenance(report, env);
  if (mismatch) return { valid:true, level:'STATIC_VERIFIED', reason:mismatch, outcome };
  return { valid:true, level:'CI_VERIFIED', reason:'CI_EXECUTION_BINDING_EXACT', outcome };
}
function runCli() {
  const dir = resolve(process.cwd(), 'artifacts', 'autonomous-engineering'); const input = resolve(dir, 'latest.json'); const output = resolve(dir, 'evidence.json'); mkdirSync(dir, {recursive:true});
  if (!existsSync(input)) { const r={schema_version:2,verification_level:'UNVERIFIED',status:'NO_EVIDENCE',evidence_hash:null,reason:'NO_ENGINEERING_REPORT'}; writeFileSync(output,`${JSON.stringify(r,null,2)}\n`); process.exitCode=2; return; }
  let report; try { report=JSON.parse(readFileSync(input,'utf8')); } catch { const r={schema_version:2,verification_level:'UNVERIFIED',status:'INVALID',evidence_hash:null,reason:'MALFORMED_ENGINEERING_REPORT'}; writeFileSync(output,`${JSON.stringify(r,null,2)}\n`); process.exitCode=2; return; }
  const evaluation=evaluateEvidence(report); const p=reportProvenance(report); const r={schema_version:2,generated_at:new Date().toISOString(),repository:report.repository??null,commit:report.commit??null,workflow:p?.workflow??null,workflow_ref:p?.workflow_ref??null,workflow_run_id:p?.run_id??null,run_attempt:p?.run_attempt??null,ref:p?.ref??null,event:p?.event??null,verification_level:evaluation.level,status:evaluation.valid?'EVALUATED':'INVALID',outcome:evaluation.outcome,reason:evaluation.reason,evidence_hash:hashEvidence(report)};
  writeFileSync(output,`${JSON.stringify(r,null,2)}\n`); console.log(JSON.stringify(r,null,2)); process.exitCode=evaluation.valid?0:2;
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) runCli();
