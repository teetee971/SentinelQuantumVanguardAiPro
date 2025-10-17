import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// ⚙️ Configuration Vite optimisée pour Cloudflare Pages + React + Tailwind
export default defineConfig({
  plugins: [react()],
  css: {
    postcss: "./postcss.config.js",
  },
  build: {
    outDir: "dist",
    sourcemap: false,
    chunkSizeWarningLimit: 1000,
  },
  resolve: {
    alias: {
      "@": "/src",
    },
  },
  server: {
    port: 5173,
    host: true,
  },
});
