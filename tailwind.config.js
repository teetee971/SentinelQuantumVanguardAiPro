/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'sentinel-dark': '#0a0a0a',
        'sentinel-cyan': '#00ffff',
      },
    },
  },
  plugins: [],
};
