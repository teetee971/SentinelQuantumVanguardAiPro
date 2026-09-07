import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createPostgresModerationStore } from './postgres-moderation-store.js';

const schema = readFileSync(new URL('./postgres-moderation-schema.sql', import.meta.url), 'utf8');

test('schema encodes concurrency, indexing, evidence and append-only controls', () => {
  assert.match(schema, /pg_advisory_xact_lock/);
  assert.match(schema, /sentinel_phone_valid_evidence_refs/);
  assert.match(schema, /appeals_report_idx/);
  assert.match(schema, /appeals_one_open_idx/);
  assert.match(schema, /BEFORE UPDATE OR DELETE/);
  assert.match(schema, /BEFORE TRUNCATE/);
  assert.match(schema, /REVOKE ALL ON sentinel_phone_moderation_audit FROM PUBLIC/);
});

test('fails closed without a durable executor', async () => {
  const store = createPostgresModerationStore();
  assert.equal((await store.createReport({})).reason, 'DURABLE_MODERATION_EXECUTOR_REQUIRED');
});

test('report persistence and audit append are one SQL statement', async () => {
  let query;
  const store = createPostgresModerationStore({ execute: async (value) => {
    query = value;
    return { rows: [{ report_id: 'report-1', audit_hash: 'a'.repeat(64) }] };
  } });
  const result = await store.createReport({
    id: 'report-1', actor_id: 'actor-1', number: '+33612345678', reason: 'phishing', note: '',
    evidence_refs: [], created_at_ms: 1_000, expires_at_ms: 61_000,
  });
  assert.equal(result.persisted, true);
  assert.match(query.text, /WITH inserted AS/);
  assert.match(query.text, /sentinel_phone_append_audit/);
  assert.equal(query.values[0], 'report-1');
});

test('moderation update preserves terminal and automation boundaries', async () => {
  let query;
  const store = createPostgresModerationStore({ execute: async (value) => {
    query = value;
    return { rows: [{ report_id: 'report-1' }] };
  } });
  await store.moderateReport({ id: 'report-1', status: 'escalated', moderation_reason: 'HUMAN_REVIEW_REQUIRED',
    moderator_id: 'automation-1', decision_source: 'automated', updated_at_ms: 2_000 });
  assert.match(query.text, /status IN \('pending', 'escalated'\)/);
  assert.match(query.text, /\$5 <> 'automated' OR \$2 = 'escalated'/);
  assert.match(query.text, /updated_at <= \$6/);
});

test('appeal decision is human-only and computes SLA atomically', async () => {
  let query;
  const store = createPostgresModerationStore({ execute: async (value) => {
    query = value;
    return { rows: [{ appeal_id: 'appeal-1' }] };
  } });
  await store.decideAppeal({ id: 'appeal-1', status: 'accepted', reviewer_id: 'reviewer-1',
    decision_reason: 'EVIDENCE_CONFIRMED', decision_note: 'reviewed', updated_at_ms: 3_000 });
  assert.match(query.text, /decision_source = 'human'/);
  assert.match(query.text, /CASE WHEN \$6 <= due_at THEN 'met' ELSE 'breached' END/);
  assert.match(query.text, /status IN \('open', 'in_review'\)/);
});

test('database conflicts and outages fail closed', async () => {
  const conflict = createPostgresModerationStore({ execute: async () => ({ rows: [] }) });
  assert.equal((await conflict.createAppeal({
    id: 'appeal-1', report_id: 'report-1', actor_id: 'actor-1', message: 'review',
    created_at_ms: 1_000, due_at_ms: 61_000,
  })).reason, 'DURABLE_MODERATION_CONFLICT');
  const outage = createPostgresModerationStore({ execute: async () => { throw new Error('offline'); } });
  assert.equal((await outage.moderateReport({
    id: 'report-1', status: 'accepted', moderation_reason: 'EVIDENCE_REVIEWED',
    moderator_id: 'moderator-1', decision_source: 'human', updated_at_ms: 2_000,
  })).reason, 'DURABLE_MODERATION_UNAVAILABLE');
});

test('invalid records are rejected before database access', async () => {
  let calls = 0;
  const store = createPostgresModerationStore({ execute: async () => { calls += 1; return { rows: [] }; } });
  assert.equal((await store.createReport({ id: 'report-1' })).reason, 'DURABLE_REPORT_INPUT_INVALID');
  assert.equal((await store.moderateReport({ id: 'report-1', decision_source: 'automated', status: 'accepted' })).reason,
    'DURABLE_MODERATION_INPUT_INVALID');
  assert.equal((await store.decideAppeal({ id: 'appeal-1', updated_at_ms: Number.NaN })).reason,
    'DURABLE_APPEAL_DECISION_INPUT_INVALID');
  assert.equal((await store.pruneExpired(-1)).reason, 'DURABLE_PRUNE_TIME_INVALID');
  assert.equal(calls, 0);
});
