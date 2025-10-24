/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'sentinel-dark': '#0a0f1c',
        'sentinel-glow': '#00ffff',
        'sentinel-accent': '#39e3ff',
      },
      backgroundImage: theme => ({
        'sentinel-gradient': "linear-gradient(135deg, #0a0f1c 0%, #051933 40%, #002f47 80%)",
      }),
    },
  },
  plugins: [],
}
