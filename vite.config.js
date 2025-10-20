import { defineConfig } from 'vite';
import postcss from './postcss.config.js';

export default defineConfig({
  css: {
    postcss,
  },
});
