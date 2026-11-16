import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import {
  authorizeBoundExecutionStart,
  createExecutionRecord,
  transitionBoundExecution,
  verifyExecutionBinding,
} from './execution-binding.js';
import { createInMemoryReplayGuard } from './anti-replay.js';
import { createSimulationBinding } from './simulation-binding.js';
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
  const value = {
    ...record,
    issuer_id: issuerId,
    key_id: keyId,
    signature_alg: 'ed25519',
  };
  value.signature = sign(null, signingPayload(value, proofType), privateKey).toString('base64');
  return value;
}

function operation(overrides = {}) {
  return {
    action_id: 'a1',
    authorization_id: 'auth-1',
    action: 'block',
    target_id: 'target-1',
    policy_version: 'policy-1',
    input_hash: 'input-1',
    ...overrides,
  };
}

function authorization(overrides = {}) {
  return signed({
    authorization_id: 'auth-1',
    actor_id: 'operator-1',
    issued_at: '2026-09-03T11:00:00.000Z',
    expires_at: '2026-09-03T13:00:00.000Z',
    action: 'block',
    target_id: 'target-1',
    scope: { environment: 'security-test' },
    policy_version: 'policy-1',
    source: 'operator',
    ...overrides,
  }, 'authorization', 'auth-service', 'auth-key-1', AUTH_KEYS.privateKey);
}

function humanApproval(overrides = {}) {
  return signed({
    approval_id: 'approval-1',
    actor_id: 'human-1',
    approved_at: '2026-09-03T11:30:00.000Z',
    expires_at: '2026-09-03T12:30:00.000Z',
    action: 'block',
    target_id: 'target-1',
    scope: { environment: 'security-test' },
    policy_version: 'policy-1',
    source: 'human',
    ...overrides,
  }, 'approval', 'human-approval-service', 'approval-key-1', APPROVAL_KEYS.privateKey);
}

function simulation(overrides = {}) {
  return signed({
    simulation_id: 'sim-1',
    action_id: 'a1',
    action: 'block',
    target_id: 'target-1',
    policy_version: 'policy-1',
    input_hash: 'input-1',
    simulation_version: 'sim-v1',
    started_at: '2026-09-03T12:00:00.000Z',
    completed_at: '2026-09-03T12:00:01.000Z',
    safe: true,
    source: 'simulator',
    ...overrides,
  }, 'simulation', 'simulator-service', 'sim-key-1', SIM_KEYS.privateKey);
}

function boundSimulation(op = operation(), sim = simulation()) {
  const result = createSimulationBinding(op, sim);
  assert.equal(result.valid, true);
  return result.binding;
}

function readyRecord() {
  const op = operation();
  const created = createExecutionRecord(op, '2026-09-03T12:00:00.000Z');
  const validated = transitionBoundExecution(created.record, op, 'VALIDATED', '2026-09-03T12:00:01.000Z');
  const authorized = transitionBoundExecution(validated.record, op, 'AUTHORIZED', '2026-09-03T12:00:02.000Z');
  const approved = transitionBoundExecution(authorized.record, op, 'APPROVED', '2026-09-03T12:00:03.000Z');
  return transitionBoundExecution(approved.record, op, 'READY', '2026-09-03T12:00:04.000Z');
}

async function authorize({
  op = operation(),
  ready = readyRecord(),
  guard = createInMemoryReplayGuard(),
  sim = simulation(),
  now = NOW,
  auth = authorization(),
  approval = humanApproval(),
  trust = TRUST,
  binding = null,
} = {}) {
  const simulationProof = binding ?? boundSimulation(op, sim);
  return authorizeBoundExecutionStart(
    ready.record, op, guard, simulationProof, sim, now, auth, approval, trust,
  );
}

test('creates a PROPOSED execution record bound to the operation digest', () => {
  const result = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'PROPOSED');
  assert.match(result.record.operation_digest, /^[a-f0-9]{64}$/);
  assert.equal(result.record.authorization_id, 'auth-1');
});

test('rejects an operation mutation before transition', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const mutated = verifyExecutionBinding(created.record, operation({ target_id: 'target-critical' }));
  assert.equal(mutated.valid, false);
  assert.equal(mutated.reason, 'OPERATION_DIGEST_MISMATCH');
});

test('rejects action-id substitution even if a new digest is supplied', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const result = verifyExecutionBinding(created.record, operation({ action_id: 'attacker-action' }));
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ACTION_ID_MISMATCH');
});

test('rejects authorization-id substitution even if the operation remains otherwise valid', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const result = verifyExecutionBinding(created.record, operation({ authorization_id: 'attacker-auth' }));
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'AUTHORIZATION_ID_MISMATCH');
});

test('permits a bound forward transition', () => {
  const created = createExecutionRecord(operation(), '2026-09-03T12:00:00.000Z');
  const result = transitionBoundExecution(created.record, operation(), 'VALIDATED', '2026-09-03T12:00:01.000Z');
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'VALIDATED');
  assert.equal(result.record.operation_digest, created.record.operation_digest);
});

test('blocks READY to EXECUTING through the generic transition path', () => {
  const result = transitionBoundExecution(readyRecord().record, operation(), 'EXECUTING', NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'EXECUTION_START_REQUIRES_FINAL_BOUNDARY');
});

test('blocks READY to EXECUTING when the operation changes', () => {
  const result = transitionBoundExecution(readyRecord().record, operation({ policy_version: 'policy-2' }), 'EXECUTING', NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OPERATION_DIGEST_MISMATCH');
});

test('requires live signed authorization, human approval and simulation at the final boundary', async () => {
  const result = await authorize();
  assert.equal(result.valid, true);
  assert.equal(result.record.state, 'EXECUTING');
});

test('fails closed when proof trust is not supplied', async () => {
  const result = await authorize({ trust: null });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_TRUST_REQUIRED');
});

test('fails closed when a signed authorization is mutated after signing', async () => {
  const auth = authorization();
  auth.scope = { environment: 'attacker' };
  const result = await authorize({ auth });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_SIGNATURE_INVALID');
});

test('fails closed when the authorization signing key is revoked', async () => {
  const result = await authorize({
    trust: { ...TRUST, revokedKeyIds: new Set(['auth-key-1']) },
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_KEY_REVOKED');
});

test('fails closed when final authorization is expired', async () => {
  const result = await authorize({ auth: authorization({ expires_at: '2026-09-03T12:00:04.000Z' }) });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'AUTHORIZATION_EXPIRED_OR_NOT_YET_VALID');
});

test('fails closed when final human approval is expired', async () => {
  const result = await authorize({ approval: humanApproval({ expires_at: '2026-09-03T12:00:04.000Z' }) });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'APPROVAL_EXPIRED_OR_NOT_YET_VALID');
});

test('fails closed when final authorization is bound to another target', async () => {
  const result = await authorize({ auth: authorization({ target_id: 'target-attacker' }) });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'AUTHORIZATION_TARGET_MISMATCH');
});

test('fails closed when the final human approval is not human-issued', async () => {
  const result = await authorize({ approval: humanApproval({ source: 'automation' }) });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'APPROVAL_SOURCE_NOT_HUMAN');
});

test('awaits an asynchronous replay guard before entering EXECUTING', async () => {
  let consumed = false;
  const guard = {
    consumeAtomically: async () => {
      await new Promise((resolve) => setImmediate(resolve));
      if (consumed) return { valid: false, reason: 'REPLAY_DETECTED' };
      consumed = true;
      return { valid: true, reason: 'REPLAY_KEY_CONSUMED' };
    },
  };
  const first = await authorize({ guard });
  const second = await authorize({ guard, now: '2026-09-03T12:00:06.000Z' });
  assert.equal(first.valid, true);
  assert.equal(first.record.state, 'EXECUTING');
  assert.equal(second.valid, false);
  assert.equal(second.reason, 'REPLAY_DETECTED');
});

test('fails closed when the simulation binding is missing at the final boundary', async () => {
  const result = await authorizeBoundExecutionStart(
    readyRecord().record,
    operation(),
    createInMemoryReplayGuard(),
    null,
    simulation(),
    NOW,
    authorization(),
    humanApproval(),
    TRUST,
  );
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'INVALID_SIMULATION_BINDING');
});

test('fails closed when the simulation is bound to a mutated operation', async () => {
  const op = operation();
  const sim = simulation();
  const binding = boundSimulation(op, sim);
  const result = await authorizeBoundExecutionStart(
    readyRecord().record,
    operation({ target_id: 'target-attacker' }),
    createInMemoryReplayGuard(),
    binding,
    sim,
    NOW,
    authorization(),
    humanApproval(),
    TRUST,
  );
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OPERATION_DIGEST_MISMATCH');
});

test('fails closed on replay at the final execution boundary', async () => {
  const guard = createInMemoryReplayGuard();
  assert.equal((await authorize({ guard })).valid, true);
  const second = await authorize({ guard, now: '2026-09-03T12:00:06.000Z' });
  assert.equal(second.valid, false);
  assert.equal(second.reason, 'REPLAY_DETECTED');
});

test('fails closed when no replay guard is supplied', async () => {
  const result = await authorize({ guard: null });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'ANTI_REPLAY_GUARD_REQUIRED');
});

test('fails closed when final proof records are missing', async () => {
  const op = operation();
  const sim = simulation();
  const result = await authorizeBoundExecutionStart(
    readyRecord().record, op, createInMemoryReplayGuard(), boundSimulation(op, sim), sim, NOW,
    null, null, TRUST,
  );
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'INVALID_AUTHORIZATION_RECORD');
});
