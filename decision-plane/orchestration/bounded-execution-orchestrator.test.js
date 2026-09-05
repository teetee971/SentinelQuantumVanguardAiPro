import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { authorizeBoundedOperation } from './bounded-execution-orchestrator.js';
import { createExecutionRecord, transitionBoundExecution } from '../action-verification/execution-binding.js';
import { createInMemoryReplayGuard } from '../action-verification/anti-replay.js';
import { createSimulationBinding } from '../action-verification/simulation-binding.js';
import { signingPayload } from '../policy/proof-authenticity.js';

const NOW = '2026-09-03T12:00:05.000Z';
const AUTH_KEYS = generateKeyPairSync('ed25519');
const APPROVAL_KEYS = generateKeyPairSync('ed25519');
const SIM_KEYS = generateKeyPairSync('ed25519');

const TRUST = Object.freeze({
  authorizedIssuers: Object.freeze({
    authorization: new Set(['auth-service']),
    approval: new Set(['human-approval-service']),
    simulation: new Set(['simulator-service']),
  }),
  revokedKeyIds: new Set(),
  resolvePublicKey: ({ keyId }) => {
    if (keyId === 'auth-key-1') return AUTH_KEYS.publicKey;
    if (keyId === 'approval-key-1') return APPROVAL_KEYS.publicKey;
    if (keyId === 'sim-key-1') return SIM_KEYS.publicKey;
    return null;
  },
});

function signed(record, proofType, issuerId, keyId, privateKey) {
  const value = { ...record, issuer_id: issuerId, key_id: keyId, signature_alg: 'ed25519' };
  value.signature = sign(null, signingPayload(value, proofType), privateKey).toString('base64');
  return value;
}

function operation(overrides = {}) {
  return {
    action_id: 'a1', authorization_id: 'auth-1', action: 'block', target_id: 'target-1',
    policy_version: 'policy-1', input_hash: 'input-1', ...overrides,
  };
}

function actionPlan(overrides = {}) {
  return {
    action: 'block',
    target_id: 'target-1',
    preconditions: ['authorized'],
    postconditions: ['verified'],
    rollback: { enabled: true, reference: 'rollback://block-target-1' },
    execution_adapter: { approved: true },
    ...overrides,
  };
}

function impactInput(overrides = {}) {
  return {
    nodes: [
      { id: 'target-1', critical: false },
      { id: 'neighbor-1', critical: false },
      { id: 'neighbor-2', critical: false },
      { id: 'neighbor-3', critical: false },
      { id: 'neighbor-4', critical: false },
    ],
    edges: [],
    action: 'block',
    targetIds: ['target-1'],
    ...overrides,
  };
}

function readyRecord(op) {
  let result = createExecutionRecord(op, '2026-09-03T12:00:00.000Z');
  for (const [state, at] of [
    ['VALIDATED', '2026-09-03T12:00:01.000Z'],
    ['AUTHORIZED', '2026-09-03T12:00:02.000Z'],
    ['APPROVED', '2026-09-03T12:00:03.000Z'],
    ['READY', '2026-09-03T12:00:04.000Z'],
  ]) result = transitionBoundExecution(result.record, op, state, at);
  return result.record;
}

function authorization() {
  return signed({
    authorization_id: 'auth-1', actor_id: 'operator-1', issued_at: '2026-09-03T11:00:00.000Z',
    expires_at: '2026-09-03T13:00:00.000Z', action: 'block', target_id: 'target-1',
    scope: { environment: 'security-test' }, policy_version: 'policy-1', source: 'operator',
  }, 'authorization', 'auth-service', 'auth-key-1', AUTH_KEYS.privateKey);
}

function approval() {
  return signed({
    approval_id: 'approval-1', actor_id: 'human-1', approved_at: '2026-09-03T11:30:00.000Z',
    expires_at: '2026-09-03T12:30:00.000Z', action: 'block', target_id: 'target-1',
    scope: { environment: 'security-test' }, policy_version: 'policy-1', source: 'human',
  }, 'approval', 'human-approval-service', 'approval-key-1', APPROVAL_KEYS.privateKey);
}

function simulation() {
  return signed({
    simulation_id: 'sim-1', action_id: 'a1', action: 'block', target_id: 'target-1',
    policy_version: 'policy-1', input_hash: 'input-1', simulation_version: 'sim-v1',
    started_at: '2026-09-03T12:00:00.000Z', completed_at: '2026-09-03T12:00:01.000Z',
    safe: true, source: 'simulator',
  }, 'simulation', 'simulator-service', 'sim-key-1', SIM_KEYS.privateKey);
}

function decision() {
  return {
    decision_type: 'block',
    evidence: [{ kind: 'observation', id: 'obs-1' }],
    assessment: { risk_score: 0.9 },
  };
}

async function authorize(overrides = {}) {
  const op = overrides.operation ?? operation();
  const sim = overrides.simulation ?? simulation();
  const binding = createSimulationBinding(op, sim);
  assert.equal(binding.valid, true);
  return authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true, kill_switch_active: false },
    actionPlan: actionPlan(),
    impactInput: impactInput(),
    executionRecord: readyRecord(op),
    operation: op,
    replayGuard: createInMemoryReplayGuard(),
    simulationBinding: binding.binding,
    simulation: sim,
    now: NOW,
    authorizationRecord: authorization(),
    humanApprovalRecord: approval(),
    proofTrust: TRUST,
    ...overrides,
  });
}

test('connects policy, plan and impact preflight to final bound authorization without performing a side effect', async () => {
  const result = await authorize();
  assert.equal(result.valid, true);
  assert.equal(result.reason, 'BOUNDED_OPERATION_AUTHORIZED');
  assert.equal(result.record.state, 'EXECUTING');
  assert.equal(result.impact.safe, true);
  assert.equal(result.side_effect_performed, false);
});

test('fails closed at policy gate before touching execution authorization', async () => {
  const result = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: false, authorized_target: true },
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'POLICY_HUMAN_APPROVAL_REQUIRED');
});

test('rejects unknown operation actions before final execution authorization', async () => {
  const op = operation({ action: 'invented-privileged-action' });
  const result = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan({ action: 'invented-privileged-action' }),
    impactInput: impactInput({ action: 'invented-privileged-action' }),
    operation: op,
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'UNKNOWN_OPERATION_ACTION');
});

test('requires a structurally valid action plan', async () => {
  const result = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan({ rollback: { enabled: false, reference: 'none' } }),
    impactInput: impactInput(),
    operation: operation(),
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ACTION_PLAN_ROLLBACK_REQUIRED');
});

test('rejects an action-plan action mismatch', async () => {
  const result = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan({ action: 'contain' }),
    impactInput: impactInput(),
    operation: operation(),
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ACTION_PLAN_ACTION_MISMATCH');
});

test('rejects an action-plan target mismatch', async () => {
  const result = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan({ target_id: 'target-2' }),
    impactInput: impactInput(),
    operation: operation(),
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ACTION_PLAN_TARGET_MISMATCH');
});

test('requires an impact preflight bound to the same action and target', async () => {
  const missing = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan(),
    operation: operation(),
  });
  assert.equal(missing.valid, false);
  assert.equal(missing.reason, 'IMPACT_PREFLIGHT_REQUIRED');

  const actionMismatch = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan(),
    impactInput: impactInput({ action: 'contain' }),
    operation: operation(),
  });
  assert.equal(actionMismatch.valid, false);
  assert.equal(actionMismatch.reason, 'IMPACT_PREFLIGHT_ACTION_MISMATCH');

  const targetMismatch = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan(),
    impactInput: impactInput({ targetIds: ['neighbor-1'] }),
    operation: operation(),
  });
  assert.equal(targetMismatch.valid, false);
  assert.equal(targetMismatch.reason, 'IMPACT_PREFLIGHT_TARGET_MISMATCH');
});

test('blocks unsafe graph impact before the signed final execution boundary', async () => {
  const result = await authorizeBoundedOperation({
    decision: decision(),
    policyContext: { human_approval: true, authorized_target: true },
    actionPlan: actionPlan(),
    impactInput: impactInput({
      nodes: [
        { id: 'target-1', critical: false },
        { id: 'critical-1', critical: true },
        { id: 'neighbor-2', critical: false },
        { id: 'neighbor-3', critical: false },
        { id: 'neighbor-4', critical: false },
      ],
      edges: [{ from: 'target-1', to: 'critical-1' }],
    }),
    operation: operation(),
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'IMPACT_PREFLIGHT_CRITICAL_IMPACT');
  assert.equal(result.impact.safe, false);
});
