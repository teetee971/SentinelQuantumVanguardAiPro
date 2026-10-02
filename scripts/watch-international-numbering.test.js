import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { probe, sourceUrls, watch } from './watch-international-numbering.js';

test('extracts only explicit catalogue sources and deduplicates URLs', () => {
  const text = 'https://outside.example\n## Sources officielles — référence\n- https://official.example/a\n- https://official.example/a\n## Next\nhttps://outside.example/next';
  assert.deepEqual(sourceUrls(text), ['https://official.example/a']);
  assert.throws(() => sourceUrls('no section'), /SOURCE_SECTION_MISSING/);
});
test('the real catalogue is included without a hardcoded URL inventory', () => {
  const urls = sourceUrls(readFileSync('docs/INTERNATIONAL_NUMBERING_SOURCES.md', 'utf8'));
  assert.ok(urls.length >= 30);
  assert.ok(urls.some(value => new URL(value).hostname === 'www.anrt.ma'));
  assert.equal(new Set(urls).size, urls.length);
});
test('probe uses HTTPS, disallows redirects, and hashes bounded response bytes', async () => {
  const result = await probe('https://official.example/', { fetchImpl: async (url, options) => {
    assert.equal(options.redirect, 'error');
    assert.ok(options.signal);
    return new Response('official');
  } });
  assert.equal(result.bytes, 8);
  assert.match(result.sha256, /^[a-f0-9]{64}$/);
  await assert.rejects(probe('http://official.example/'), /HTTPS_SOURCE_REQUIRED/);
});
test('empty, oversized streamed responses and HTTP errors are rejected', async () => {
  await assert.rejects(probe('https://official.example/', { fetchImpl: async () => new Response('') }), /EMPTY_SOURCE/);
  await assert.rejects(probe('https://official.example/', { maxBytes: 2, fetchImpl: async () => new Response('large') }), /SOURCE_TOO_LARGE/);
  await assert.rejects(probe('https://official.example/', { fetchImpl: async () => new Response('error', { status: 503 }) }), /HTTP_503/);
});
test('failed sources preserve last successful provenance and never become fresh', async () => {
  const prior = { url: 'https://official.example/', sha256: 'old', bytes: 10, lastSuccessfulAt: '2026-09-01T00:00:00Z', changedAt: '2026-09-01T00:00:00Z' };
  let attempts = 0;
  const result = await watch([prior.url], { sources: [prior] }, { checkedAt: '2026-10-02T00:00:00Z', probeImpl: async () => { attempts++; throw new Error('HTTP_503'); } });
  assert.equal(attempts, 2);
  assert.equal(result.sources[0].status, 'UNAVAILABLE');
  assert.equal(result.sources[0].sha256, prior.sha256);
  assert.equal(result.sources[0].lastSuccessfulAt, prior.lastSuccessfulAt);
  assert.equal(result.sources[0].changedAt, prior.changedAt);
});
test('retries transient failures and distinguishes first observation, change and no change', async () => {
  const urls = ['https://a.example/', 'https://b.example/', 'https://c.example/'];
  const attempts = new Map();
  const result = await watch(urls, { sources: [{ url: urls[1], sha256: 'old' }, { url: urls[2], sha256: 'new' }] }, {
    checkedAt: '2026-10-02T00:00:00Z', probeImpl: async url => {
      const n = (attempts.get(url) ?? 0) + 1;
      attempts.set(url, n);
      if (n === 1 && url === urls[0]) throw new Error('transient');
      return { sha256: 'new', bytes: 10 };
    }
  });
  assert.deepEqual(result.sources.map(source => source.status), ['FIRST_SEEN', 'CHANGED', 'UNCHANGED']);
  assert.equal(attempts.get(urls[0]), 2);
  assert.equal(result.sources.some(source => 'sourcePublishedAt' in source), false);
});
