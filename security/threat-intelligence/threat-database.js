import {
  THREAT_VERDICTS,
  buildThreatConsensus,
  normalizeThreatObservation
} from './normalization.js';

export const SENTINEL_THREAT_DATABASE_VERSION = 'sentinel-threat-db-v1';

function isoMillis(value, fallback = null) {
  if (value == null) return fallback;
  const ms = Date.parse(value);
  return Number.isFinite(ms) ? ms : fallback;
}

function minIso(values) {
  const parsed = values
    .map(value => [value, isoMillis(value)])
    .filter(([, ms]) => Number.isFinite(ms))
    .sort((a, b) => a[1] - b[1]);
  return parsed.length ? new Date(parsed[0][1]).toISOString() : null;
}

function maxIso(values) {
  const parsed = values
    .map(value => [value, isoMillis(value)])
    .filter(([, ms]) => Number.isFinite(ms))
    .sort((a, b) => b[1] - a[1]);
  return parsed.length ? new Date(parsed[0][1]).toISOString() : null;
}

function uniqueSorted(values) {
  return [...new Set(values.filter(value => typeof value === 'string' && value.trim() !== ''))]
    .sort();
}

function indicatorKey(item) {
  return `${item.indicator_type}:${item.indicator_value}`;
}

function isActive(item, nowMs) {
  const expires = isoMillis(item.expires_at);
  return expires == null || expires > nowMs;
}

function aggregateRecord(items, generatedAt) {
  const nowMs = Date.parse(generatedAt);
  const active = items.filter(item => isActive(item, nowMs));
  const evidence = active.length ? active : [];

  const consensus = evidence.length
    ? buildThreatConsensus(evidence)
    : Object.freeze({
        indicator_type: items[0].indicator_type,
        indicator_value: items[0].indicator_value,
        verdict: THREAT_VERDICTS.UNKNOWN,
        confidence: 0,
        sources: [],
        reasons: ['ALL_EVIDENCE_EXPIRED'],
        attribution: null,
        enforcement_allowed: false
      });

  const knownExploited = active.some(item => item.exploit_status === 'known_exploited');
  const knownRansomwareUse = active.some(item => item.ransomware_use === 'Known');

  return Object.freeze({
    indicator_type: items[0].indicator_type,
    indicator_value: items[0].indicator_value,
    active: active.length > 0,
    verdict: consensus.verdict,
    confidence: consensus.confidence,
    sources: uniqueSorted(active.map(item => item.source_id)),
    source_kinds: uniqueSorted(active.map(item => item.source_kind)),
    malware_families: uniqueSorted(active.map(item => item.malware_family)),
    threat_types: uniqueSorted(active.map(item => item.threat_type)),
    platforms: uniqueSorted(active.map(item => item.platform)),
    vendors: uniqueSorted(active.map(item => item.vendor)),
    products: uniqueSorted(active.map(item => item.product)),
    cwes: uniqueSorted(active.flatMap(item => item.cwes ?? [])),
    tags: uniqueSorted(active.flatMap(item => item.tags ?? [])),
    first_seen: minIso(active.map(item => item.first_seen ?? item.observed_at)),
    last_seen: maxIso(active.map(item => item.last_seen ?? item.observed_at)),
    expires_at: minIso(active.map(item => item.expires_at).filter(Boolean)),
    known_exploited: knownExploited,
    known_ransomware_use: knownRansomwareUse,
    reasons: consensus.reasons,
    enforcement_allowed: false
  });
}

export function buildThreatDatabase(observations, {
  generatedAt = new Date().toISOString()
} = {}) {
  if (!Array.isArray(observations)) throw new TypeError('THREAT_DATABASE_OBSERVATIONS_REQUIRED');
  if (!Number.isFinite(Date.parse(generatedAt))) {
    throw new TypeError('THREAT_DATABASE_GENERATED_AT_INVALID');
  }

  const normalized = observations.map(normalizeThreatObservation);
  const byObservationId = new Map();
  for (const item of normalized) {
    const existing = byObservationId.get(item.observation_id);
    if (
      existing &&
      (existing.indicator_type !== item.indicator_type ||
        existing.indicator_value !== item.indicator_value ||
        existing.source_id !== item.source_id)
    ) {
      throw new TypeError('THREAT_DATABASE_OBSERVATION_ID_COLLISION');
    }
    if (!existing || Date.parse(item.retrieved_at) > Date.parse(existing.retrieved_at)) {
      byObservationId.set(item.observation_id, item);
    }
  }

  const groups = new Map();
  for (const item of byObservationId.values()) {
    const key = indicatorKey(item);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }

  const records = [...groups.values()]
    .map(items => aggregateRecord(items, generatedAt))
    .sort((a, b) =>
      a.indicator_type.localeCompare(b.indicator_type) ||
      a.indicator_value.localeCompare(b.indicator_value)
    );

  return Object.freeze({
    schema_version: SENTINEL_THREAT_DATABASE_VERSION,
    generated_at: new Date(Date.parse(generatedAt)).toISOString(),
    observation_count: byObservationId.size,
    record_count: records.length,
    records: Object.freeze(records)
  });
}

/**
 * Conservative mobile export:
 * - multi-source KNOWN_MALWARE SHA-256 => malicious
 * - single-source malicious / multi-source suspicious => suspicious
 * - conflicts, unknown and expired records are excluded
 */
export function buildAndroidSha256Reputation(database) {
  if (!database || database.schema_version !== SENTINEL_THREAT_DATABASE_VERSION) {
    throw new TypeError('THREAT_DATABASE_INVALID');
  }

  const malicious = [];
  const suspicious = [];

  for (const record of database.records) {
    if (!record.active || record.indicator_type !== 'sha256') continue;

    if (record.verdict === THREAT_VERDICTS.KNOWN_MALWARE) {
      malicious.push(record.indicator_value);
      continue;
    }
    if (
      record.verdict === THREAT_VERDICTS.LIKELY_MALICIOUS ||
      record.verdict === THREAT_VERDICTS.SUSPICIOUS
    ) {
      suspicious.push(record.indicator_value);
    }
  }

  return Object.freeze({
    malicious_sha256: Object.freeze([...new Set(malicious)].sort()),
    suspicious_sha256: Object.freeze([...new Set(suspicious)].sort())
  });
}
