/**
 * Structured authorization proof for sensitive Sentinel actions.
 * A boolean flag is never sufficient to establish authorization.
 */

const REQUIRED_FIELDS = Object.freeze([
  'authorization_id',
  'actor_id',
  'issued_at',
  'expires_at',
  'action',
  'target_id',
  'scope',
  'policy_version',
  'source',
]);

const OPERATIONAL_AUTH_SOURCES = new Set(['system', 'operator']);
const FORBIDDEN_AUTH_SOURCES = new Set(['ai', 'model', 'test', 'fixture']);

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function parseTimestamp(value) {
  if (!isNonEmptyString(value)) return null;
  const time = Date.parse(value);
  return Number.isFinite(time) ? time : null;
}

function validateAuthorizationRecord(record, now = Date.now()) {
  if (!record || typeof record !== 'object') {
    return { valid: false, reason: 'INVALID_AUTHORIZATION_RECORD' };
  }

  for (const field of REQUIRED_FIELDS) {
    if (record[field] === undefined || record[field] === null) {
      return { valid: false, reason: `AUTHORIZATION_FIELD_REQUIRED:${field}` };
    }
  }

  const issuedAt = parseTimestamp(record.issued_at);
  const expiresAt = parseTimestamp(record.expires_at);
  if (issuedAt === null || expiresAt === null || expiresAt <= issuedAt) {
    return { valid: false, reason: 'INVALID_AUTHORIZATION_WINDOW' };
  }

  if (!isNonEmptyString(record.action) || !isNonEmptyString(record.target_id)) {
    return { valid: false, reason: 'AUTHORIZATION_SCOPE_INCOMPLETE' };
  }

  if (!record.scope || typeof record.scope !== 'object' || Array.isArray(record.scope)) {
    return { valid: false, reason: 'AUTHORIZATION_SCOPE_INVALID' };
  }

  if (!isNonEmptyString(record.policy_version)) {
    return { valid: false, reason: 'POLICY_VERSION_REQUIRED' };
  }

  const source = String(record.source).trim().toLowerCase();
  if (FORBIDDEN_AUTH_SOURCES.has(source) || !OPERATIONAL_AUTH_SOURCES.has(source)) {
    return { valid: false, reason: 'AUTHORIZATION_SOURCE_UNTRUSTED' };
  }

  if (!Number.isFinite(now) || now < issuedAt || now >= expiresAt) {
    return { valid: false, reason: 'AUTHORIZATION_EXPIRED_OR_NOT_YET_VALID' };
  }

  return { valid: true, reason: 'AUTHORIZATION_RECORD_VALID' };
}

export { REQUIRED_FIELDS, validateAuthorizationRecord };
