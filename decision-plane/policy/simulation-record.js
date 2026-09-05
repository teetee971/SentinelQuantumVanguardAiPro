/**
 * Structured simulation proof for sensitive Sentinel actions.
 * The record binds one simulation result to the exact action, target and policy.
 * This is a structural proof only; the producer must be independently trusted.
 */

const REQUIRED_FIELDS = Object.freeze([
  'simulation_id',
  'action_id',
  'action',
  'target_id',
  'policy_version',
  'input_hash',
  'simulation_version',
  'started_at',
  'completed_at',
  'safe',
  'source',
]);

const FORBIDDEN_SOURCES = new Set(['ai', 'model', 'test', 'fixture', 'automation']);

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function parseTimestamp(value) {
  if (!isNonEmptyString(value)) return null;
  const time = Date.parse(value);
  return Number.isFinite(time) ? time : null;
}

function validateSimulationRecord(record, expected = {}, now = Date.now()) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) {
    return { valid: false, reason: 'INVALID_SIMULATION_RECORD' };
  }

  for (const field of REQUIRED_FIELDS) {
    if (record[field] === undefined || record[field] === null) {
      return { valid: false, reason: `SIMULATION_FIELD_REQUIRED:${field}` };
    }
  }

  for (const field of ['simulation_id', 'action_id', 'action', 'target_id', 'policy_version', 'input_hash', 'simulation_version']) {
    if (!isNonEmptyString(record[field])) {
      return { valid: false, reason: `SIMULATION_FIELD_INVALID:${field}` };
    }
  }

  if (record.safe !== true) return { valid: false, reason: 'SIMULATION_NOT_SAFE' };

  const startedAt = parseTimestamp(record.started_at);
  const completedAt = parseTimestamp(record.completed_at);
  if (startedAt === null || completedAt === null || completedAt < startedAt) {
    return { valid: false, reason: 'INVALID_SIMULATION_WINDOW' };
  }
  if (!Number.isFinite(now) || now < completedAt) {
    return { valid: false, reason: 'SIMULATION_NOT_COMPLETED' };
  }

  const source = String(record.source).trim().toLowerCase();
  if (FORBIDDEN_SOURCES.has(source) || source !== 'simulator') {
    return { valid: false, reason: 'SIMULATION_SOURCE_UNTRUSTED' };
  }

  const expectedPairs = [
    ['action', record.action, expected.action],
    ['action_id', record.action_id, expected.action_id],
    ['target_id', record.target_id, expected.target_id],
    ['policy_version', record.policy_version, expected.policy_version],
  ];
  for (const [field, actual, wanted] of expectedPairs) {
    if (wanted !== undefined && actual !== wanted) {
      return { valid: false, reason: `SIMULATION_BINDING_MISMATCH:${field}` };
    }
  }

  return { valid: true, reason: 'SIMULATION_RECORD_VALID' };
}

export { REQUIRED_FIELDS, validateSimulationRecord };
