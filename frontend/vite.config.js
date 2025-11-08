import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Custom plugin to add preload hints
function htmlPlugin() {
  return {
    name: 'html-transform',
    transformIndexHtml(html) {
      // Add DNS prefetch and preconnect for external resources
      const preconnectLinks = `
    <!-- DNS prefetch and preconnect for performance -->
    <link rel="dns-prefetch" href="https://cdn.jsdelivr.net" />
    <link rel="preconnect" href="https://cdn.jsdelivr.net" crossorigin />
  `
      return html.replace('</title>', `</title>${preconnectLinks}`)
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), htmlPlugin()],
  build: {
    // Enable minification with esbuild (default, faster than terser)
    minify: 'esbuild',
    // Enable CSS code splitting
    cssCodeSplit: true,
    // Chunk size optimization
    rollupOptions: {
      output: {
        manualChunks: {
          // Split vendor chunks
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'three-vendor': ['three', '@react-three/fiber', '@react-three/drei'],
          'ui-vendor': ['framer-motion', 'lucide-react'],
        },
        // Asset file naming
        assetFileNames: 'assets/[name]-[hash][extname]',
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js',
      },
    },
    // Increase chunk size warning limit
    chunkSizeWarningLimit: 600,
  },
})
