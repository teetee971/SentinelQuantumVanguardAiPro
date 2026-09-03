import { computeOperationDigest } from './operation-digest.js';

const MAX_ID_LENGTH = 256;
const MAX_DIGEST_LENGTH = 64;

function validString(value, maxLength = MAX_ID_LENGTH) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;
}

function validDigest(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
}

/**
 * Binds a simulation certificate to the exact operation digest.
 * The simulation input hash must already equal the operation input hash.
 */
export function createSimulationBinding(operation, simulation) {
  if (!operation || typeof operation !== 'object' || Array.isArray(operation)) {
    return { valid: false, reason: 'INVALID_OPERATION' };
  }
  if (!simulation || typeof simulation !== 'object' || Array.isArray(simulation)) {
    return { valid: false, reason: 'INVALID_SIMULATION_RECORD' };
  }
  if (!validString(operation.action_id) || !validString(operation.authorization_id)) {
    return { valid: false, reason: 'OPERATION_ID_REQUIRED' };
  }
  if (!validString(simulation.simulation_id)) {
    return { valid: false, reason: 'SIMULATION_ID_REQUIRED' };
  }
  if (!validString(operation.input_hash, MAX_DIGEST_LENGTH)) {
    return { valid: false, reason: 'OPERATION_INPUT_HASH_REQUIRED' };
  }
  if (!validString(simulation.input_hash, MAX_DIGEST_LENGTH)) {
    return { valid: false, reason: 'SIMULATION_INPUT_HASH_REQUIRED' };
  }
  if (simulation.input_hash !== operation.input_hash) {
    return { valid: false, reason: 'SIMULATION_INPUT_HASH_MISMATCH' };
  }
  if (simulation.safe !== true) {
    return { valid: false, reason: 'SIMULATION_NOT_SAFE' };
  }

  const digest = computeOperationDigest(operation);
  if (!digest.valid) return digest;

  return {
    valid: true,
    reason: 'SIMULATION_OPERATION_BOUND',
    binding: Object.freeze({
      simulation_id: simulation.simulation_id,
      action_id: operation.action_id,
      authorization_id: operation.authorization_id,
      operation_digest: digest.digest,
      input_hash: operation.input_hash,
    }),
  };
}

export function verifySimulationBinding(binding, operation, simulation) {
  if (!binding || typeof binding !== 'object' || Array.isArray(binding)) {
    return { valid: false, reason: 'INVALID_SIMULATION_BINDING' };
  }
  if (!validDigest(binding.operation_digest)) {
    return { valid: false, reason: 'SIMULATION_OPERATION_DIGEST_REQUIRED' };
  }
  if (!validString(binding.simulation_id) || binding.simulation_id !== simulation?.simulation_id) {
    return { valid: false, reason: 'SIMULATION_ID_MISMATCH' };
  }
  if (binding.action_id !== operation?.action_id) {
    return { valid: false, reason: 'SIMULATION_ACTION_ID_MISMATCH' };
  }
  if (binding.authorization_id !== operation?.authorization_id) {
    return { valid: false, reason: 'SIMULATION_AUTHORIZATION_ID_MISMATCH' };
  }
  if (binding.input_hash !== operation?.input_hash || binding.input_hash !== simulation?.input_hash) {
    return { valid: false, reason: 'SIMULATION_INPUT_HASH_MISMATCH' };
  }
  const digest = computeOperationDigest(operation);
  if (!digest.valid) return digest;
  return digest.digest === binding.operation_digest
    ? { valid: true, reason: 'SIMULATION_OPERATION_BINDING_VALID' }
    : { valid: false, reason: 'SIMULATION_OPERATION_DIGEST_MISMATCH' };
}
