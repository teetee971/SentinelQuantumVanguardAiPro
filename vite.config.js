import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from 'tailwindcss'
import autoprefixer from 'autoprefixer'

// ✅ Vite config 100 % compatible Termux + Firebase
export default defineConfig({
  plugins: [react()],
  css: {
    transformer: 'postcss',
    postcss: {
      plugins: [tailwindcss(), autoprefixer()],
    },
    lightningcss: false, // ⚡️ Désactive totalement LightningCSS
  },
  optimizeDeps: {
    include: ['firebase/app', 'firebase/firestore'],
  },
  build: {
    target: 'esnext',
    outDir: 'dist',
    minify: 'esbuild',
  },
})
