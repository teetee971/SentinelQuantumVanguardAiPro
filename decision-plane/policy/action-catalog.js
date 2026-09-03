/**
 * Canonical action/decision vocabulary for the Sentinel decision plane.
 * Unknown values must never acquire privileged semantics by accident.
 */

const DECISION_TYPES = Object.freeze([
  'observe',
  'investigate',
  'simulate',
  'contain',
  'block',
  'allow',
]);

const SENSITIVE_ACTIONS = Object.freeze([
  'execute',
  'contain',
  'block',
  'isolate',
  'delete',
  'quarantine',
  'disable',
]);

const DECISION_TYPE_SET = new Set(DECISION_TYPES);
const SENSITIVE_ACTION_SET = new Set(SENSITIVE_ACTIONS);

function normalizeOperation(value) {
  if (typeof value !== 'string') return null;
  const normalized = value.trim().toLowerCase();
  return normalized || null;
}

function isKnownDecisionType(value) {
  const normalized = normalizeOperation(value);
  return normalized !== null && DECISION_TYPE_SET.has(normalized);
}

function isSensitiveAction(value) {
  const normalized = normalizeOperation(value);
  return normalized !== null && SENSITIVE_ACTION_SET.has(normalized);
}

export {
  DECISION_TYPES,
  SENSITIVE_ACTIONS,
  isKnownDecisionType,
  isSensitiveAction,
  normalizeOperation,
};
