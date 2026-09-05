import assert from 'node:assert/strict';
import test from 'node:test';
import { validateAuthorizationRecord } from './authorization-record.js';
import { validateHumanApprovalRecord } from './human-approval-record.js';

const now = Date.parse('2026-09-03T12:00:00.000Z');

const authorization = {
  authorization_id: 'auth-001',
  actor_id: 'operator-001',
  issued_at: '2026-09-03T11:00:00.000Z',
  expires_at: '2026-09-03T13:00:00.000Z',
  action: 'block',
  target_id: 'target-001',
  scope: { environment: 'security-test' },
  policy_version: 'policy-1',
  source: 'operator',
};

const approval = {
  approval_id: 'approval-001',
  actor_id: 'human-001',
  approved_at: '2026-09-03T11:30:00.000Z',
  expires_at: '2026-09-03T12:30:00.000Z',
  action: 'block',
  target_id: 'target-001',
  scope: { environment: 'security-test' },
  policy_version: 'policy-1',
  source: 'human',
};

test('accepts a valid scoped authorization record', () => {
  assert.equal(validateAuthorizationRecord(authorization, now).valid, true);
});

test('rejects expired authorization', () => {
  assert.equal(validateAuthorizationRecord({ ...authorization, expires_at: '2026-09-03T11:59:00.000Z' }, now).reason, 'AUTHORIZATION_EXPIRED_OR_NOT_YET_VALID');
});

test('rejects AI-generated authorization', () => {
  assert.equal(validateAuthorizationRecord({ ...authorization, source: 'ai' }, now).reason, 'AUTHORIZATION_SOURCE_UNTRUSTED');
});

test('rejects authorization without structured scope', () => {
  assert.equal(validateAuthorizationRecord({ ...authorization, scope: true }, now).reason, 'AUTHORIZATION_SCOPE_INVALID');
});

test('accepts a valid human approval bound to action, target and policy', () => {
  assert.equal(validateHumanApprovalRecord(approval, {
    action: 'block',
    target_id: 'target-001',
    policy_version: 'policy-1',
  }, now).valid, true);
});

test('rejects approval from AI or automation', () => {
  assert.equal(validateHumanApprovalRecord({ ...approval, source: 'ai' }, {}, now).reason, 'APPROVAL_SOURCE_NOT_HUMAN');
});

test('rejects expired human approval', () => {
  assert.equal(validateHumanApprovalRecord({ ...approval, expires_at: '2026-09-03T11:59:00.000Z' }, {}, now).reason, 'APPROVAL_EXPIRED_OR_NOT_YET_VALID');
});

test('rejects approval bound to the wrong target', () => {
  assert.equal(validateHumanApprovalRecord(approval, { target_id: 'target-999' }, now).reason, 'APPROVAL_BINDING_MISMATCH:target_id');
});
