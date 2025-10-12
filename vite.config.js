import { defineConfig } from 'vite'

export default defineConfig({
  build: {
    rollupOptions: false, // 🔒 empêche tout appel à Rollup
    minify: false,
    target: 'esnext',
  },
  optimizeDeps: {
    esbuildOptions: {
      target: 'esnext',
    },
  },
  server: {
    host: '0.0.0.0',
    port: 3000
  }
})
