import assert from 'node:assert/strict';
import test from 'node:test';
import { fetchOfficialBytes } from './official-numbering-download.js';

const options = { allowedOrigins: ['https://data.rtr.at'], maxBytes: 100 };
test('accepts bounded bytes only from an explicitly allowed HTTPS origin', async () => {
  const result = await fetchOfficialBytes('https://data.rtr.at/example.csv', {
    ...options, fetchImpl: async (url, config) => {
      assert.equal(config.redirect, 'manual');
      assert.ok(config.signal);
      return new Response('official');
    }
  });
  assert.equal(result.bytes.toString(), 'official');
  for (const url of ['http://data.rtr.at/a', 'https://evil.example/a', 'https://user@data.rtr.at/a', 'https://data.rtr.at:8080/a']) {
    await assert.rejects(fetchOfficialBytes(url, { ...options, fetchImpl: async () => { throw new Error('MUST_NOT_FETCH'); } }), /OFFICIAL_SOURCE_NOT_ALLOWED/);
  }
});
test('follows an official-host redirect and records the final download URL', async () => {
  const requested = [];
  const result = await fetchOfficialBytes('https://data.rtr.at/start', {
    ...options, fetchImpl: async url => {
      requested.push(url);
      return requested.length === 1 ? new Response(null, { status: 302, headers: { location: '/final.csv' } }) : new Response('csv');
    }
  });
  assert.equal(result.url, 'https://data.rtr.at/final.csv');
  assert.equal(requested.length, 2);
});
test('refuses foreign redirects without requesting the foreign host', async () => {
  let calls = 0;
  await assert.rejects(fetchOfficialBytes('https://data.rtr.at/start', {
    ...options, fetchImpl: async () => { calls++; return new Response(null, { status: 302, headers: { location: 'https://evil.example/csv' } }); }
  }), /OFFICIAL_SOURCE_NOT_ALLOWED/);
  assert.equal(calls, 1);
});
test('caps redirect loops and rejects missing destinations', async () => {
  await assert.rejects(fetchOfficialBytes('https://data.rtr.at/start', {
    ...options, fetchImpl: async () => new Response(null, { status: 302, headers: { location: '/loop' } })
  }), /OFFICIAL_REDIRECT_LIMIT/);
  await assert.rejects(fetchOfficialBytes('https://data.rtr.at/start', {
    ...options, fetchImpl: async () => new Response(null, { status: 302 })
  }), /OFFICIAL_REDIRECT_WITHOUT_LOCATION/);
});
test('rejects declared and streamed oversize, empty bodies and HTTP errors', async () => {
  for (const response of [new Response('large'), new Response('x', { headers: { 'content-length': '100' } })]) {
    await assert.rejects(fetchOfficialBytes('https://data.rtr.at/a', { ...options, maxBytes: 2, fetchImpl: async () => response }), /OFFICIAL_INPUT_TOO_LARGE/);
  }
  await assert.rejects(fetchOfficialBytes('https://data.rtr.at/a', { ...options, fetchImpl: async () => new Response('') }), /OFFICIAL_EMPTY_RESPONSE/);
  await assert.rejects(fetchOfficialBytes('https://data.rtr.at/a', { ...options, fetchImpl: async () => new Response('error', { status: 503 }) }), /OFFICIAL_HTTP_503/);
});
