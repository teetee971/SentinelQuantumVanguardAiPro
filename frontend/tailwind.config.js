/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        sentinel: {
          blue: "#0EA5E9",
          dark: "#0A0A0A",
          green: "#22C55E",
          red: "#EF4444",
        },
      },
    },
  },
  plugins: [],
};
