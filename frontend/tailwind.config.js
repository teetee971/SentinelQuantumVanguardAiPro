/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx,ts,tsx}"
  ],
  theme: {
    extend: {
      colors: {
        sentinel: {
          dark: "#0a0a0f",
          accent: "#00ffff",
          glow: "#14b8a6",
        },
      },
      backgroundImage: {
        "sentinel-gradient":
          "linear-gradient(135deg, #0a0a0f 0%, #001f2e 40%, #003f4f 70%, #00ffff 100%)",
      },
      animation: {
        glow: "glowPulse 6s ease-in-out infinite",
      },
      keyframes: {
        glowPulse: {
          "0%, 100%": { opacity: 0.9, filter: "brightness(1)" },
          "50%": { opacity: 1, filter: "brightness(1.4)" },
        },
      },
    },
  },
  plugins: [],
};
