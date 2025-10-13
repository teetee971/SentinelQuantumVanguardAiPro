import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import SentinelAI from "./SentinelAI";

function App() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-black via-gray-900 to-cyan-950 text-gray-100 flex flex-col items-center justify-center p-6">
      <header className="mb-8 text-center">
        <h1 className="text-3xl md:text-4xl font-bold text-cyan-400 drop-shadow-lg">
          🛰️ Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-gray-400 mt-2">
          Supervision IA active — Surveillance, diagnostic, et défense autonome
        </p>
      </header>

      <main className="w-full max-w-3xl space-y-8">
        <section className="bg-gray-900/50 p-6 rounded-2xl border border-cyan-700/40 backdrop-blur-lg shadow-lg">
          <h2 className="text-xl font-semibold text-cyan-300 mb-3">
            Journal IA Live
          </h2>
          <SentinelAI />
        </section>

        <footer className="text-center text-sm text-gray-500">
          <p>© 2025 Sentinel Quantum Vanguard AI Pro — Supervision autonome IA</p>
        </footer>
      </main>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);