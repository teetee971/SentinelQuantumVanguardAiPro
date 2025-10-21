import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// 🧠 Détection automatique environnement (local / cloud)
const isTermux = !!process.env.PREFIX?.includes('com.termux')
const isProd = process.env.NODE_ENV === 'production' && !isTermux

console.log(`🚀 Mode: ${isProd ? 'Production (Cloudflare)' : 'Local (Termux)'}`)

// ⚙️ Configuration Sentinel Quantum Vanguard AI Pro
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
      // 🧩 Activation conditionnelle
      disable: !isProd, // false en prod = SW actif, true en local = désactivé
      injectRegister: isProd,
      strategies: isProd ? 'generateSW' : 'injectManifest',
      workbox: isProd
        ? {
            cleanupOutdatedCaches: true,
            clientsClaim: true,
            skipWaiting: true,
            runtimeCaching: [
              {
                urlPattern: /^https:\/\/sentinelquantumvanguardaipro\.pages\.dev\/.*$/,
                handler: 'NetworkFirst',
                options: {
                  cacheName: 'sentinel-cache',
                  expiration: {
                    maxEntries: 100,
                    maxAgeSeconds: 60 * 60 * 24 * 7 // 7 jours
                  }
                }
              }
            ]
          }
        : undefined
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
