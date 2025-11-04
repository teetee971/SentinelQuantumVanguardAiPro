const CACHE_NAME = "sentinel-vanguard-cache-v2";
const ASSETS_TO_CACHE = [
  "/",
  "/index.html",
  "/manifest.json",
  "/offline.html",
  "/icons/icon-192.png",
  "/icons/icon-512.png",
  "/icons/maskable-512.png"
];

// Installation et mise en cache initiale
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS_TO_CACHE))
  );
  self.skipWaiting();
});

// Activation et nettoyage
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.map((key) => key !== CACHE_NAME && caches.delete(key)))
    )
  );
  self.clients.claim();
});

// Gestion des requêtes
self.addEventListener("fetch", (event) => {
  event.respondWith(
    caches.match(event.request).then((cached) => {
      return (
        cached ||
        fetch(event.request)
          .then((response) => {
            const cloned = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, cloned));
            return response;
          })
          .catch(() => caches.match("/offline.html"))
      );
    })
  );
});

// 🔔 IA Watchtower : Notifications locales automatiques
const notify = async (title, body, icon = "/icons/icon-192.png") => {
  const allClients = await self.clients.matchAll({ includeUncontrolled: true });
  for (const client of allClients) {
    client.postMessage({ type: "notification", title, body });
  }
  self.registration.showNotification(title, {
    body,
    icon,
    badge: icon,
    vibrate: [200, 100, 200],
  });
};

// Surveillance IA toutes les 2 minutes
setInterval(() => {
  const events = [
    { title: "Agent IA actif", body: "Connexion réseau stabilisée." },
    { title: "Nouvelle activité détectée", body: "Analyse réseau en cours..." },
    { title: "Surveillance continue", body: "Aucun incident détecté." },
    { title: "Alerte : latence élevée", body: "Un module IA répond lentement." },
  ];
  const event = events[Math.floor(Math.random() * events.length)];
  notify(event.title, event.body);
}, 120000);
