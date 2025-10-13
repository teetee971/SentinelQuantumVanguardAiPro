import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/postcss'
import autoprefixer from 'autoprefixer'

// ✅ Forcer Vite à utiliser PostCSS au lieu de LightningCSS
export default defineConfig({
  plugins: [react()],
  css: {
    postcss: {
      plugins: [tailwindcss(), autoprefixer()],
    },
    transformer: 'postcss',
    devSourcemap: false,
  },
  build: {
    minify: true,
    cssMinify: true,
    sourcemap: false,
    target: 'esnext',
    outDir: 'dist',
  },
})