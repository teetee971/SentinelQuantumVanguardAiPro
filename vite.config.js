import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// 🚀 Détection automatique environnement (local / cloud)
const isTermux = !!process.env.PREFIX?.includes('com.termux');
const isProd = process.env.NODE_ENV === 'production' && !isTermux;

console.log(`🧠 Mode actif : ${isProd ? '🌐 Production (Cloudflare)' : '💻 Local (Termux)'}`);

// ⚙️ Configuration complète Sentinel Quantum Vanguard AI Pro
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'robots.txt'],
      manifest: {
        name: 'Sentinel Quantum Vanguard AI Pro',
        short_name: 'SentinelAI',
        description: 'Cybersécurité IA et bouclier quantique',
        theme_color: '#000000',
        background_color: '#000000',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        orientation: 'portrait-primary',
        icons: [
          {
            src: '/icons/icon-72.png',
            sizes: '72x72',
            type: 'image/png',
          },
          {
            src: '/icons/icon-96.png',
            sizes: '96x96',
            type: 'image/png',
          },
          {
            src: '/icons/icon-144.png',
            sizes: '144x144',
            type: 'image/png',
          },
          {
            src: '/icons/icon-192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: '/icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: '/icons/maskable-icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
        screenshots: [
          {
            src: '/screenshots/preview-dark.png',
            sizes: '1280x720',
            type: 'image/png',
            form_factor: 'wide',
          },
          {
            src: '/screenshots/preview-mobile.png',
            sizes: '720x1280',
            type: 'image/png',
            form_factor: 'narrow',
          },
        ],
      },

      // 🧩 Activation conditionnelle SW (désactivé en local)
      disable: !isProd,
      injectRegister: isProd,
      strategies: isProd ? 'generateSW' : 'injectManifest',
      workbox: isProd
        ? {
            cleanupOutdatedCaches: true,
            clientsClaim: true,
            skipWaiting: true,
            runtimeCaching: [
              {
                urlPattern: /^https:\/\/sentinelquantumvanguardaipro\.pages\.dev\/?.*/,
                handler: 'NetworkFirst',
                options: {
                  cacheName: 'sentinel-cache',
                  expiration: {
                    maxEntries: 100,
                    maxAgeSeconds: 60 * 60 * 24 * 7, // 7 jours
                  },
                },
              },
            ],
          }
        : undefined,
    }),
  ],

  // 📦 Build config Cloudflare
  base: './',
  build: {
    outDir: 'dist',
    sourcemap: false,
    emptyOutDir: true,
  },

  // 🌐 Serveur local
  server: {
    host: true,
    port: 5173,
  },
});
