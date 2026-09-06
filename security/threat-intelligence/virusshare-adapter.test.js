import assert from 'node:assert/strict';
import test from 'node:test';
import { canVirusShareAdapterDownloadSamples, mapVirusShareReportToObservation, queryVirusShareMetadata } from './virusshare-adapter.js';

const HASH = 'b'.repeat(64);
const NOW = '2026-09-06T19:00:00.000Z';

function response(payload, status = 200) {
  const text = JSON.stringify(payload);
  return { ok: status >= 200 && status < 300, status, headers: { get: () => null }, async text() { return text; } };
}

test('maps malware and benign VirusShare reports without attribution or download', () => {
  const malware = mapVirusShareReportToObservation({ response: 1, sha256: HASH, added_timestamp: '2026-09-06T18:00:00+00:00', filetype: 'PE32', extension: 'exe' }, { queriedHash: HASH, retrievedAt: NOW });
  assert.equal(malware.source_id, 'virusshare');
  assert.equal(malware.source_verdict, 'malicious');
  assert.equal(malware.sample_downloaded, false);
  assert.equal(malware.attribution, null);

  const benign = mapVirusShareReportToObservation({ response: 2, sha256: HASH }, { queriedHash: HASH, retrievedAt: NOW });
  assert.equal(benign.source_verdict, 'benign');
});

test('queries only the file-report endpoint and never the download endpoint', async () => {
  let captured;
  const result = await queryVirusShareMetadata(HASH, {
    apiKey: 'test-key',
    now: () => Date.parse(NOW),
    fetchImpl: async (url, options) => {
      captured = { url, options };
      return response({ response: 1, sha256: HASH, added_timestamp: '2026-09-06T18:00:00+00:00' });
    }
  });

  const url = new URL(captured.url);
  assert.equal(url.pathname, '/apiv2/file');
  assert.equal(url.searchParams.get('hash'), HASH);
  assert.equal(url.searchParams.get('apikey'), 'test-key');
  assert.equal(captured.options.redirect, 'error');
  assert.equal(result.found, true);
  assert.equal(result.sample_downloaded, false);
  assert.equal(result.actor_attribution, null);
  assert.equal(result.enforcement_allowed, false);
  assert.doesNotMatch(captured.url, /\/download/);
});

test('returns unknown without escalation and fails closed on invalid inputs', async () => {
  const unknown = await queryVirusShareMetadata(HASH, { apiKey: 'x', fetchImpl: async () => response({ response: 0 }) });
  assert.equal(unknown.found, false);
  assert.equal(unknown.sample_downloaded, false);

  await assert.rejects(() => queryVirusShareMetadata('bad', { apiKey: 'x', fetchImpl: async () => response({}) }), /VIRUSSHARE_HASH_INVALID/);
  await assert.rejects(() => queryVirusShareMetadata(HASH, { fetchImpl: async () => response({}) }), /VIRUSSHARE_API_KEY_REQUIRED/);
  await assert.rejects(() => queryVirusShareMetadata(HASH, { apiKey: 'x', fetchImpl: async () => response({ response: 7 }) }), /VIRUSSHARE_RESPONSE_INVALID/);
});

test('adapter cannot download samples', () => {
  assert.equal(canVirusShareAdapterDownloadSamples(), false);
});
