/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: [
    "./index.html",
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        sentinel: {
          bg: "#04070d",
          panel: "#0a101b",
          primary: "#00eaff",
          glow: "#00c8ff",
          neon: "#08f7fe",
          danger: "#ff073a",
        },
      },
      boxShadow: {
        neon: "0 0 25px #00eaff",
        neonSoft: "0 0 15px #00c8ff",
      },
      animation: {
        pulseNeon: "pulseNeon 2s infinite",
        scan: "scan 6s linear infinite",
      },
      keyframes: {
        pulseNeon: {
          "0%,100%": { boxShadow: "0 0 0px #00eaff" },
          "50%": { boxShadow: "0 0 20px #00eaff" },
        },
        scan: {
          "0%": { transform: "translateY(-100%)" },
          "100%": { transform: "translateY(200%)" },
        },
      },
    },
  },
  plugins: [],
};
