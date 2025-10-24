/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        "sentinel-dark": "#0a0f1c",
        "sentinel-accent": "#39e3ff",
        "sentinel-glow": "#00ffff",
      },
    },
  },
  plugins: [],
};
