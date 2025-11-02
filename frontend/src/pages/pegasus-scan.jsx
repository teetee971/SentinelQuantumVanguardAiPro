import React, { useState, useEffect, useRef } from "react";
import { motion } from "framer-motion";
import { Smartphone, Activity } from "lucide-react";

export default function PegasusScan() {
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState("En attente du scan...");
  const [result, setResult] = useState(null);
  const [scanning, setScanning] = useState(false);
  const intervalRef = useRef(null);

  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  const startScan = () => {
    setScanning(true);
    setProgress(0);
    setStatus("Analyse IA en cours...");
    intervalRef.current = setInterval(() => {
      setProgress((p) => {
        if (p >= 100) {
          clearInterval(intervalRef.current);
          setScanning(false);
          const randomResult = Math.random() > 0.85 ? "Risque détecté ⚠️" : "Appareil sécurisé ✅";
          setResult(randomResult);
          setStatus("Analyse terminée.");
          return 100;
        }
        return p + 2;
      });
    }, 100);
  };

  return (
    <section className="min-h-screen bg-black text-white flex flex-col items-center justify-center p-6">
      <div className="max-w-md w-full text-center border border-blue-800 bg-blue-950/20 rounded-2xl p-6 shadow-lg">
        <Smartphone className="w-12 h-12 text-blue-400 mx-auto mb-4" />
        <h1 className="text-2xl font-bold text-blue-400 mb-2">🛰️ Pegasus Scan IA</h1>
        <p className="text-gray-400 mb-6">
          Analysez votre téléphone pour détecter d'éventuels logiciels espions ou activités suspectes.
        </p>

        {!scanning && (
          <button
            onClick={startScan}
            className="bg-blue-600 hover:bg-blue-700 px-6 py-3 rounded-xl text-white font-semibold transition-all"
          >
            🔍 Lancer le scan IA
          </button>
        )}

        {scanning && (
          <div className="w-full bg-gray-800 rounded-full h-3 mt-4 overflow-hidden">
            <motion.div
              className="h-full bg-blue-500"
              initial={{ width: 0 }}
              animate={{ width: `${progress}%` }}
              transition={{ duration: 0.2 }}
            />
          </div>
        )}

        <p className="text-sm text-gray-400 mt-4">{status}</p>
        {result && (
          <motion.div
            className="mt-6 p-4 rounded-xl border border-blue-800 bg-blue-950/30"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <Activity className="w-6 h-6 text-blue-400 mx-auto mb-2" />
            <p className="text-lg font-semibold">{result}</p>
          </motion.div>
        )}
      </div>

      <p className="text-gray-500 text-xs mt-8">
        Analyse simulée — version de démonstration Sentinel Quantum Vanguard AI Pro
      </p>

      {/* Navigation */}
      <div className="mt-8 flex gap-4">
        <a
          href="/"
          className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
        >
          ← Retour à l'accueil
        </a>
      </div>
    </section>
  );
}
