import { normalizeThreatObservation } from './normalization.js';

export const VIRUSSHARE_API_BASE = 'https://virusshare.com/apiv2';
export const VIRUSSHARE_TIMEOUT_MS = 10_000;
export const VIRUSSHARE_MAX_RESPONSE_BYTES = 1_000_000;

function requireApiKey(value) {
  if (typeof value !== 'string' || value.trim() === '' || value.length > 4096) throw new TypeError('VIRUSSHARE_API_KEY_REQUIRED');
  return value.trim();
}

function normalizeHash(value) {
  if (typeof value !== 'string') throw new TypeError('VIRUSSHARE_HASH_REQUIRED');
  const hash = value.trim().toLowerCase();
  const lengths = new Map([[32, 'md5'], [40, 'sha1'], [56, 'sha224'], [64, 'sha256'], [96, 'sha384'], [128, 'sha512']]);
  const type = lengths.get(hash.length);
  if (!type || !/^[a-f0-9]+$/.test(hash)) throw new TypeError('VIRUSSHARE_HASH_INVALID');
  return { type, value: hash };
}

function observedAt(record, fallback) {
  const value = record?.added_timestamp;
  if (typeof value !== 'string') return fallback;
  const ms = Date.parse(value);
  return Number.isFinite(ms) ? new Date(ms).toISOString() : fallback;
}

function indicatorTypeForNormalizer(type) {
  return ['md5', 'sha1', 'sha256'].includes(type) ? type : 'sha256';
}

function assertReportMatchesQuery(report, query) {
  if (!report || typeof report !== 'object' || Array.isArray(report)) throw new TypeError('VIRUSSHARE_REPORT_INVALID');
  const returned = typeof report[query.type] === 'string' ? report[query.type].trim().toLowerCase() : '';
  if (returned !== query.value) throw new Error('VIRUSSHARE_HASH_BINDING_MISMATCH');
}

export function mapVirusShareReportToObservation(report, { queriedHash, retrievedAt }) {
  if (!report || typeof report !== 'object' || Array.isArray(report)) throw new TypeError('VIRUSSHARE_REPORT_INVALID');
  const query = normalizeHash(queriedHash);
  const response = Number(report.response);
  if (![1, 2].includes(response)) throw new TypeError('VIRUSSHARE_REPORT_STATUS_INVALID');

  let type = query.type;
  let value = query.value;
  if (!['md5', 'sha1', 'sha256'].includes(type)) {
    if (typeof report.sha256 !== 'string' || !/^[a-fA-F0-9]{64}$/.test(report.sha256)) throw new TypeError('VIRUSSHARE_SHA256_REQUIRED');
    type = indicatorTypeForNormalizer(type);
    value = report.sha256.toLowerCase();
  }

  return normalizeThreatObservation({
    observation_id: `virusshare:${type}:${value}`,
    indicator_type: type,
    indicator_value: value,
    source_id: 'virusshare',
    source_kind: 'malware_repository',
    source_uri: 'https://virusshare.com/',
    source_verdict: response === 1 ? 'malicious' : 'benign',
    confidence: response === 1 ? 90 : 75,
    observed_at: observedAt(report, retrievedAt),
    retrieved_at: retrievedAt,
    legally_accessible: true,
    tags: [report.filetype, report.extension, report.mimetype].filter(v => typeof v === 'string' && v.trim() !== '').map(v => v.trim())
  });
}

export async function queryVirusShareMetadata(hash, {
  apiKey,
  fetchImpl = globalThis.fetch,
  now = () => Date.now(),
  timeoutMs = VIRUSSHARE_TIMEOUT_MS
} = {}) {
  const query = normalizeHash(hash);
  const key = requireApiKey(apiKey);
  if (typeof fetchImpl !== 'function') throw new TypeError('FETCH_IMPLEMENTATION_REQUIRED');
  if (!Number.isInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > 30_000) throw new TypeError('VIRUSSHARE_TIMEOUT_INVALID');

  const url = new URL(`${VIRUSSHARE_API_BASE}/file`);
  url.searchParams.set('apikey', key);
  url.searchParams.set('hash', query.value);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  let response;
  try {
    response = await fetchImpl(url.toString(), { method: 'GET', redirect: 'error', headers: { Accept: 'application/json' }, signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }

  if (!response || typeof response !== 'object') throw new TypeError('VIRUSSHARE_HTTP_RESPONSE_INVALID');
  if (response.redirected === true) throw new Error('VIRUSSHARE_REDIRECT_REJECTED');
  if (response.ok !== true) throw new Error(`VIRUSSHARE_HTTP_${Number(response.status) || 0}`);
  const length = Number(response.headers?.get?.('content-length'));
  if (Number.isFinite(length) && length > VIRUSSHARE_MAX_RESPONSE_BYTES) throw new Error('VIRUSSHARE_RESPONSE_TOO_LARGE');
  const text = await response.text();
  if (Buffer.byteLength(text, 'utf8') > VIRUSSHARE_MAX_RESPONSE_BYTES) throw new Error('VIRUSSHARE_RESPONSE_TOO_LARGE');

  let payload;
  try { payload = JSON.parse(text); } catch { throw new TypeError('VIRUSSHARE_JSON_INVALID'); }
  const code = Number(payload?.response);
  if (code === 0) return Object.freeze({ found: false, observations: [], sample_downloaded: false });
  if (![1, 2].includes(code)) throw new TypeError('VIRUSSHARE_RESPONSE_INVALID');

  assertReportMatchesQuery(payload, query);

  const retrievedAt = new Date(now()).toISOString();
  const observation = mapVirusShareReportToObservation(payload, { queriedHash: query.value, retrievedAt });
  return Object.freeze({ found: true, observations: [observation], sample_downloaded: false, actor_attribution: null, enforcement_allowed: false });
}

export function canVirusShareAdapterDownloadSamples() { return false; }
