import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: [
        'favicon.ico',
        'robots.txt',
        'apple-touch-icon.png',
        'assets/sentinel_logo.png'
      ],
      manifest: {
        name: 'Sentinel Quantum Vanguard AI Pro',
        short_name: 'Sentinel AI Pro',
        description: 'Système IA avancé de cybersécurité Sentinel Quantum Vanguard AI Pro',
        theme_color: '#0d1117',
        background_color: '#000000',
        display: 'standalone',
        orientation: 'portrait',
        icons: [
          {
            src: '/assets/sentinel_logo.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      }
    })
  ],
  build: { outDir: 'dist' },
  base: './',
})
