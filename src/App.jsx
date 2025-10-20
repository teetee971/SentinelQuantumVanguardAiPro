import React, { useEffect, useState } from "react";

let MotionDiv = ({ children, ...props }) => <div {...props}>{children}</div>;
try {
  const { motion } = require("framer-motion");
  MotionDiv = motion.div;
} catch (e) {
  console.warn("⚠️ framer-motion non présent, mode statique utilisé.");
}

export default function App() {
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const t = setTimeout(() => setLoading(false), 1000);
    return () => clearTimeout(t);
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-black text-cyan-400 text-2xl font-bold">
        Chargement de Sentinel Quantum Vanguard AI Pro…
      </div>
    );
  }

  return (
    <div className="relative min-h-screen overflow-hidden bg-[#06090f] text-gray-100 font-sans">
      {/* --- Fond holographique plasma --- */}
      <div className="absolute inset-0 overflow-hidden -z-10">
        <div className="absolute top-[-20%] left-[-10%] w-[80vw] h-[80vw] bg-cyan-500/20 rounded-full blur-[120px] animate-pulse"></div>
        <div className="absolute bottom-[-25%] right-[-15%] w-[90vw] h-[90vw] bg-purple-500/20 rounded-full blur-[140px] animate-pulse"></div>
      </div>

      {/* Halo animé */}
      <MotionDiv
        initial={{ opacity: 0 }}
        animate={{ opacity: [0.1, 0.4, 0.1], scale: [0.8, 1.2, 0.8] }}
        transition={{ duration: 6, repeat: Infinity }}
        className="absolute top-6 left-6 w-16 h-16 rounded-full bg-cyan-400/40 blur-xl shadow-[0_0_40px_#22d3ee]"
      />

      {/* En-tête */}
      <header className="p-6 text-center relative z-10">
        <h1 className="text-3xl md:text-4xl font-extrabold text-cyan-300 drop-shadow-lg">
          Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-gray-400 mt-2">
          “La sécurité du futur, dès aujourd’hui”
        </p>
        <nav className="flex justify-center gap-6 mt-4 text-cyan-400 font-medium">
          <a href="#">Modules</a>
          <a href="#">Actualités</a>
          <a href="#">Blog</a>
          <a href="#">Télécharger</a>
          <select className="bg-transparent border border-cyan-600 rounded px-2 text-sm">
            <option>FR</option>
            <option>EN</option>
          </select>
        </nav>
      </header>

      {/* Contenu principal */}
      <main className="max-w-3xl mx-auto px-6 pb-20 relative z-10">
        <h2 className="text-2xl font-bold text-cyan-300 mb-4">Modules actifs</h2>
        <div className="space-y-6 text-gray-300">
          <section>
            <h3 className="text-xl font-semibold text-white">IA prédictive</h3>
            <p>Analyse comportementale et détection de menaces en temps réel.</p>
          </section>
          <section>
            <h3 className="text-xl font-semibold text-white">Quantum Shield</h3>
            <p>Bouclier de cybersécurité quantique.</p>
          </section>
          <section>
            <h3 className="text-xl font-semibold text-white">Scanner OSINT</h3>
            <p>
              Analyse des sources ouvertes, profils suspects et signaux faibles.
            </p>
          </section>
        </div>
      </main>

      {/* Pied de page */}
      <footer className="text-center text-gray-500 py-6 border-t border-gray-800 text-sm relative z-10">
        © {new Date().getFullYear()} Sentinel Quantum Vanguard AI Pro — Tous droits réservés
      </footer>
    </div>
  );
}