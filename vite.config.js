import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// ⚙️ Configuration principale Sentinel Quantum Vanguard AI Pro
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
          }
        ]
      },
      // 🚫 Désactivation locale du Service Worker pour éviter les erreurs Termux
      disable: process.env.TERMUX === 'true' || true,
      injectRegister: false,
      strategies: 'generateSW'
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
