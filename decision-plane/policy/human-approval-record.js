/**
 * Structured human approval proof for sensitive Sentinel actions.
 * Approval is scoped to one exact action, target and policy version.
 */

const REQUIRED_FIELDS = Object.freeze([
  'approval_id',
  'actor_id',
  'approved_at',
  'expires_at',
  'action',
  'target_id',
  'scope',
  'policy_version',
  'source',
]);

const FORBIDDEN_APPROVAL_SOURCES = new Set(['ai', 'model', 'test', 'fixture', 'automation']);

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function parseTimestamp(value) {
  if (!isNonEmptyString(value)) return null;
  const time = Date.parse(value);
  return Number.isFinite(time) ? time : null;
}

function validateHumanApprovalRecord(record, expected = {}, now = Date.now()) {
  if (!record || typeof record !== 'object') {
    return { valid: false, reason: 'INVALID_HUMAN_APPROVAL_RECORD' };
  }

  for (const field of REQUIRED_FIELDS) {
    if (record[field] === undefined || record[field] === null) {
      return { valid: false, reason: `APPROVAL_FIELD_REQUIRED:${field}` };
    }
  }

  const approvedAt = parseTimestamp(record.approved_at);
  const expiresAt = parseTimestamp(record.expires_at);
  if (approvedAt === null || expiresAt === null || expiresAt <= approvedAt) {
    return { valid: false, reason: 'INVALID_APPROVAL_WINDOW' };
  }

  if (!isNonEmptyString(record.actor_id) || !isNonEmptyString(record.action) || !isNonEmptyString(record.target_id)) {
    return { valid: false, reason: 'APPROVAL_SCOPE_INCOMPLETE' };
  }

  if (!record.scope || typeof record.scope !== 'object' || Array.isArray(record.scope)) {
    return { valid: false, reason: 'APPROVAL_SCOPE_INVALID' };
  }

  if (!isNonEmptyString(record.policy_version)) {
    return { valid: false, reason: 'POLICY_VERSION_REQUIRED' };
  }

  const source = String(record.source).trim().toLowerCase();
  if (FORBIDDEN_APPROVAL_SOURCES.has(source) || source !== 'human') {
    return { valid: false, reason: 'APPROVAL_SOURCE_NOT_HUMAN' };
  }

  if (!Number.isFinite(now) || now < approvedAt || now >= expiresAt) {
    return { valid: false, reason: 'APPROVAL_EXPIRED_OR_NOT_YET_VALID' };
  }

  const expectedPairs = [
    ['action', record.action, expected.action],
    ['target_id', record.target_id, expected.target_id],
    ['policy_version', record.policy_version, expected.policy_version],
  ];

  for (const [field, actual, wanted] of expectedPairs) {
    if (wanted !== undefined && actual !== wanted) {
      return { valid: false, reason: `APPROVAL_BINDING_MISMATCH:${field}` };
    }
  }

  return { valid: true, reason: 'HUMAN_APPROVAL_RECORD_VALID' };
}

export { REQUIRED_FIELDS, validateHumanApprovalRecord };
