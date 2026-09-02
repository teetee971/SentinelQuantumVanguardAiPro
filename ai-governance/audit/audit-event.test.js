import assert from 'node:assert/strict';
import { appendAuditEvent, verifyAuditChain } from './audit-event.js';

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

const chain = appendAuditEvent([], base);
const chained = appendAuditEvent(chain, { ...base, decision_id: 'decision-2' });

assert.equal(verifyAuditChain(chained).valid, true);
assert.equal(chained[1].previous_hash, chained[0].hash);

const tampered = structuredClone(chained);
tampered[0].result = 'deny';
assert.equal(verifyAuditChain(tampered).reason, 'AUDIT_HASH_INVALID');

const broken = structuredClone(chained);
broken[1].previous_hash = '0'.repeat(64);
assert.equal(verifyAuditChain(broken).reason, 'AUDIT_CHAIN_LINK_INVALID');

assert.throws(() => appendAuditEvent([], { ...base, evidence_refs: 'invalid' }), /AUDIT_EVIDENCE_REFS_REQUIRED/);
assert.throws(() => appendAuditEvent([], { ...base, action: undefined }), /AUDIT_EVENT_MISSING_ACTION/);

console.log('audit-event tests: PASS');
