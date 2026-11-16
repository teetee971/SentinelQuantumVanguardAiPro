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

const ACTION_TYPES = Object.freeze([
  ...new Set([...DECISION_TYPES, ...SENSITIVE_ACTIONS]),
]);

const DECISION_TYPE_SET = new Set(DECISION_TYPES);
const ACTION_TYPE_SET = new Set(ACTION_TYPES);
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

function isKnownAction(value) {
  const normalized = normalizeOperation(value);
  return normalized !== null && ACTION_TYPE_SET.has(normalized);
}

function isSensitiveAction(value) {
  const normalized = normalizeOperation(value);
  return normalized !== null && SENSITIVE_ACTION_SET.has(normalized);
}

export {
  DECISION_TYPES,
  ACTION_TYPES,
  SENSITIVE_ACTIONS,
  isKnownDecisionType,
  isKnownAction,
  isSensitiveAction,
  normalizeOperation,
};
