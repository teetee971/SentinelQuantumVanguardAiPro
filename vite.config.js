import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Configuration Vite pour Sentinel Quantum Vanguard AI Pro
export default defineConfig({
  // base doit correspondre à la racine de votre site déployé
  base: '/',
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',       // enregistre et met à jour automatiquement le service worker en production
      includeAssets: ['favicon.ico', 'robots.txt'],
      manifest: {
        name: 'Sentinel Quantum Vanguard AI Pro',
        short_name: 'SentinelAI',
        description: 'Cybersécurité IA et bouclier quantique',
        theme_color: '#000000',
        background_color: '#000000',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: 'icons/icon-192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: 'icons/maskable-icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      }
    })
  ],
  build: {
    outDir: 'dist',
    sourcemap: false
  },
  server: {
    host: true
  }
})
