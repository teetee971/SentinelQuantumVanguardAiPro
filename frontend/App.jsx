import React from "react";

function App() {
  return (
    <div className="min-h-screen flex flex-col bg-gradient-to-br from-gray-900 via-[#0a192f] to-[#010409] text-cyan-300 font-inter">
      {/* --- Menu glass --- */}
      <header className="fixed top-0 w-full backdrop-blur-xl bg-white/5 border-b border-white/10 flex items-center justify-between px-6 py-4 z-50">
        <div className="flex items-center space-x-3">
          <img
            src="/logo.svg"
            alt="Sentinel Logo"
            className="w-10 h-10 drop-shadow-[0_0_15px_#00ffff70]"
          />
          <span className="text-lg font-semibold tracking-wide text-cyan-300">
            Sentinel Quantum Vanguard AI Pro
          </span>
        </div>
        <nav className="hidden md:flex space-x-6 text-gray-300">
          <a href="/pegasus-scan" className="hover:text-cyan-400 transition">
            Pegasus Scan
          </a>
          <a href="/journal" className="hover:text-cyan-400 transition">
            Journal
          </a>
          <a href="/telechargement" className="hover:text-cyan-400 transition">
            Téléchargement
          </a>
          <a href="/admin" className="hover:text-cyan-400 transition">
            Admin
          </a>
        </nav>
      </header>

      {/* --- Hero principal --- */}
      <main className="flex-1 flex flex-col justify-center items-center text-center px-6 pt-28">
        <div className="relative p-10 rounded-3xl backdrop-blur-2xl bg-white/5 border border-cyan-500/20 shadow-[0_0_50px_#00ffff20]">
          <h1 className="text-5xl md:text-6xl font-bold mb-4 drop-shadow-[0_0_25px_#00ffff70]">
            Quantum-Grade Security Intelligence
          </h1>
          <p className="text-gray-400 max-w-2xl mx-auto">
            L’alliance du calcul quantique et de l’intelligence artificielle
            pour une cybersécurité auto-réparatrice, prédictive et totalement
            autonome.
          </p>
          <button className="mt-8 px-6 py-3 rounded-xl bg-cyan-500/20 hover:bg-cyan-400/30 border border-cyan-400/40 text-cyan-200 transition">
            🚀 Lancer le Scan IA
          </button>

          {/* --- Effet halo Quantum --- */}
          <div className="absolute -z-10 inset-0 rounded-3xl bg-gradient-to-r from-cyan-500/20 via-transparent to-cyan-500/20 blur-3xl"></div>
        </div>
      </main>

      {/* --- Footer --- */}
      <footer className="mt-auto py-6 text-center text-gray-500 border-t border-white/10 backdrop-blur-xl bg-white/5">
        <p>
          © 2025 Sentinel Quantum Vanguard AI Pro —{" "}
          <span className="text-cyan-400">Quantum Secured</span>
        </p>
        <div className="text-xs mt-1">
          Déploiement autonome IA supervisé
        </div>
      </footer>
    </div>
  );
}

export default App;
