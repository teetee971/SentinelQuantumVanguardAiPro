import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// === 🧠 Configuration Vite Méga Full++ ===
export default defineConfig({
  plugins: [
    react({
      include: '**/*.jsx',
      jsxRuntime: 'automatic',
      babel: {
        plugins: [
          ['@babel/plugin-transform-react-jsx', { runtime: 'automatic' }],
          '@babel/plugin-syntax-jsx',
        ],
      },
    }),

    // === 🌍 Support PWA + offline ===
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'robots.txt', 'apple-touch-icon.png'],
      manifest: {
        name: 'Sentinel Quantum Vanguard AI Pro',
        short_name: 'Sentinel AI',
        description: 'Supervision IA, Globe 3D, Auto-Heal et Monitoring Cloud.',
        theme_color: '#000000',
        background_color: '#000000',
        display: 'standalone',
        orientation: 'portrait',
        icons: [
          {
            src: '/icons/icon-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: '/icons/icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
        ],
      },
      devOptions: {
        enabled: true,
      },
    }),
  ],

  // === ⚙️ Résolution et alias pour imports simplifiés ===
  resolve: {
    alias: {
      '@': '/src',
      '@components': '/src/components',
      '@pages': '/src/pages',
      '@assets': '/public/assets',
    },
  },

  // === ⚡ Serveur local ===
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    open: true,
    cors: true,
  },

  // === 🚀 Build optimisée ===
  build: {
    target: 'esnext',
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: undefined,
      },
    },
  },

  // === 🌐 Configuration Cloudflare Pages ===
  optimizeDeps: {
    include: ['react', 'react-dom', 'three', 'globe.gl', 'firebase/app', 'firebase/firestore'],
  },
});
