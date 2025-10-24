import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Globe, Wifi, AlertTriangle } from "lucide-react";
import { motion } from "framer-motion";

export default function ThreatMap() {
  const navigate = useNavigate();
  const [attacks, setAttacks] = useState([]);
  const [activeScans, setActiveScans] = useState(0);
  const [loading, setLoading] = useState(true);

  // Simulation IA : mise à jour dynamique
  useEffect(() => {
    const attackSources = [
      "Moscou 🇷🇺",
      "Paris 🇫🇷",
      "New York 🇺🇸",
      "Shenzhen 🇨🇳",
      "Tel Aviv 🇮🇱",
      "Berlin 🇩🇪",
      "São Paulo 🇧🇷",
      "Tokyo 🇯🇵",
    ];

    const interval = setInterval(() => {
      const random = Math.floor(Math.random() * attackSources.length);
      const newAttack = {
        id: Date.now(),
        source: attackSources[random],
        target: "Guadeloupe 🇬🇵",
        type:
          Math.random() > 0.5
            ? "Tentative d'intrusion réseau"
            : "Scan IA suspect",
      };
      setAttacks((prev) => [newAttack, ...prev.slice(0, 7)]);
      setActiveScans((prev) => prev + 1);
      setLoading(false);
    }, 2500);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-[#050505] text-gray-200 flex flex-col items-center justify-center relative overflow-hidden">
      {/* Effet globe */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 0.15, scale: [1, 1.05, 1] }}
        transition={{ repeat: Infinity, duration: 10 }}
        className="absolute inset-0 flex items-center justify-center"
      >
        <Globe className="w-72 h-72 text-[#00BFFF]" />
      </motion.div>

      {/* Titre principal */}
      <div className="z-10 text-center space-y-2">
        <h1 className="text-3xl font-bold text-[#00BFFF]">
          Sentinel Threat Map — Global IA Monitor
        </h1>
        <p className="text-gray-400 text-sm">
          Surveillance mondiale en temps réel des menaces IA
        </p>
      </div>

      {/* Stats */}
      <div className="z-10 mt-8 flex space-x-6">
        <div className="bg-[#111] px-4 py-2 rounded-xl border border-[#00BFFF]/30 shadow-lg">
          <Wifi className="inline-block mr-2 text-[#00BFFF]" />
          <span className="text-sm text-gray-300">
            Scans actifs : {activeScans}
          </span>
        </div>
        <div className="bg-[#111] px-4 py-2 rounded-xl border border-[#00BFFF]/30 shadow-lg">
          <AlertTriangle className="inline-block mr-2 text-[#FF5555]" />
          <span className="text-sm text-gray-300">
            Menaces détectées : {attacks.length}
          </span>
        </div>
      </div>

      {/* Journal d'activité */}
      <div className="z-10 mt-10 w-full max-w-md bg-[#0a0a0a] border border-[#00BFFF]/20 rounded-2xl p-4 shadow-lg overflow-y-auto max-h-96">
        {loading ? (
          <p className="text-gray-500 text-center">
            Initialisation du flux IA Sentinel...
          </p>
        ) : (
          attacks.map((a) => (
            <motion.div
              key={a.id}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              className="border-b border-gray-800 py-2"
            >
              <p className="text-sm">
                <span className="text-[#00BFFF] font-semibold">
                  {a.source}
                </span>{" "}
                → <span className="text-[#00FFAA]">{a.target}</span>
              </p>
              <p className="text-xs text-gray-400">{a.type}</p>
            </motion.div>
          ))
        )}
      </div>

      {/* Bouton retour */}
      <button
        onClick={() => navigate("/")}
        className="z-10 mt-8 px-6 py-2 rounded-lg bg-[#00BFFF] hover:bg-[#0099cc] text-black font-semibold transition duration-200"
      >
        ← Retour Console
      </button>

      {/* Footer */}
      <footer className="absolute bottom-4 text-xs text-gray-600">
        © 2025 Sentinel Quantum Vanguard AI Pro — Carte mondiale des menaces
      </footer>
    </div>
  );
}
