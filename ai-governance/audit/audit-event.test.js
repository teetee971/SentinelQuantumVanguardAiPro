import assert from 'node:assert/strict';
import test from 'node:test';
import { appendAuditEvent, hashAuditEvent, verifyAuditChain } from './audit-event.js';

const base = {
  trace_id: 'trace-1',
  decision_id: 'decision-1',
  evidence_refs: ['evidence-1'],
  model_id: 'sentinel-model',
  model_version: '1.0.0',
  policy_version: '1.0.0',
  action: 'observe',
  result: 'allow',
  timestamp: '2026-09-02T00:00:00.000Z',
};

test('builds a cryptographically linked immutable chain', () => {
  const chain = appendAuditEvent([], base);
  const chained = appendAuditEvent(chain, { ...base, decision_id: 'decision-2' });

  assert.equal(verifyAuditChain(chained).valid, true);
  assert.equal(chained[1].previous_hash, chained[0].hash);
  assert.equal(chained[0].hash.length, 64);
  assert.equal(Object.isFrozen(chained), true);
  assert.equal(Object.isFrozen(chained[0]), true);
  assert.equal(Object.isFrozen(chained[0].evidence_refs), true);
  assert.throws(() => chained.push({}), TypeError);
});

test('detects content tampering', () => {
  const chain = appendAuditEvent([], base);
  const tampered = structuredClone(chain);
  tampered[0].result = 'deny';
  assert.equal(verifyAuditChain(tampered).reason, 'AUDIT_HASH_INVALID');
});

test('detects a broken parent link', () => {
  const chain = appendAuditEvent([], base);
  const chained = appendAuditEvent(chain, { ...base, decision_id: 'decision-2' });
  const broken = structuredClone(chained);
  broken[1].previous_hash = '0'.repeat(64);
  assert.equal(verifyAuditChain(broken).reason, 'AUDIT_CHAIN_LINK_INVALID');
});

test('rejects structurally invalid events even when the attacker recomputes the hash', () => {
  const chain = appendAuditEvent([], base);
  const forged = structuredClone(chain);
  delete forged[0].action;
  const { hash, ...event } = forged[0];
  forged[0].hash = hashAuditEvent(event, '');
  assert.equal(verifyAuditChain(forged).reason, 'AUDIT_EVENT_MISSING_ACTION');
});

test('rejects unknown fields to match the strict event schema', () => {
  assert.throws(
    () => appendAuditEvent([], { ...base, attacker_field: 'unexpected' }),
    /AUDIT_UNKNOWN_FIELD/,
  );
});

test('rejects malformed hashes and oversized evidence references', () => {
  const chain = appendAuditEvent([], base);
  const badHash = structuredClone(chain);
  badHash[0].hash = 'not-a-hash';
  assert.equal(verifyAuditChain(badHash).reason, 'AUDIT_HASH_FORMAT_INVALID');

  assert.throws(
    () => appendAuditEvent([], { ...base, evidence_refs: Array.from({ length: 1001 }, () => 'e') }),
    /AUDIT_EVIDENCE_REFS_INVALID/,
  );
});

test('deep-clones caller data so post-append mutation cannot alter stored evidence', () => {
  const evidenceRefs = ['evidence-original'];
  const event = { ...base, evidence_refs: evidenceRefs };
  const chain = appendAuditEvent([], event);

  evidenceRefs[0] = 'evidence-mutated';
  assert.deepEqual(chain[0].evidence_refs, ['evidence-original']);
  assert.equal(verifyAuditChain(chain).valid, true);
});

test('rejects insertion or deletion without a valid recalculated chain', () => {
  const first = appendAuditEvent([], base);
  const second = appendAuditEvent(first, { ...base, decision_id: 'decision-2' });

  const inserted = structuredClone(second);
  inserted.splice(1, 0, structuredClone(inserted[0]));
  assert.equal(verifyAuditChain(inserted).valid, false);

  const deleted = structuredClone(second);
  deleted.splice(0, 1);
  assert.equal(verifyAuditChain(deleted).reason, 'AUDIT_CHAIN_LINK_INVALID');
});

test('append rejects an already-corrupted input chain', () => {
  const chain = appendAuditEvent([], base);
  const corrupted = structuredClone(chain);
  corrupted[0].result = 'tampered';
  assert.throws(
    () => appendAuditEvent(corrupted, { ...base, decision_id: 'decision-2' }),
    /AUDIT_HASH_INVALID/,
  );
});

test('hashing is deterministic for equivalent key ordering', () => {
  const a = { action: 'observe', result: 'allow', trace_id: 't' };
  const b = { trace_id: 't', result: 'allow', action: 'observe' };
  assert.equal(hashAuditEvent(a), hashAuditEvent(b));
});

test('rejects invalid event inputs before they enter the chain', () => {
  assert.throws(() => appendAuditEvent([], { ...base, evidence_refs: 'invalid' }), /AUDIT_EVIDENCE_REFS_INVALID/);
  assert.throws(() => appendAuditEvent([], { ...base, action: undefined }), /AUDIT_EVENT_FIELD_INVALID/);
  assert.throws(() => appendAuditEvent([], { ...base, timestamp: 'not-a-date' }), /AUDIT_TIMESTAMP_INVALID/);
  assert.equal(verifyAuditChain(null).reason, 'AUDIT_CHAIN_REQUIRED');
});
