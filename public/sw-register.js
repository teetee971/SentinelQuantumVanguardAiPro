/* Sentinel Quantum Vanguard AI Pro — service worker registration */
(() => {
  'use strict';

  if (!('serviceWorker' in navigator)) return;

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/public/sw.js').catch(() => {
      // Service-worker registration is an optional enhancement; page operation
      // must remain unaffected when registration is unavailable.
    });
  }, { once: true });
})();
