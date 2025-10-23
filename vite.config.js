// vite.config.js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // enregistrement automatique et mise à jour silencieuse du service worker
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
        icons: [
          {
            src: '/icons/icon-192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: '/icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: '/icons/maskable-icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      },
      // désactive le service‑worker en mode développement
      devOptions: { enabled: false }
    })
  ],
  build: {
    outDir: 'dist',
    sourcemap: false
  },
  server: {
    host: true
  }
});
