/**
 * Sentinel Quantum Vanguard AI Pro - Service Worker
 * Cache strategy: cache-first for static assets, network-first for pages.
 * Cache schema: 2.0.0
 */

const CACHE_VERSION = 'sentinel-v2.0.0';
const STATIC_CACHE = `${CACHE_VERSION}-static`;
const DYNAMIC_CACHE = `${CACHE_VERSION}-dynamic`;

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
      const cache = await caches.open(DYNAMIC_CACHE);
      await cache.put(request, response.clone());
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
      const cache = await caches.open(DYNAMIC_CACHE);
      await cache.put(request, response.clone());
    }
    return response;
  } catch {
    const cached = await caches.match(request);
    return cached || offlineResponse();
  }
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
    event.waitUntil(caches.open(DYNAMIC_CACHE).then((cache) => cache.addAll(event.data.urls)));
  }
});
