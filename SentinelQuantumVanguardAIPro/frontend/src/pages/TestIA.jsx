import React, { useState } from "react";
import { motion } from "framer-motion";

export default function TestIA() {
  const [prompt, setPrompt] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState("gpt4");

  const API_BASE = "https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net";

  const endpoints = {
    gpt4: `${API_BASE}/generateText`,
    gemini: `${API_BASE}/gemini`,
    deepl: `${API_BASE}/translate`,
    moderate: `${API_BASE}/moderate`,
  };

  const runTest = async () => {
    if (!prompt.trim()) return alert("Merci d’entrer un texte à analyser.");
    setLoading(true);
    setResult(null);

    try {
      const endpoint = endpoints[mode];
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ input: prompt }),
      });
      const data = await response.json();
      setResult(data);
    } catch (err) {
      setResult({ error: err.message });
    } finally {
      setLoading(false);
    }
  };

  const ModeButton = ({ label, value, color }) => (
    <motion.button
      whileHover={{ scale: 1.05 }}
      onClick={() => setMode(value)}
      style={{
        backgroundColor: mode === value ? color : "#111",
        color: "#fff",
        border: `1px solid ${color}`,
        borderRadius: "8px",
        margin: "0.3rem",
        padding: "0.6rem 1rem",
        cursor: "pointer",
        fontWeight: "bold",
      }}
    >
      {label}
    </motion.button>
  );

  return (
    <div
      style={{
        background: "radial-gradient(circle at 20% 20%, #060606 0%, #000 100%)",
        color: "#fff",
        minHeight: "100vh",
        padding: "6rem 1rem 3rem",
        fontFamily: "Inter, sans-serif",
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
          textAlign: "center",
          textShadow: "0 0 15px #00ffcc55",
          marginBottom: "1rem",
        }}
      >
        🧠 Test IA - Analyse & Génération
      </motion.h1>

      <p style={{ textAlign: "center", color: "#aaa", marginBottom: "2rem" }}>
        Entrez un texte ou une question, sélectionnez un moteur IA, puis lancez le test.
      </p>

      {/* Sélection du moteur */}
      <div style={{ textAlign: "center", marginBottom: "1rem" }}>
        <ModeButton label="GPT-4 (OpenAI)" value="gpt4" color="#00ffcc" />
        <ModeButton label="Gemini (Google)" value="gemini" color="#00ccff" />
        <ModeButton label="DeepL (Traduction)" value="deepl" color="#ffee33" />
        <ModeButton label="Modération IA" value="moderate" color="#ff5555" />
      </div>

      {/* Zone de saisie */}
      <div style={{ textAlign: "center" }}>
        <textarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="Exemple : Analyse ce texte ou traduis-le..."
          style={{
            width: "90%",
            maxWidth: "600px",
            height: "120px",
            background: "#111",
            color: "#fff",
            border: "1px solid #00ffcc33",
            borderRadius: "8px",
            padding: "1rem",
            fontSize: "1rem",
            fontFamily: "Inter, sans-serif",
            resize: "none",
            marginBottom: "1rem",
          }}
        />
      </div>

      {/* Bouton lancer */}
      <div style={{ textAlign: "center" }}>
        <motion.button
          whileHover={{ scale: 1.05 }}
          onClick={runTest}
          disabled={loading}
          style={{
            backgroundColor: "#00ffcc",
            color: "#000",
            border: "none",
            borderRadius: "8px",
            padding: "0.8rem 1.5rem",
            fontWeight: "bold",
            cursor: "pointer",
            fontSize: "1rem",
          }}
        >
          {loading ? "Analyse en cours..." : "🚀 Lancer le test"}
        </motion.button>
      </div>

      {/* Résultat */}
      <div
        style={{
          marginTop: "2rem",
          backgroundColor: "#0a0a0a",
          borderRadius: "10px",
          border: "1px solid #00ffcc33",
          width: "90%",
          maxWidth: "700px",
          marginInline: "auto",
          padding: "1.5rem",
          boxShadow: "0 0 20px #00ffcc22",
        }}
      >
        <h3 style={{ color: "#00ffcc", marginBottom: "0.5rem" }}>Résultat</h3>
        {result ? (
          <pre
            style={{
              whiteSpace: "pre-wrap",
              wordWrap: "break-word",
              backgroundColor: "#111",
              padding: "1rem",
              borderRadius: "8px",
              color: "#fff",
              fontSize: "0.9rem",
              border: "1px solid #222",
              maxHeight: "400px",
              overflowY: "auto",
            }}
          >
            {JSON.stringify(result, null, 2)}
          </pre>
        ) : (
          <p style={{ color: "#777", textAlign: "center" }}>
            Aucun résultat pour le moment.
          </p>
        )}
      </div>

      {/* Footer */}
      <footer
        style={{
          textAlign: "center",
          color: "#666",
          fontSize: "0.9rem",
          padding: "2rem 0 0",
        }}
      >
        <p>Sentinel Quantum Vanguard AI Network © 2025</p>
      </footer>
    </div>
  );
}
