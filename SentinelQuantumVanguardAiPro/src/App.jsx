import React, { useEffect, useState } from "react";

export default function App() {
  const [darkMode, setDarkMode] = useState(
    () =>
      localStorage.getItem("theme") === "dark" ||
      (!("theme" in localStorage) &&
        window.matchMedia("(prefers-color-scheme: dark)").matches)
  );

  useEffect(() => {
    const root = window.document.documentElement;
    if (darkMode) {
      root.classList.add("dark");
      localStorage.setItem("theme", "dark");
    } else {
      root.classList.remove("dark");
      localStorage.setItem("theme", "light");
    }
  }, [darkMode]);

  return (
    <div className="min-h-screen flex flex-col items-center justify-center transition-colors duration-500 bg-white text-gray-900 dark:bg-gray-900 dark:text-white">
      <h1 className="text-3xl font-bold mb-6 text-center">
        Sentinel Quantum Vanguard AI Pro
      </h1>

      <button
        onClick={() => setDarkMode(!darkMode)}
        className="flex items-center gap-2 bg-blue-400 dark:bg-indigo-600 text-black dark:text-white px-5 py-2 rounded-full shadow-md hover:opacity-90 transition-all duration-300"
      >
        {darkMode ? "☀️ Mode clair" : "🌙 Mode sombre"}
      </button>

      <p className="mt-6 opacity-80 text-center max-w-md">
        Interface immersive IA — thème adaptatif et mode sombre automatique.{" "}
        <span className="text-blue-400">React + Tailwind + Vite</span>.
      </p>
    </div>
  );
}
