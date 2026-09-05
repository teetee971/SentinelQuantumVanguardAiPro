/**
 * Sentinel Quantum Vanguard AI Pro - Service Worker
 * Cache strategy: cache-first for static assets, network-first for pages.
 * Cache schema: 2.0.0
 */

const CACHE_VERSION = 'sentinel-v2.0.0';
const STATIC_CACHE = `${CACHE_VERSION}-static`;
const DYNAMIC_CACHE = `${CACHE_VERSION}-dynamic`;
const MAX_DYNAMIC_CACHE_ENTRIES = 100;

const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/public/manifest.json',
  '/public/icon.svg',
  '/public/shared-styles.css',
  '/public/shared-navigation.js',
  '/assets/images/modules/soc-monitoring.svg',
  '/assets/images/modules/defense-infrastructure.svg',
  '/assets/images/modules/audit-analysis.svg',
  '/assets/images/modules/ai-orchestration.svg',
  '/assets/images/modules/compliance-governance.svg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then((cache) => cache.addAll(STATIC_ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => Promise.all(
        cacheNames
          .filter((cacheName) => cacheName.startsWith('sentinel-') && cacheName !== STATIC_CACHE && cacheName !== DYNAMIC_CACHE)
          .map((cacheName) => caches.delete(cacheName))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== location.origin) return;

  if (isStaticAsset(url.pathname)) {
    event.respondWith(cacheFirst(request));
  } else {
    event.respondWith(networkFirst(request));
  }
});

function isStaticAsset(pathname) {
  return ['.css', '.js', '.svg', '.png', '.jpg', '.jpeg', '.webp', '.woff', '.woff2'].some((ext) => pathname.endsWith(ext));
}

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;

  try {
    const response = await fetch(request);
    if (response.ok) {
      await cacheDynamicResponse(request, response);
    }
    return response;
  } catch {
    return offlineResponse();
  }
}

async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      await cacheDynamicResponse(request, response);
    }
    return response;
  } catch {
    const cached = await caches.match(request);
    return cached || offlineResponse();
  }
}

async function cacheDynamicResponse(request, response) {
  const cache = await caches.open(DYNAMIC_CACHE);
  const existing = await cache.match(request);
  await cache.put(request, response.clone());
  if (existing) return;

  const keys = await cache.keys();
  const excess = keys.length - MAX_DYNAMIC_CACHE_ENTRIES;
  await Promise.all(keys.slice(0, Math.max(0, excess)).map((key) => cache.delete(key)));
}

function offlineResponse() {
  return new Response('Sentinel est hors connexion. Vérifiez votre réseau.', {
    status: 503,
    statusText: 'Service Unavailable',
    headers: { 'Content-Type': 'text/plain; charset=utf-8' }
  });
}

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting();
  if (event.data?.type === 'CACHE_URLS' && Array.isArray(event.data.urls)) {
    event.waitUntil(Promise.all(
      event.data.urls.map(async (url) => {
        const request = new Request(url);
        if (new URL(request.url).origin !== location.origin) return;
        const response = await fetch(request);
        if (response.ok) await cacheDynamicResponse(request, response);
      })
    ));
  }
});

// Exported for unit testing the dynamic cache bound/eviction logic in isolation;
// has no effect on the service worker's runtime behavior in the browser.
export { cacheDynamicResponse, MAX_DYNAMIC_CACHE_ENTRIES, DYNAMIC_CACHE };
