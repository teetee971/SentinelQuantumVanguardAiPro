import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

// === Sentinel Quantum Vanguard AI Pro ===
// ✅ Mode full autonome PWA + Build + CI/CD + Cloudflare-ready

const isProd = process.env.NODE_ENV === 'production'

export default defineConfig({
  root: '.',
  base: './',
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'auto',
      disable: !isProd,
      strategies: 'generateSW',
      workbox: {
        // ✅ Correction Termux : génération safe en répertoire temporaire
        swDest: 'dist/sw.js',
        cleanupOutdatedCaches: true,
        skipWaiting: true,
        clientsClaim: true,
        navigateFallback: '/index.html',
        globPatterns: ['**/*.{js,css,html,png,svg,ico,webmanifest}']
      },
      manifest: {
        name: 'Sentinel Quantum Vanguard AI Pro',
        short_name: 'SentinelAI',
        description: 'Bouclier IA quantique - cybersécurité en temps réel',
        theme_color: '#000000',
        background_color: '#000000',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/maskable-icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
        ]
      },
      devOptions: { enabled: false }
    })
  ],

  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: { react: ['react', 'react-dom'] }
      }
    }
  },

  server: {
    host: true,
    port: 5173,
    open: false
  },

  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  },

  optimizeDeps: {
    include: ['react', 'react-dom']
  }
})
