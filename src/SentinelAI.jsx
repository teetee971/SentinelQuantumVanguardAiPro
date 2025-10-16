import { useState } from "react";
import "./index.css";

export default function SentinelAI() {
  const [status, setStatus] = useState("online");

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-900 to-black text-white flex flex-col items-center justify-center font-sans">
      <h1 className="text-4xl font-bold mb-6 tracking-wide">🛡️ Sentinel Quantum Vanguard AI Pro</h1>
      <p className="text-gray-300 mb-2">Statut du réseau IA : <span className="text-green-400">{status}</span></p>
      <p className="text-gray-400 mb-8 text-center max-w-lg">
        Infrastructure auto-réparatrice, surveillance temps-réel et redéploiement autonome.
      </p>

      <div className="flex space-x-4">
        <button
          onClick={() => setStatus("online")}
          className="bg-green-600 hover:bg-green-700 px-4 py-2 rounded-lg transition"
        >
          Démarrer
        </button>
        <button
          onClick={() => setStatus("recovery")}
          className="bg-yellow-600 hover:bg-yellow-700 px-4 py-2 rounded-lg transition"
        >
          Reprise automatique
        </button>
      </div>

      <footer className="absolute bottom-6 text-gray-500 text-sm">
        Sentinel AI © 2025 – Quantum Systems
      </footer>
    </div>
  );
}