const SOURCE_ID = /^[A-Za-z0-9._:-]{1,80}$/;
const SCOPE = /^[a-z0-9][a-z0-9._:-]{0,79}$/;
const HTTPS_URL = /^https:\/\//i;
const MAX_SOURCES = 200;
const MAX_SCOPES = 32;

function nonEmpty(value, max = 200) {
  return typeof value === 'string' && value.trim().length > 0 && value.trim().length <= max;
}

function timestamp(value) {
  if (!nonEmpty(value, 64)) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizeSource(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('SOURCE_RECORD_INVALID');
  const sourceId = String(raw.source_id || '').trim();
  if (!SOURCE_ID.test(sourceId)) throw new Error('SOURCE_ID_INVALID');
  if (!nonEmpty(raw.name, 160)) throw new Error('SOURCE_NAME_INVALID');
  if (!['authorized', 'suspended', 'expired'].includes(raw.status)) throw new Error('SOURCE_STATUS_INVALID');
  if (!nonEmpty(raw.authorization_type, 80)) throw new Error('SOURCE_AUTHORIZATION_TYPE_INVALID');
  if (!nonEmpty(raw.authorization_reference, 240)) throw new Error('SOURCE_AUTHORIZATION_REFERENCE_INVALID');
  if (!nonEmpty(raw.evidence_url, 2048) || !HTTPS_URL.test(raw.evidence_url.trim())) throw new Error('SOURCE_EVIDENCE_URL_INVALID');
  if (!nonEmpty(raw.reviewed_by, 120)) throw new Error('SOURCE_REVIEWER_INVALID');

  const validFrom = timestamp(raw.valid_from);
  const validUntil = timestamp(raw.valid_until);
  const reviewedAt = timestamp(raw.reviewed_at);
  if (validFrom === null || validUntil === null || reviewedAt === null || validUntil <= validFrom) {
    throw new Error('SOURCE_VALIDITY_INVALID');
  }

  if (!Array.isArray(raw.scopes) || raw.scopes.length < 1 || raw.scopes.length > MAX_SCOPES) {
    throw new Error('SOURCE_SCOPES_INVALID');
  }
  const scopes = raw.scopes.map((value) => String(value).trim());
  if (scopes.some((value) => !SCOPE.test(value)) || new Set(scopes).size !== scopes.length) {
    throw new Error('SOURCE_SCOPES_INVALID');
  }

  return Object.freeze({
    source_id: sourceId,
    name: raw.name.trim(),
    status: raw.status,
    authorization_type: raw.authorization_type.trim(),
    authorization_reference: raw.authorization_reference.trim(),
    evidence_url: raw.evidence_url.trim(),
    reviewed_by: raw.reviewed_by.trim(),
    reviewed_at: new Date(reviewedAt).toISOString(),
    valid_from: new Date(validFrom).toISOString(),
    valid_until: new Date(validUntil).toISOString(),
    scopes: Object.freeze([...scopes].sort()),
  });
}

function createSourceRegistry(records = []) {
  if (!Array.isArray(records) || records.length > MAX_SOURCES) throw new Error('SOURCE_REGISTRY_INVALID');
  const map = new Map();
  for (const raw of records) {
    const source = normalizeSource(raw);
    if (map.has(source.source_id)) throw new Error('SOURCE_ID_DUPLICATE');
    map.set(source.source_id, source);
  }
  return map;
}

function authorizeSource(registry, { sourceId, scope, at = Date.now() } = {}) {
  if (!(registry instanceof Map)) return { allowed: false, reason: 'SOURCE_REGISTRY_REQUIRED' };
  if (!SOURCE_ID.test(String(sourceId || ''))) return { allowed: false, reason: 'SOURCE_ID_INVALID' };
  if (!SCOPE.test(String(scope || ''))) return { allowed: false, reason: 'SOURCE_SCOPE_INVALID' };
  if (!Number.isFinite(at) || at < 0) return { allowed: false, reason: 'SOURCE_TIME_INVALID' };

  const source = registry.get(sourceId);
  if (!source) return { allowed: false, reason: 'SOURCE_NOT_REGISTERED' };
  if (source.status !== 'authorized') return { allowed: false, reason: 'SOURCE_NOT_AUTHORIZED' };
  const validFrom = Date.parse(source.valid_from);
  const validUntil = Date.parse(source.valid_until);
  if (at < validFrom || at >= validUntil) return { allowed: false, reason: 'SOURCE_AUTHORIZATION_OUTSIDE_VALIDITY' };
  if (!source.scopes.includes(scope)) return { allowed: false, reason: 'SOURCE_SCOPE_NOT_AUTHORIZED' };

  return { allowed: true, reason: 'SOURCE_AUTHORIZED', source };
}

export { MAX_SOURCES, authorizeSource, createSourceRegistry, normalizeSource };
