#!/usr/bin/env node
/**
 * Sentinel Evidence/Trust Layer.
 *
 * Converts observable workflow evidence into a conservative verification level.
 * This module never upgrades evidence beyond what the current execution proves.
 */
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

export const VERIFICATION_LEVELS = Object.freeze([
  'UNVERIFIED',
  'STATIC_VERIFIED',
  'CI_VERIFIED',
  'PRODUCTION_VERIFIED',
  'BLOCKED',
]);

function stableJson(value) {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

export function hashEvidence(value) {
  return createHash('sha256').update(stableJson(value), 'utf8').digest('hex');
}

export function evaluateEvidence(report, env = process.env) {
  if (!report || typeof report !== 'object' || Array.isArray(report)) {
    return { valid: false, level: 'UNVERIFIED', reason: 'INVALID_REPORT' };
  }
  if (typeof report.repository !== 'string' || !report.repository) {
    return { valid: false, level: 'UNVERIFIED', reason: 'REPOSITORY_REQUIRED' };
  }
  if (typeof report.commit !== 'string' || !report.commit || report.commit === 'LOCAL_OR_UNKNOWN') {
    return { valid: false, level: 'UNVERIFIED', reason: 'COMMIT_REQUIRED' };
  }
  if (!Array.isArray(report.checks)) {
    return { valid: false, level: 'UNVERIFIED', reason: 'CHECKS_REQUIRED' };
  }

  const failures = report.checks.filter((check) => check?.status === 'FAIL');
  if (failures.length > 0) {
    return { valid: true, level: 'BLOCKED', reason: 'FAILED_CHECKS_PRESENT' };
  }
  if (!report.checks.length || report.checks.some((check) => check?.status !== 'PASS')) {
    return { valid: true, level: 'STATIC_VERIFIED', reason: 'INCOMPLETE_CHECK_EVIDENCE' };
  }

  const inGitHubActions = env.GITHUB_ACTIONS === 'true';
  const currentSha = env.GITHUB_SHA;
  if (!inGitHubActions || typeof currentSha !== 'string' || currentSha !== report.commit) {
    return { valid: true, level: 'STATIC_VERIFIED', reason: 'CI_PROVENANCE_NOT_BOUND' };
  }

  return { valid: true, level: 'CI_VERIFIED', reason: 'CI_RUN_AND_COMMIT_BOUND' };
}

function runCli() {
  const root = resolve(process.cwd());
  const dir = resolve(root, 'artifacts', 'autonomous-engineering');
  const input = resolve(dir, 'latest.json');
  const output = resolve(dir, 'evidence.json');
  mkdirSync(dir, { recursive: true });

  if (!existsSync(input)) {
    const result = {
      schema_version: 1,
      verification_level: 'UNVERIFIED',
      status: 'NO_EVIDENCE',
      evidence_hash: null,
      reason: 'NO_ENGINEERING_REPORT',
    };
    writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
    console.log(JSON.stringify(result, null, 2));
    process.exitCode = 2;
    return;
  }

  let report;
  try {
    report = JSON.parse(readFileSync(input, 'utf8'));
  } catch {
    const result = {
      schema_version: 1,
      verification_level: 'UNVERIFIED',
      status: 'INVALID',
      evidence_hash: null,
      reason: 'MALFORMED_ENGINEERING_REPORT',
    };
    writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
    console.log(JSON.stringify(result, null, 2));
    process.exitCode = 2;
    return;
  }

  const evaluation = evaluateEvidence(report);
  const result = {
    schema_version: 1,
    generated_at: new Date().toISOString(),
    repository: report.repository ?? null,
    commit: report.commit ?? null,
    workflow_run_id: process.env.GITHUB_RUN_ID ?? null,
    verification_level: evaluation.level,
    status: evaluation.valid ? 'EVALUATED' : 'INVALID',
    reason: evaluation.reason,
    evidence_hash: hashEvidence(report),
  };
  writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = evaluation.valid ? 0 : 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  runCli();
}
