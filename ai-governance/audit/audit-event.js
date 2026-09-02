import { createHash } from 'node:crypto';

const REQUIRED = Object.freeze([
  'trace_id',
  'decision_id',
  'evidence_refs',
  'model_id',
  'model_version',
  'policy_version',
  'action',
  'result',
  'timestamp',
]);

const HASH_PATTERN = /^[a-f0-9]{64}$/;
const DATE_TIME_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;
const MAX_EVIDENCE_REFS = 1000;
const MAX_STRING_LENGTH = 256;
const MAX_VERSION_LENGTH = 128;
const MAX_ACTION_LENGTH = 128;
const AUDIT_FIELDS = new Set([...REQUIRED, 'previous_hash', 'hash']);

function isPlainObject(value) {
  if (value === null || typeof value !== 'object') return false;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function canonicalize(value) {
  if (value === null || typeof value !== 'object') {
    if (typeof value === 'undefined' || typeof value === 'function' || typeof value === 'symbol') {
      throw new TypeError('AUDIT_UNSUPPORTED_VALUE');
    }
    if (typeof value === 'number' && !Number.isFinite(value)) {
      throw new TypeError('AUDIT_NON_FINITE_NUMBER');
    }
    if (typeof value === 'bigint') throw new TypeError('AUDIT_BIGINT_UNSUPPORTED');
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(',')}]`;
  if (!isPlainObject(value)) throw new TypeError('AUDIT_NON_PLAIN_OBJECT');
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`).join(',')}}`;
}

function deepClone(value) {
  return structuredClone(value);
}

function deepFreeze(value, seen = new WeakSet()) {
  if (value === null || typeof value !== 'object' || seen.has(value)) return value;
  seen.add(value);
  for (const child of Object.values(value)) deepFreeze(child, seen);
  return Object.freeze(value);
}

function validateString(value, maxLength) {
  return typeof value === 'string' && value.length >= 1 && value.length <= maxLength;
}

function validateTimestamp(value) {
  return typeof value === 'string'
    && value.length <= 64
    && DATE_TIME_PATTERN.test(value)
    && !Number.isNaN(Date.parse(value));
}

function validateAuditEvent(event, { requireHashes = false } = {}) {
  if (!isPlainObject(event)) return { valid: false, reason: 'AUDIT_EVENT_INVALID' };

  const unknown = Object.keys(event).find((key) => !AUDIT_FIELDS.has(key));
  if (unknown) return { valid: false, reason: 'AUDIT_UNKNOWN_FIELD' };

  for (const field of REQUIRED) {
    if (!Object.hasOwn(event, field)) return { valid: false, reason: `AUDIT_EVENT_MISSING_${field.toUpperCase()}` };
  }

  if (!validateString(event.trace_id, MAX_STRING_LENGTH)
    || !validateString(event.decision_id, MAX_STRING_LENGTH)
    || !validateString(event.model_id, MAX_STRING_LENGTH)
    || !validateString(event.model_version, MAX_VERSION_LENGTH)
    || !validateString(event.policy_version, MAX_VERSION_LENGTH)
    || !validateString(event.action, MAX_ACTION_LENGTH)
    || !validateString(event.result, MAX_ACTION_LENGTH)) {
    return { valid: false, reason: 'AUDIT_EVENT_FIELD_INVALID' };
  }

  if (!Array.isArray(event.evidence_refs)
    || event.evidence_refs.length > MAX_EVIDENCE_REFS
    || event.evidence_refs.some((ref) => !validateString(ref, MAX_STRING_LENGTH))) {
    return { valid: false, reason: 'AUDIT_EVIDENCE_REFS_INVALID' };
  }

  if (!validateTimestamp(event.timestamp)) return { valid: false, reason: 'AUDIT_TIMESTAMP_INVALID' };

  if (requireHashes) {
    if (typeof event.previous_hash !== 'string' || !/^$|^[a-f0-9]{64}$/.test(event.previous_hash)) {
      return { valid: false, reason: 'AUDIT_PREVIOUS_HASH_INVALID' };
    }
    if (typeof event.hash !== 'string' || !HASH_PATTERN.test(event.hash)) {
      return { valid: false, reason: 'AUDIT_HASH_FORMAT_INVALID' };
    }
  }

  return { valid: true, reason: 'AUDIT_EVENT_VALID' };
}

export function hashAuditEvent(event, previousHash = '') {
  if (typeof previousHash !== 'string' || !/^$|^[a-f0-9]{64}$/.test(previousHash)) {
    throw new TypeError('AUDIT_PREVIOUS_HASH_INVALID');
  }
  const payload = `${previousHash}:${canonicalize(event)}`;
  return createHash('sha256').update(payload, 'utf8').digest('hex');
}

export function appendAuditEvent(chain, event) {
  if (!Array.isArray(chain)) throw new TypeError('AUDIT_CHAIN_REQUIRED');

  const existing = verifyAuditChain(chain);
  if (!existing.valid) throw new TypeError(existing.reason);

  const validation = validateAuditEvent(event);
  if (!validation.valid) throw new TypeError(validation.reason);

  const safeEvent = deepClone(event);
  const previousHash = chain.length ? chain.at(-1).hash : '';
  const entry = { ...safeEvent, previous_hash: previousHash };
  const next = [...chain, { ...entry, hash: hashAuditEvent(entry, previousHash) }];
  return deepFreeze(next);
}

export function verifyAuditChain(chain) {
  if (!Array.isArray(chain)) return { valid: false, reason: 'AUDIT_CHAIN_REQUIRED' };

  let previousHash = '';
  for (const entry of chain) {
    const validation = validateAuditEvent(entry, { requireHashes: true });
    if (!validation.valid) return validation;

    if (entry.previous_hash !== previousHash) return { valid: false, reason: 'AUDIT_CHAIN_LINK_INVALID' };
    const { hash, ...event } = entry;
    if (hash !== hashAuditEvent(event, previousHash)) return { valid: false, reason: 'AUDIT_HASH_INVALID' };
    previousHash = hash;
  }

  return { valid: true, reason: 'AUDIT_CHAIN_VALID' };
}
