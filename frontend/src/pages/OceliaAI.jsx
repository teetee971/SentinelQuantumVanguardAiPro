import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { PhoneIncoming, ShieldCheck, AlertTriangle, Volume2 } from "lucide-react";
import { collection, addDoc } from "firebase/firestore";
import { db } from "../firebase/config";
import Navbar from "../components/Navbar";

export default function OceliaAI() {
  const [status, setStatus] = useState("📞 Appel entrant détecté...");
  const [analyzing, setAnalyzing] = useState(false);
  const [analysis, setAnalysis] = useState(null);

  const startInteraction = async () => {
    setStatus("🤖 OCELIA : Décrochage IA en cours...");
    setAnalyzing(true);
    setAnalysis(null);

    // Log call start
    try {
      await addDoc(collection(db, "ocelia_calls"), {
        timestamp: new Date().toISOString(),
        status: "call_started",
        device: navigator.userAgent,
      });
    } catch (error) {
      console.error("Erreur Firestore:", error);
    }

    // Simule le décrochage + analyse IA vocale
    setTimeout(() => {
      setStatus("🎧 OCELIA : Bonjour, ici Sentinel Quantum AI. Veuillez indiquer le motif de votre appel...");
    }, 1500);

    setTimeout(async () => {
      const randomScenario = Math.random();
      let analysisResult;
      
      if (randomScenario < 0.4) {
        analysisResult = {
          type: "Appel suspect",
          risk: "Élevé",
          response: "OCELIA : Cet appel présente un risque de fraude. L'appel a été bloqué automatiquement.",
          color: "text-red-400",
          icon: <AlertTriangle className="inline w-5 h-5 text-red-400 mr-2" />,
          category: "fraud"
        };
      } else if (randomScenario < 0.8) {
        analysisResult = {
          type: "Appel légitime",
          risk: "Faible",
          response: "OCELIA : Appel authentifié, transfert en cours vers l'utilisateur.",
          color: "text-green-400",
          icon: <ShieldCheck className="inline w-5 h-5 text-green-400 mr-2" />,
          category: "legit"
        };
      } else {
        analysisResult = {
          type: "Appel indéterminé",
          risk: "Modéré",
          response: "OCELIA : Analyse comportementale en cours... aucune anomalie détectée pour l'instant.",
          color: "text-yellow-300",
          icon: <Volume2 className="inline w-5 h-5 text-yellow-300 mr-2" />,
          category: "moderate"
        };
      }

      setAnalysis(analysisResult);
      setStatus("🧠 Analyse IA terminée.");
      setAnalyzing(false);

      // Log call result
      try {
        await addDoc(collection(db, "ocelia_calls"), {
          timestamp: new Date().toISOString(),
          status: "call_completed",
          result: analysisResult.type,
          risk: analysisResult.risk,
          category: analysisResult.category,
          device: navigator.userAgent,
        });
      } catch (error) {
        console.error("Erreur Firestore:", error);
      }
    }, 5000);
  };

  return (
    <>
      <Navbar />
      <section className="min-h-screen bg-black text-white flex flex-col items-center justify-center p-6 pt-24">
        <motion.div
          className="max-w-md w-full text-center border border-blue-800 bg-blue-950/20 rounded-2xl p-6 shadow-lg"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
        >
          <motion.div
            animate={{ scale: analyzing ? [1, 1.1, 1] : 1 }}
            transition={{ repeat: analyzing ? Infinity : 0, duration: 2 }}
          >
            <PhoneIncoming className="w-12 h-12 text-blue-400 mx-auto mb-4" />
          </motion.div>
          
          <h1 className="text-2xl font-bold text-blue-400 mb-2">🎙️ OCELIA Sentinel – IA Vocale Défensive</h1>
          <p className="text-gray-400 mb-6">
            Assistant vocal intelligent de cybersécurité – filtrage et réponse automatisée aux appels suspects.
          </p>

          {!analyzing && !analysis && (
            <motion.button
              onClick={startInteraction}
              className="bg-blue-600 hover:bg-blue-700 px-6 py-3 rounded-xl text-white font-semibold transition-all"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              📞 Décrocher via OCELIA
            </motion.button>
          )}

          <p className="text-sm text-gray-400 mt-4">{status}</p>

          {analyzing && (
            <motion.div
              className="mt-6 h-2 w-full bg-gray-800 rounded-full overflow-hidden"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              <motion.div
                className="h-full bg-blue-500"
                initial={{ width: "0%" }}
                animate={{ width: "100%" }}
                transition={{ duration: 5, ease: "linear" }}
              />
            </motion.div>
          )}

          {analysis && (
            <motion.div
              className={`mt-6 p-4 rounded-xl border border-blue-800 bg-blue-950/30 ${analysis.color}`}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8 }}
            >
              <div className="flex flex-col items-center">
                {analysis.icon}
                <p className="font-semibold">{analysis.type}</p>
                <p className="text-sm mt-2">{analysis.response}</p>
                <p className="text-xs text-gray-500 mt-2">Niveau de risque : {analysis.risk}</p>
              </div>
              
              <motion.button
                onClick={() => {
                  setAnalysis(null);
                  setStatus("📞 Appel entrant détecté...");
                }}
                className="mt-4 bg-blue-900/50 hover:bg-blue-800/50 px-4 py-2 rounded-lg text-sm transition-all"
                whileHover={{ scale: 1.05 }}
              >
                Nouvelle simulation
              </motion.button>
            </motion.div>
          )}
        </motion.div>

        <p className="text-gray-500 text-xs mt-8">
          Simulation IA — OCELIA Sentinel Quantum Vanguard AI Pro
        </p>
      </section>
    </>
  );
}
