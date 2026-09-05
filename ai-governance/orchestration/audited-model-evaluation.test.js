import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyAuditChain } from '../audit/audit-event.js';
import { evaluateModelForApprovalWithAudit } from './audited-model-evaluation.js';

const model = Object.freeze({ model_id: 'model-a', version: '1.0.0' });
const evidence = Object.freeze([{ id: 'e1', content: 'trusted observation' }]);

function passingInput(overrides = {}) {
  return {
    output: {
      decision: 'allow',
      evidence_ids: ['e1'],
      action: 'allow',
    },
    evidence,
    untrusted_evidence: [],
    input: { request_id: 'r1' },
    baseline_score: 1,
    suite_version: '1.0.0',
    ...overrides,
  };
}

function auditContext(overrides = {}) {
  return {
    trace_id: 'trace-1',
    decision_id: 'decision-1',
    evidence_refs: ['e1'],
    policy_version: 'policy-1',
    timestamp: '2026-09-05T17:50:00Z',
    ...overrides,
  };
}

test('records an approved model evaluation in the tamper-evident audit chain', () => {
  const result = evaluateModelForApprovalWithAudit({
    model,
    evaluationInput: passingInput(),
    auditChain: [],
    auditContext: auditContext(),
  });

  assert.equal(result.allowed, true);
  assert.equal(result.audit_recorded, true);
  assert.equal(result.production_side_effect_performed, false);
  assert.equal(result.audit_chain.length, 1);
  assert.equal(result.audit_chain[0].model_id, 'model-a');
  assert.equal(result.audit_chain[0].result, 'APPROVED');
  assert.deepEqual(verifyAuditChain(result.audit_chain), { valid: true, reason: 'AUDIT_CHAIN_VALID' });
});

test('records denied evaluation outcomes instead of dropping them from the audit trail', () => {
  const result = evaluateModelForApprovalWithAudit({
    model,
    evaluationInput: passingInput({
      output: { decision: 'allow', evidence_ids: ['unknown'], action: 'allow' },
    }),
    auditChain: [],
    auditContext: auditContext({ evidence_refs: ['unknown'] }),
  });

  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'EVALUATION_FAILED');
  assert.equal(result.audit_recorded, true);
  assert.equal(result.audit_chain[0].result, 'DENIED');
  assert.equal(verifyAuditChain(result.audit_chain).valid, true);
});

test('fails closed when audit context is missing', () => {
  const result = evaluateModelForApprovalWithAudit({
    model,
    evaluationInput: passingInput(),
    auditChain: [],
  });

  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'AUDIT_CONTEXT_REQUIRED');
  assert.equal(result.audit_recorded, false);
});

test('fails closed when the existing audit chain is tampered', () => {
  const first = evaluateModelForApprovalWithAudit({
    model,
    evaluationInput: passingInput(),
    auditChain: [],
    auditContext: auditContext(),
  });
  const tampered = structuredClone(first.audit_chain);
  tampered[0].result = 'DENIED';

  const second = evaluateModelForApprovalWithAudit({
    model,
    evaluationInput: passingInput(),
    auditChain: tampered,
    auditContext: auditContext({ decision_id: 'decision-2', timestamp: '2026-09-05T17:51:00Z' }),
  });

  assert.equal(second.allowed, false);
  assert.equal(second.reason, 'AUDIT_APPEND_FAILED');
  assert.equal(second.audit_recorded, false);
  assert.match(second.audit_error, /AUDIT_HASH_INVALID/);
});

test('caller cannot change the audited model identity through evaluation input', () => {
  const result = evaluateModelForApprovalWithAudit({
    model,
    evaluationInput: passingInput({ model_id: 'other-model', version: '9.9.9' }),
    auditChain: [],
    auditContext: auditContext(),
  });

  assert.equal(result.allowed, true);
  assert.equal(result.audit_chain[0].model_id, 'model-a');
  assert.equal(result.audit_chain[0].model_version, '1.0.0');
});
