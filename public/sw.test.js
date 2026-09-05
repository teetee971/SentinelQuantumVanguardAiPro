import test from 'node:test';
import assert from 'node:assert/strict';

// public/sw.js is a browser Service Worker script (not a module consumed by the
// app bundle). It is imported here purely to unit test the bounded dynamic
// cache/eviction logic in isolation, using minimal fakes for the SW globals.
class FakeCache {
  constructor() {
    this.store = new Map();
  }
  key(request) {
    return typeof request === 'string' ? request : request.url;
  }
  async match(request) {
    return this.store.get(this.key(request));
  }
  async put(request, response) {
    this.store.set(this.key(request), response);
  }
  async keys() {
    return [...this.store.keys()].map((url) => ({ url }));
  }
  async delete(request) {
    return this.store.delete(this.key(request));
  }
}

class FakeCacheStorage {
  constructor() {
    this.caches = new Map();
  }
  async open(name) {
    if (!this.caches.has(name)) this.caches.set(name, new FakeCache());
    return this.caches.get(name);
  }
}

globalThis.self = {
  addEventListener: () => {},
  skipWaiting: () => {},
  clients: { claim: () => {} },
};
globalThis.caches = new FakeCacheStorage();

const { cacheDynamicResponse, MAX_DYNAMIC_CACHE_ENTRIES, DYNAMIC_CACHE } = await import('./sw.js');

function makeResponse(body) {
  return new Response(body, { status: 200 });
}

test('dynamic cache stays within the configured max entries after overflow', async () => {
  const cache = await globalThis.caches.open(DYNAMIC_CACHE);
  const total = MAX_DYNAMIC_CACHE_ENTRIES + 10;
  for (let i = 0; i < total; i += 1) {
    await cacheDynamicResponse(`https://example.test/asset-${i}.js`, makeResponse(String(i)));
  }
  const keys = await cache.keys();
  assert.equal(keys.length, MAX_DYNAMIC_CACHE_ENTRIES);
});

test('dynamic cache evicts the oldest entries first (FIFO)', async () => {
  globalThis.caches = new FakeCacheStorage();
  const cache = await globalThis.caches.open(DYNAMIC_CACHE);
  const total = MAX_DYNAMIC_CACHE_ENTRIES + 3;
  for (let i = 0; i < total; i += 1) {
    await cacheDynamicResponse(`https://example.test/asset-${i}.js`, makeResponse(String(i)));
  }
  const keys = (await cache.keys()).map((k) => k.url);
  for (let i = 0; i < 3; i += 1) {
    assert.ok(!keys.includes(`https://example.test/asset-${i}.js`), `expected asset-${i}.js to be evicted`);
  }
  for (let i = 3; i < total; i += 1) {
    assert.ok(keys.includes(`https://example.test/asset-${i}.js`), `expected asset-${i}.js to remain cached`);
  }
});

test('re-caching an existing entry does not trigger eviction of others', async () => {
  globalThis.caches = new FakeCacheStorage();
  const cache = await globalThis.caches.open(DYNAMIC_CACHE);
  for (let i = 0; i < MAX_DYNAMIC_CACHE_ENTRIES; i += 1) {
    await cacheDynamicResponse(`https://example.test/asset-${i}.js`, makeResponse(String(i)));
  }
  await cacheDynamicResponse('https://example.test/asset-0.js', makeResponse('updated'));
  const keys = (await cache.keys()).map((k) => k.url);
  assert.equal(keys.length, MAX_DYNAMIC_CACHE_ENTRIES);
  assert.ok(keys.includes('https://example.test/asset-0.js'));
});
