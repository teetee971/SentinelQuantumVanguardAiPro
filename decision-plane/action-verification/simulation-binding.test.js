import test from 'node:test';
import assert from 'node:assert/strict';
import { createSimulationBinding, verifySimulationBinding } from './simulation-binding.js';

const NOW = Date.parse('2026-09-03T12:00:05.000Z');

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

function simulation(overrides = {}) {
  return {
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
  };
}

test('binds a safe simulation to the exact operation digest', () => {
  const result = createSimulationBinding(operation(), simulation(), NOW);
  assert.equal(result.valid, true);
  assert.match(result.binding.operation_digest, /^[a-f0-9]{64}$/);
  assert.equal(result.binding.input_hash, 'input-1');
});

test('rejects mismatched simulation action at binding creation', () => {
  const result = createSimulationBinding(operation(), simulation({ action: 'delete' }), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_BINDING_MISMATCH:action');
});

test('rejects mismatched simulation target at binding creation', () => {
  const result = createSimulationBinding(operation(), simulation({ target_id: 'target-2' }), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_BINDING_MISMATCH:target_id');
});

test('rejects mismatched simulation policy at binding creation', () => {
  const result = createSimulationBinding(operation(), simulation({ policy_version: 'policy-2' }), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_BINDING_MISMATCH:policy_version');
});

test('rejects operation input mutation after simulation', () => {
  const binding = createSimulationBinding(operation(), simulation(), NOW).binding;
  const result = verifySimulationBinding(binding, operation({ input_hash: 'attacker-input' }), simulation(), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_INPUT_HASH_MISMATCH');
});

test('rejects mismatched simulation input hash at binding creation', () => {
  const result = createSimulationBinding(operation(), simulation({ input_hash: 'different-input' }), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_INPUT_HASH_MISMATCH');
});

test('rejects target mutation after simulation', () => {
  const binding = createSimulationBinding(operation(), simulation(), NOW).binding;
  const result = verifySimulationBinding(binding, operation({ target_id: 'target-2' }), simulation(), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_BINDING_MISMATCH:target_id');
});

test('rejects policy mutation after simulation', () => {
  const binding = createSimulationBinding(operation(), simulation(), NOW).binding;
  const result = verifySimulationBinding(binding, operation({ policy_version: 'policy-2' }), simulation(), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_BINDING_MISMATCH:policy_version');
});

test('rejects simulation-id substitution', () => {
  const binding = createSimulationBinding(operation(), simulation(), NOW).binding;
  const result = verifySimulationBinding(binding, operation(), simulation({ simulation_id: 'sim-attacker' }), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_ID_MISMATCH');
});

test('rejects an unsafe simulation', () => {
  const result = createSimulationBinding(operation(), simulation({ safe: false }), NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_NOT_SAFE');
});

test('rejects a simulation record that is structurally incomplete at final verification', () => {
  const binding = createSimulationBinding(operation(), simulation(), NOW).binding;
  const result = verifySimulationBinding(binding, operation(), { ...simulation(), source: undefined }, NOW);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'SIMULATION_FIELD_REQUIRED:source');
});
