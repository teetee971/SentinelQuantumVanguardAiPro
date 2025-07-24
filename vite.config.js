import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/ia': 'http://localhost:5001' // Pour tests locaux
    }
  }
})