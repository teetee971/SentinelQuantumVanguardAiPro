import React, { useState } from "react";
import { motion } from "framer-motion";
import { ShieldAlert, Smartphone, Activity, AlertTriangle, CheckCircle, Scan, Lock, Wifi, Database } from "lucide-react";
import Navbar from "../components/Navbar";

export default function PegasusScan() {
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState("En attente du scan...");
  const [result, setResult] = useState(null);
  const [scanning, setScanning] = useState(false);
  const [detailsVisible, setDetailsVisible] = useState(false);

  const startScan = () => {
    setScanning(true);
    setProgress(0);
    setResult(null);
    setDetailsVisible(false);
    setStatus("🔍 Initialisation du scan IA...");

    const phases = [
      { progress: 15, status: "🔐 Analyse des permissions système..." },
      { progress: 30, status: "📱 Scan des applications installées..." },
      { progress: 45, status: "🌐 Vérification des connexions réseau..." },
      { progress: 60, status: "🧠 Détection comportementale IA..." },
      { progress: 75, status: "🔒 Analyse des fichiers système..." },
      { progress: 90, status: "⚡ Corrélation des données..." },
      { progress: 100, status: "✅ Analyse terminée" }
    ];

    let currentPhase = 0;

    const interval = setInterval(() => {
      if (currentPhase < phases.length) {
        setProgress(phases[currentPhase].progress);
        setStatus(phases[currentPhase].status);
        currentPhase++;
      } else {
        clearInterval(interval);
        setScanning(false);
        
        // Résultat aléatoire pour la démo
        const randomResult = Math.random() > 0.85 
          ? { 
              status: "warning", 
              message: "⚠️ Activité suspecte détectée", 
              details: [
                "📡 Connexion non autorisée détectée",
                "🔓 Permission d'accès microphone inhabituelle",
                "📂 Fichier système modifié récemment"
              ]
            }
          : { 
              status: "safe", 
              message: "✅ Appareil sécurisé", 
              details: [
                "✔️ Aucune trace de Pegasus détectée",
                "✔️ Permissions système normales",
                "✔️ Connexions réseau sécurisées",
                "✔️ Comportement applicatif standard"
              ]
            };
        
        setResult(randomResult);
        setDetailsVisible(true);
      }
    }, 800);
  };

  return (
    <>
      <Navbar />
      <section className="min-h-screen bg-black text-white pt-20 px-6 md:px-12 pb-12">
        <div className="max-w-5xl mx-auto">
          {/* En-tête */}
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7 }}
            className="text-center mb-12"
          >
            <div className="flex items-center justify-center mb-4">
              <ShieldAlert className="w-16 h-16 text-red-500 mr-4" />
              <h1 className="text-4xl md:text-5xl font-bold text-blue-400">
                Pegasus Scan IA
              </h1>
            </div>
            <p className="text-gray-400 text-lg max-w-3xl mx-auto">
              Détection avancée d'espionnage mobile — Analyse comportementale par intelligence artificielle
            </p>
          </motion.div>

          {/* Carte principale de scan */}
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="bg-gradient-to-br from-blue-950/40 to-blue-900/20 border border-blue-800 rounded-2xl p-8 shadow-2xl mb-8"
          >
            {/* Icône et statut */}
            <div className="flex flex-col items-center mb-8">
              <motion.div
                animate={scanning ? { rotate: 360 } : {}}
                transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                className="mb-6"
              >
                <Smartphone className="w-24 h-24 text-blue-400" />
              </motion.div>

              <h2 className="text-2xl font-semibold text-blue-300 mb-2">
                {status}
              </h2>

              {/* Barre de progression */}
              {scanning && (
                <div className="w-full max-w-md mt-6">
                  <div className="w-full bg-gray-800 rounded-full h-3 overflow-hidden">
                    <motion.div
                      className="h-full bg-gradient-to-r from-blue-500 to-cyan-400"
                      style={{ width: `${progress}%` }}
                      transition={{ duration: 0.3 }}
                    />
                  </div>
                  <p className="text-center text-gray-400 text-sm mt-2">{progress}%</p>
                </div>
              )}
            </div>

            {/* Bouton de scan */}
            {!scanning && !result && (
              <motion.button
                onClick={startScan}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="mx-auto block px-8 py-4 bg-gradient-to-r from-blue-600 to-cyan-500 text-white font-bold rounded-xl shadow-lg hover:shadow-blue-500/50 transition-all"
              >
                <Scan className="inline mr-2" />
                Lancer le scan Pegasus
              </motion.button>
            )}

            {/* Résultats du scan */}
            {result && detailsVisible && (
              <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.6 }}
                className={`mt-8 p-6 rounded-xl border-2 ${
                  result.status === "warning"
                    ? "bg-red-950/30 border-red-600"
                    : "bg-green-950/30 border-green-600"
                }`}
              >
                <div className="flex items-center mb-4">
                  {result.status === "warning" ? (
                    <AlertTriangle className="w-10 h-10 text-red-500 mr-3" />
                  ) : (
                    <CheckCircle className="w-10 h-10 text-green-500 mr-3" />
                  )}
                  <h3 className="text-2xl font-bold">{result.message}</h3>
                </div>

                <div className="space-y-2 mb-6">
                  {result.details.map((detail, i) => (
                    <motion.p
                      key={i}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: i * 0.1 }}
                      className="text-gray-300"
                    >
                      {detail}
                    </motion.p>
                  ))}
                </div>

                <button
                  onClick={() => {
                    setResult(null);
                    setProgress(0);
                    setStatus("En attente du scan...");
                  }}
                  className="px-6 py-2 bg-blue-700 hover:bg-blue-600 rounded-lg transition"
                >
                  Relancer le scan
                </button>
              </motion.div>
            )}
          </motion.div>

          {/* Indicateurs de sécurité */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 0.6 }}
            className="grid md:grid-cols-3 gap-6 mb-8"
          >
            {[
              { icon: Lock, label: "Chiffrement", value: "AES-256" },
              { icon: Wifi, label: "Connexions", value: scanning ? "Analyse..." : "Standby" },
              { icon: Database, label: "Base de données", value: "Sentinel AI" }
            ].map((item, i) => (
              <div
                key={i}
                className="bg-blue-950/30 border border-blue-800 rounded-xl p-4 text-center"
              >
                <item.icon className="w-8 h-8 text-blue-400 mx-auto mb-2" />
                <p className="text-gray-400 text-sm">{item.label}</p>
                <p className="text-blue-300 font-semibold">{item.value}</p>
              </div>
            ))}
          </motion.div>

          {/* Informations */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.8 }}
            className="bg-blue-950/20 border border-blue-800 rounded-xl p-6"
          >
            <h3 className="text-xl font-semibold text-blue-300 mb-3">
              À propos du scan Pegasus IA
            </h3>
            <p className="text-gray-400 mb-2">
              Le module <strong className="text-blue-400">Pegasus Scan IA</strong> utilise des algorithmes 
              d'apprentissage profond pour détecter les traces de logiciels espions comme Pegasus, NSO Group, 
              et autres outils de surveillance avancés.
            </p>
            <p className="text-gray-400">
              ✅ Analyse comportementale • ✅ Détection des anomalies réseau • ✅ Scan des permissions système
            </p>
          </motion.div>
        </div>
      </section>
    </>
  );
}
