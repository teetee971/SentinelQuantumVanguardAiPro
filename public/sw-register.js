/* Sentinel Quantum Vanguard AI Pro — service worker registration */
(() => {
  'use strict';

  if (!('serviceWorker' in navigator)) return;

  const register = () => {
    navigator.serviceWorker.register('/public/sw.js').catch(() => {
      // Service-worker registration is an optional enhancement; page operation
      // must remain unaffected when registration is unavailable.
    });
  };

  const scheduleRegistration = () => {
    if (typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(register, { timeout: 5000 });
    } else {
      window.setTimeout(register, 0);
    }
  };

  window.addEventListener('load', scheduleRegistration, { once: true });
})();
