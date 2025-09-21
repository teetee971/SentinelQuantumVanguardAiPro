import { defineConfig } from 'vite'

export default defineConfig({
  base: './',
  server: {
    host: true, // Allow external connections
    port: 5175,  // Default port for the script
    strictPort: false // Allow fallback to other ports if 5175 is busy
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    rollupOptions: {
      output: {
        manualChunks: undefined
      }
    }
  }
})
