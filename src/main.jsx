import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";

function App() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-900 via-black to-gray-950 text-white flex flex-col items-center justify-center p-8">
      <header className="fixed top-0 w-full bg-gray-900/30 backdrop-blur-md border-b border-gray-800 shadow-lg py-3 flex justify-center">
        <h1 className="text-xl font-semibold tracking-widest text-cyan-400 drop-shadow-md">
          🛰️ Sentinel Quantum Vanguard AI Pro
        </h1>
      </header>

      <main className="flex flex-col items-center justify-center mt-24 text-center space-y-6">
        <h2 className="text-4xl font-bold text-cyan-300 drop-shadow-lg">
          Vanguard Core Online
        </h2>
        <p className="text-gray-400 max-w-md">
          Analyse comportementale, auto-réparation et supervision IA en temps
          réel activées.
        </p>

        <div className="mt-10 w-full max-w-md bg-gray-900/40 backdrop-blur-lg border border-cyan-800/40 rounded-2xl shadow-xl p-6 text-left">
          <h3 className="text-cyan-400 font-medium mb-2">
            Journal en direct :
          </h3>
          <div className="text-sm text-gray-300 space-y-1 font-mono">
            <p>[✓] Réseau IA Sentinel connecté</p>
            <p>[✓] Agents QuantumFailoverAI & FireGuard opérationnels</p>
            <p>[✓] Sécurité Cloudflare et Firebase actives</p>
            <p>[•] Surveillance continue en cours...</p>
          </div>
        </div>
      </main>

      <footer className="mt-auto py-4 text-xs text-gray-500">
        © 2025 Sentinel Quantum Vanguard AI Pro
      </footer>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);