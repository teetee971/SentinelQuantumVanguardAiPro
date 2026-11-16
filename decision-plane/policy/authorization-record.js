/**
 * Structured authorization proof for sensitive Sentinel actions.
 * A boolean flag is never sufficient to establish authorization.
 * Structural validity is only one trust layer; issuer, integrity and
 * replay/freshness checks must be enforced by the execution boundary.
 */

import { validateProofWindow } from './proof-freshness.js';

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

function validateAuthorizationRecord(record, now = Date.now()) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) {
    return { valid: false, reason: 'INVALID_AUTHORIZATION_RECORD' };
  }

  for (const field of REQUIRED_FIELDS) {
    if (record[field] === undefined || record[field] === null) {
      return { valid: false, reason: `AUTHORIZATION_FIELD_REQUIRED:${field}` };
    }
  }

  const window = validateProofWindow({
    issuedAt: record.issued_at,
    expiresAt: record.expires_at,
    now,
  });
  if (!window.valid) {
    return { valid: false, reason: 'AUTHORIZATION_EXPIRED_OR_NOT_YET_VALID' };
  }

  if (!isNonEmptyString(record.authorization_id) || !isNonEmptyString(record.actor_id)
    || !isNonEmptyString(record.action) || !isNonEmptyString(record.target_id)) {
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

  return { valid: true, reason: 'AUTHORIZATION_RECORD_VALID' };
}

export { REQUIRED_FIELDS, validateAuthorizationRecord };
