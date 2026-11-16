import test from 'node:test';
import assert from 'node:assert/strict';
import { assessSocAutomationRequest } from './soc-automation-policy.js';

const NOW = Date.parse('2026-09-15T10:00:00.000Z');

function approval(actorId, overrides = {}) {
  return {
    approval_id: `approval-${actorId}`,
    actor_id: actorId,
    approved_at: '2026-09-15T09:55:00.000Z',
    expires_at: '2026-09-15T10:05:00.000Z',
    action: 'contain_endpoint',
    target_id: 'endpoint-1',
    scope: { environment: 'soc-test' },
    policy_version: 'soc-policy-1',
    source: 'human',
    ...overrides,
  };
}

function request(overrides = {}) {
  return {
    action: 'contain_endpoint',
    mode: 'EXECUTE',
    policy_version: 'soc-policy-1',
    target_id: 'endpoint-1',
    source_trusted: true,
    authorized_target: true,
    simulation_safe: true,
    rollback_reference: 'rollback://endpoint-1',
    approvals_authenticated: true,
    approvals: [approval('analyst-1'), approval('analyst-2')],
    ...overrides,
  };
}

test('allows bounded passive processing but performs no side effect', () => {
  const result = assessSocAutomationRequest(request({
    action: 'enrich_alert',
    approvals: [],
  }), NOW);
  assert.equal(result.eligible, true);
  assert.equal(result.reason, 'PASSIVE_PROCESSING_ELIGIBLE');
  assert.equal(result.next_boundary, 'DETERMINISTIC_WORKER');
  assert.equal(result.side_effect_performed, false);
});

test('defaults to deny for unknown actions and untrusted sources', () => {
  assert.equal(
    assessSocAutomationRequest(request({ action: 'invented_action' }), NOW).reason,
    'UNKNOWN_ACTION',
  );
  assert.equal(
    assessSocAutomationRequest(request({ action: 'create_case', source_trusted: false }), NOW).reason,
    'TRUSTED_SOURCE_REQUIRED',
  );
});

test('forbids offensive actions even in dry-run mode', () => {
  const result = assessSocAutomationRequest(request({
    action: 'exploit_target',
    mode: 'DRY_RUN',
  }), NOW);
  assert.equal(result.eligible, false);
  assert.equal(result.reason, 'OFFENSIVE_ACTION_FORBIDDEN');
});

test('routes high-impact dry runs to simulation only', () => {
  const result = assessSocAutomationRequest(request({
    mode: 'DRY_RUN',
    approvals: [],
    simulation_safe: false,
  }), NOW);
  assert.equal(result.eligible, true);
  assert.equal(result.reason, 'HIGH_IMPACT_SIMULATION_ONLY');
  assert.equal(result.next_boundary, 'SIMULATION');
  assert.equal(result.side_effect_performed, false);
});

test('requires target authorization, safe simulation, rollback and authenticated approvals', () => {
  assert.equal(
    assessSocAutomationRequest(request({ authorized_target: false }), NOW).reason,
    'AUTHORIZED_TARGET_REQUIRED',
  );
  assert.equal(
    assessSocAutomationRequest(request({ simulation_safe: false }), NOW).reason,
    'SAFE_SIMULATION_REQUIRED',
  );
  assert.equal(
    assessSocAutomationRequest(request({ rollback_reference: '' }), NOW).reason,
    'ROLLBACK_REFERENCE_REQUIRED',
  );
  assert.equal(
    assessSocAutomationRequest(request({ approvals_authenticated: false }), NOW).reason,
    'AUTHENTICATED_APPROVALS_REQUIRED',
  );
});

test('requires two distinct, valid human approvals bound to the operation', () => {
  assert.equal(
    assessSocAutomationRequest(request({ approvals: [approval('analyst-1')] }), NOW).reason,
    'TWO_HUMAN_APPROVALS_REQUIRED',
  );
  assert.equal(
    assessSocAutomationRequest(request({
      approvals: [approval('analyst-1'), approval('analyst-1', { approval_id: 'approval-2' })],
    }), NOW).reason,
    'DISTINCT_APPROVERS_REQUIRED',
  );
  assert.match(
    assessSocAutomationRequest(request({
      approvals: [approval('analyst-1'), approval('analyst-2', { source: 'ai' })],
    }), NOW).reason,
    /^INVALID_HUMAN_APPROVAL:APPROVAL_SOURCE_NOT_HUMAN$/,
  );
});

test('only hands a qualified request to the existing bound authorization boundary', () => {
  const result = assessSocAutomationRequest(request(), NOW);
  assert.equal(result.eligible, true);
  assert.equal(result.reason, 'BOUND_AUTHORIZATION_REQUIRED');
  assert.equal(result.next_boundary, 'AUTHORIZE_BOUNDED_OPERATION');
  assert.equal(result.required_approvers, 2);
  assert.equal(result.side_effect_performed, false);
});
