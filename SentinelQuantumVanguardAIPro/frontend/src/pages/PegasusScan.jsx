import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";

export default function PegasusScan() {
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState("Initialisation du module Pegasus...");
  const [complete, setComplete] = useState(false);
  const [threatDetected, setThreatDetected] = useState(false);

  useEffect(() => {
    if (progress < 100) {
      const timer = setInterval(() => {
        setProgress((prev) => {
          const next = prev + Math.random() * 10;
          if (next >= 100) {
            clearInterval(timer);
            setProgress(100);
            setTimeout(() => {
              const infected = Math.random() < 0.15; // 15 % de chance simulation
              setThreatDetected(infected);
              setStatus(infected ? "⚠️ Anomalies suspectes détectées." : "✅ Aucun indicateur Pegasus trouvé.");
              setComplete(true);
            }, 1200);
          } else {
            setStatus(randomStatusMessage());
          }
          return next;
        });
      }, 1200);
      return () => clearInterval(timer);
    }
  }, [progress]);

  const randomStatusMessage = () => {
    const messages = [
      "Analyse des ports réseau...",
      "Scan des processus système...",
      "Vérification des permissions root...",
      "Détection d’empreintes Pegasus...",
      "Analyse des librairies dynamiques...",
      "Contrôle des signatures SHA256...",
      "Vérification des certificats SSL...",
      "Analyse du cache mémoire...",
      "Examen des métadonnées suspectes...",
    ];
    return messages[Math.floor(Math.random() * messages.length)];
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "radial-gradient(circle at 20% 20%, #000 0%, #020202 100%)",
        color: "#fff",
        fontFamily: "Inter, sans-serif",
        padding: "6rem 1rem 3rem",
        textAlign: "center",
        overflow: "hidden",
      }}
    >
      <motion.h1
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        style={{
          fontSize: "2rem",
          fontWeight: "700",
          color: "#00ffcc",
          textShadow: "0 0 15px #00ffcc55",
          marginBottom: "1rem",
        }}
      >
        🛰️ Pegasus Scan IA
      </motion.h1>

      <p style={{ color: "#aaa", marginBottom: "2rem" }}>
        Lancement du protocole d’analyse IA avancée — détection des traces de logiciels espions Pegasus.
      </p>

      {/* Radar animé */}
      <div style={{ position: "relative", width: "280px", height: "280px", margin: "2rem auto" }}>
        <div
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: "50%",
            border: "2px solid #00ffcc33",
            boxShadow: "0 0 25px #00ffcc33",
          }}
        ></div>
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ repeat: Infinity, duration: 2, ease: "linear" }}
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: "50%",
            background:
              "conic-gradient(from 0deg, rgba(0,255,204,0.4) 0deg, transparent 90deg, transparent 360deg)",
          }}
        ></motion.div>
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.5, duration: 0.5 }}
          style={{
            position: "absolute",
            inset: "40px",
            borderRadius: "50%",
            border: "1px dashed #00ffcc33",
          }}
        ></motion.div>
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 1, duration: 0.5 }}
          style={{
            position: "absolute",
            inset: "80px",
            borderRadius: "50%",
            border: "1px dashed #00ffcc33",
          }}
        ></motion.div>
        <motion.div
          animate={{ scale: [0.9, 1.1, 0.9] }}
          transition={{ repeat: Infinity, duration: 2 }}
          style={{
            position: "absolute",
            inset: "120px",
            borderRadius: "50%",
            background: "#00ffcc22",
          }}
        ></motion.div>
      </div>

      {/* Barre de progression */}
      <div style={{ width: "80%", maxWidth: "500px", margin: "2rem auto" }}>
        <div
          style={{
            height: "14px",
            background: "#111",
            borderRadius: "8px",
            overflow: "hidden",
            boxShadow: "0 0 10px #00ffcc44",
          }}
        >
          <motion.div
            initial={{ width: "0%" }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.5 }}
            style={{
              height: "100%",
              background:
                progress >= 100
                  ? threatDetected
                    ? "#ff4444"
                    : "#00ffcc"
                  : "#00ffccaa",
            }}
          ></motion.div>
        </div>
        <p style={{ marginTop: "1rem", color: "#aaa" }}>
          {progress < 100 ? `${Math.round(progress)} % — ${status}` : status}
        </p>
      </div>

      {/* Résultat final */}
      {complete && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          style={{
            backgroundColor: "#0a0a0a",
            borderRadius: "12px",
            padding: "2rem 1rem",
            border: `1px solid ${threatDetected ? "#ff4444" : "#00ffcc"}`,
            boxShadow: `0 0 25px ${threatDetected ? "#ff444433" : "#00ffcc33"}`,
            width: "85%",
            maxWidth: "600px",
            margin: "2rem auto",
          }}
        >
          <h2
            style={{
              color: threatDetected ? "#ff4444" : "#00ffcc",
              fontWeight: "700",
              marginBottom: "1rem",
            }}
          >
            {threatDetected ? "🚨 Anomalies détectées" : "✅ Appareil sécurisé"}
          </h2>
          <p style={{ color: "#ccc" }}>
            {threatDetected
              ? "Des empreintes numériques inhabituelles ont été identifiées. Il est recommandé d’exécuter un scan complet Sentinel DeepSecure."
              : "Aucun indicateur de compromission Pegasus n’a été trouvé. Le système est sain et sous surveillance IA."}
          </p>
        </motion.div>
      )}

      {/* Bouton relancer */}
      {complete && (
        <motion.button
          whileHover={{ scale: 1.05 }}
          onClick={() => {
            setProgress(0);
            setComplete(false);
            setThreatDetected(false);
            setStatus("Redémarrage du module Pegasus...");
          }}
          style={{
            backgroundColor: "#00ffcc",
            color: "#000",
            border: "none",
            borderRadius: "8px",
            padding: "0.8rem 1.5rem",
            marginTop: "1.5rem",
            fontWeight: "bold",
            cursor: "pointer",
          }}
        >
          🔄 Relancer l’analyse
        </motion.button>
      )}

      {/* Footer */}
      <footer style={{ color: "#555", fontSize: "0.9rem", marginTop: "3rem" }}>
        Sentinel Quantum Vanguard AI Pro © 2025 — Module Pegasus Shield
      </footer>
    </div>
  );
}
