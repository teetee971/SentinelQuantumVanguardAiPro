const INDICATOR_TYPES = new Set([
  'sha512',
  'sha384',
  'sha256',
  'sha224',
  'sha1',
  'md5',
  'domain',
  'ip',
  'url',
  'email',
  'cve',
  'tlsh',
  'imphash',
  'yara',
  'malware_family',
  'campaign',
  'asn'
]);

const SOURCE_KINDS = new Set([
  'malware_repository',
  'vulnerability_catalog',
  'vendor_telemetry',
  'threat_map',
  'public_osint',
  'internal_sensor',
  'analyst_input'
]);

const SOURCE_VERDICTS = new Set(['malicious', 'suspicious', 'benign', 'unknown']);

export const THREAT_VERDICTS = Object.freeze({
  KNOWN_MALWARE: 'KNOWN_MALWARE',
  LIKELY_MALICIOUS: 'LIKELY_MALICIOUS',
  SUSPICIOUS: 'SUSPICIOUS',
  UNKNOWN: 'UNKNOWN',
  CONFLICTING_INTELLIGENCE: 'CONFLICTING_INTELLIGENCE'
});

export const THREAT_INTELLIGENCE_BOUNDARY = Object.freeze({
  metadata_only: true,
  automatic_sample_download: false,
  source_geolocation_is_actor_attribution: false,
  autonomous_actor_attribution: false,
  autonomous_enforcement: false
});

function requireObject(value, code) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError(code);
}

function requireString(value, code, maxLength = 4096) {
  if (typeof value !== 'string' || value.trim() === '' || value.length > maxLength) {
    throw new TypeError(code);
  }
  return value.trim();
}

function normalizeIsoDate(value, code) {
  const text = requireString(value, code, 64);
  const ms = Date.parse(text);
  if (!Number.isFinite(ms)) throw new TypeError(code);
  return new Date(ms).toISOString();
}

function normalizeHash(value, length, code) {
  const normalized = requireString(value, code, 256).toLowerCase();
  if (!new RegExp(`^[a-f0-9]{${length}}$`).test(normalized)) throw new TypeError(code);
  return normalized;
}

function normalizeIndicatorValue(type, value) {
  if (type === 'sha512') return normalizeHash(value, 128, 'INVALID_SHA512');
  if (type === 'sha384') return normalizeHash(value, 96, 'INVALID_SHA384');
  if (type === 'sha256') return normalizeHash(value, 64, 'INVALID_SHA256');
  if (type === 'sha224') return normalizeHash(value, 56, 'INVALID_SHA224');
  if (type === 'sha1') return normalizeHash(value, 40, 'INVALID_SHA1');
  if (type === 'md5') return normalizeHash(value, 32, 'INVALID_MD5');
  if (type === 'cve') {
    const normalized = requireString(value, 'INVALID_CVE', 64).toUpperCase();
    if (!/^CVE-\d{4}-\d{4,}$/.test(normalized)) throw new TypeError('INVALID_CVE');
    return normalized;
  }
  return requireString(value, 'INVALID_INDICATOR_VALUE', 4096);
}

function normalizeLocation(location, code) {
  if (location == null) return null;
  requireObject(location, code);
  const result = {};
  if (location.country_code != null) {
    const cc = requireString(location.country_code, code, 2).toUpperCase();
    if (!/^[A-Z]{2}$/.test(cc)) throw new TypeError(code);
    result.country_code = cc;
  }
  if (location.region != null) result.region = requireString(location.region, code, 128);
  if (location.city != null) result.city = requireString(location.city, code, 128);
  if (location.asn != null) result.asn = requireString(String(location.asn), code, 64);
  if (Object.keys(result).length === 0) throw new TypeError(code);
  return result;
}

function rejectAttributionFields(record) {
  const forbidden = [
    'actor_country',
    'origin_country',
    'responsible_country',
    'attributed_country',
    'responsible_actor',
    'attributed_actor'
  ];
  for (const field of forbidden) {
    if (Object.hasOwn(record, field)) throw new TypeError(`AUTOMATED_ATTRIBUTION_FIELD_FORBIDDEN:${field}`);
  }
}

export function normalizeThreatObservation(input) {
  requireObject(input, 'THREAT_OBSERVATION_REQUIRED');
  rejectAttributionFields(input);

  const indicatorType = requireString(input.indicator_type, 'INDICATOR_TYPE_REQUIRED', 64).toLowerCase();
  if (!INDICATOR_TYPES.has(indicatorType)) throw new TypeError('INDICATOR_TYPE_UNSUPPORTED');

  const sourceKind = requireString(input.source_kind, 'SOURCE_KIND_REQUIRED', 64).toLowerCase();
  if (!SOURCE_KINDS.has(sourceKind)) throw new TypeError('SOURCE_KIND_UNSUPPORTED');

  const sourceVerdict = requireString(input.source_verdict ?? 'unknown', 'SOURCE_VERDICT_REQUIRED', 32).toLowerCase();
  if (!SOURCE_VERDICTS.has(sourceVerdict)) throw new TypeError('SOURCE_VERDICT_UNSUPPORTED');

  if (input.legally_accessible !== true) throw new TypeError('LEGAL_ACCESS_CONFIRMATION_REQUIRED');

  const confidence = Number(input.confidence ?? 0);
  if (!Number.isFinite(confidence) || confidence < 0 || confidence > 100) {
    throw new TypeError('CONFIDENCE_OUT_OF_RANGE');
  }

  const observation = {
    observation_id: requireString(input.observation_id, 'OBSERVATION_ID_REQUIRED', 256),
    indicator_type: indicatorType,
    indicator_value: normalizeIndicatorValue(indicatorType, input.indicator_value),
    source_id: requireString(input.source_id, 'SOURCE_ID_REQUIRED', 256),
    source_kind: sourceKind,
    source_uri: requireString(input.source_uri, 'SOURCE_URI_REQUIRED', 2048),
    source_verdict: sourceVerdict,
    confidence,
    observed_at: normalizeIsoDate(input.observed_at, 'OBSERVED_AT_REQUIRED'),
    retrieved_at: normalizeIsoDate(input.retrieved_at, 'RETRIEVED_AT_REQUIRED'),
    legally_accessible: true,
    observed_source_location: normalizeLocation(input.observed_source_location, 'INVALID_SOURCE_LOCATION'),
    observed_target_location: normalizeLocation(input.observed_target_location, 'INVALID_TARGET_LOCATION'),
    malware_family: input.malware_family == null ? null : requireString(input.malware_family, 'INVALID_MALWARE_FAMILY', 256),
    tags: Array.isArray(input.tags)
      ? [...new Set(input.tags.map(tag => requireString(String(tag), 'INVALID_TAG', 128)))].sort()
      : [],
    sample_downloaded: false,
    attribution: null
  };

  if (Date.parse(observation.retrieved_at) < Date.parse(observation.observed_at)) {
    throw new TypeError('RETRIEVED_BEFORE_OBSERVED');
  }

  return Object.freeze(observation);
}

function observationKey(observation) {
  return `${observation.indicator_type}:${observation.indicator_value}`;
}

export function buildThreatConsensus(observations) {
  if (!Array.isArray(observations) || observations.length === 0) {
    return Object.freeze({ verdict: THREAT_VERDICTS.UNKNOWN, confidence: 0, sources: [], reasons: ['NO_OBSERVATIONS'] });
  }

  const normalized = observations.map(normalizeThreatObservation);
  const keys = new Set(normalized.map(observationKey));
  if (keys.size !== 1) throw new TypeError('CONSENSUS_REQUIRES_SINGLE_INDICATOR');

  const bySource = new Map();
  for (const item of normalized) {
    const current = bySource.get(item.source_id);
    if (!current || item.confidence > current.confidence) bySource.set(item.source_id, item);
  }
  const independent = [...bySource.values()];

  const malicious = independent.filter(item => item.source_verdict === 'malicious');
  const suspicious = independent.filter(item => item.source_verdict === 'suspicious');
  const benign = independent.filter(item => item.source_verdict === 'benign');

  let verdict = THREAT_VERDICTS.UNKNOWN;
  const reasons = [];

  if (malicious.length > 0 && benign.length > 0) {
    verdict = THREAT_VERDICTS.CONFLICTING_INTELLIGENCE;
    reasons.push('MALICIOUS_AND_BENIGN_SOURCES_CONFLICT');
  } else if (malicious.length >= 2) {
    verdict = THREAT_VERDICTS.KNOWN_MALWARE;
    reasons.push('MULTI_SOURCE_MALICIOUS_CONSENSUS');
  } else if (malicious.length === 1 || suspicious.length >= 2) {
    verdict = THREAT_VERDICTS.LIKELY_MALICIOUS;
    reasons.push(malicious.length === 1 ? 'SINGLE_SOURCE_MALICIOUS' : 'MULTI_SOURCE_SUSPICIOUS_CONSENSUS');
  } else if (suspicious.length === 1) {
    verdict = THREAT_VERDICTS.SUSPICIOUS;
    reasons.push('SINGLE_SOURCE_SUSPICIOUS');
  } else {
    reasons.push('INSUFFICIENT_MALICIOUS_EVIDENCE');
  }

  const confidence = independent.length
    ? Math.round(independent.reduce((sum, item) => sum + item.confidence, 0) / independent.length)
    : 0;

  return Object.freeze({
    indicator_type: independent[0].indicator_type,
    indicator_value: independent[0].indicator_value,
    verdict,
    confidence,
    sources: independent.map(item => item.source_id).sort(),
    reasons,
    attribution: null,
    enforcement_allowed: false
  });
}

export function buildGlobalThreatSituation(observations) {
  if (!Array.isArray(observations)) throw new TypeError('OBSERVATIONS_ARRAY_REQUIRED');
  const normalized = observations.map(normalizeThreatObservation);

  const byIndicatorType = {};
  const bySourceKind = {};
  const observedSourceCountries = {};
  const observedTargetCountries = {};

  for (const item of normalized) {
    byIndicatorType[item.indicator_type] = (byIndicatorType[item.indicator_type] ?? 0) + 1;
    bySourceKind[item.source_kind] = (bySourceKind[item.source_kind] ?? 0) + 1;

    const sourceCountry = item.observed_source_location?.country_code;
    if (sourceCountry) observedSourceCountries[sourceCountry] = (observedSourceCountries[sourceCountry] ?? 0) + 1;

    const targetCountry = item.observed_target_location?.country_code;
    if (targetCountry) observedTargetCountries[targetCountry] = (observedTargetCountries[targetCountry] ?? 0) + 1;
  }

  return Object.freeze({
    observations: normalized.length,
    by_indicator_type: byIndicatorType,
    by_source_kind: bySourceKind,
    observed_source_countries: observedSourceCountries,
    observed_target_countries: observedTargetCountries,
    actor_attribution: null,
    caveat: 'Observed network or telemetry location is not evidence of actor nationality, responsibility, or attack origin.'
  });
}

export function canAutomaticallyDownloadMalwareSamples() {
  return false;
}

export function canInferActorFromObservedLocation() {
  return false;
}
