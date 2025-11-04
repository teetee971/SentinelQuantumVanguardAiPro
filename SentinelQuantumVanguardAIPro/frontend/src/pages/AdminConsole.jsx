import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";

export default function AdminConsole() {
  const [agents, setAgents] = useState([]);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);

  const API_BASE = "https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net";

  // Liste simulée d’agents IA Sentinel
  const agentList = [
    { id: "QuantumFailoverAI", role: "Gestion du failover et redondance réseau" },
    { id: "FlowFinalizer", role: "Stabilisation des flux et redirections automatiques" },
    { id: "FirebaseDeployExecutor", role: "Exécution autonome des déploiements Firebase" },
    { id: "SessionIntegritySentinel", role: "Surveillance des connexions utilisateurs" },
    { id: "AIRecoveryCommander", role: "Réparation automatique des modules défaillants" },
    { id: "DNSWatcher", role: "Surveillance DNS et propagation HTTPS" },
    { id: "MobileAutoTester", role: "Test des fonctions sur mobile/PWA" },
    { id: "LiveDeploySentinel", role: "Vérification continue des builds & pages Cloudflare" },
  ];

  useEffect(() => {
    simulateAgentStatus();
    const interval = setInterval(simulateAgentStatus, 8000);
    return () => clearInterval(interval);
  }, []);

  const simulateAgentStatus = () => {
    const updated = agentList.map((a) => ({
      ...a,
      status: Math.random() < 0.9 ? "🟢 Actif" : "🔴 Hors ligne",
      latency: (Math.random() * 250 + 30).toFixed(0) + " ms",
      uptime: (Math.random() * 99.9).toFixed(1) + " %",
    }));
    setAgents(updated);
  };

  const restartAgent = (id) => {
    setLoading(true);
    setLogs((prev) => [
      { time: new Date().toLocaleTimeString("fr-FR"), msg: `Redémarrage de ${id}...` },
      ...prev.slice(0, 40),
    ]);
    setTimeout(() => {
      setLogs((prev) => [
        { time: new Date().toLocaleTimeString("fr-FR"), msg: `✅ ${id} redémarré avec succès.` },
        ...prev.slice(0, 40),
      ]);
      simulateAgentStatus();
      setLoading(false);
    }, 2000);
  };

  return (
    <div
      style={{
        background: "radial-gradient(circle at 20% 20%, #050505 0%, #000 100%)",
        color: "#fff",
        minHeight: "100vh",
        fontFamily: "Inter, sans-serif",
        padding: "6rem 1rem 4rem",
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
        🧠 Console Admin IA — Supervision Agents Sentinel
      </motion.h1>

      <p style={{ textAlign: "center", color: "#aaa", marginBottom: "2rem" }}>
        État des agents IA et contrôle manuel des redémarrages autonomes.
      </p>

      {/* Tableau des agents */}
      <div
        style={{
          overflowX: "auto",
          backgroundColor: "#0a0a0a",
          border: "1px solid #00ffcc33",
          borderRadius: "12px",
          width: "95%",
          maxWidth: "900px",
          margin: "auto",
          padding: "1rem",
          boxShadow: "0 0 25px #00ffcc22",
        }}
      >
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid #222", color: "#00ffcc" }}>
              <th style={{ textAlign: "left", padding: "0.7rem" }}>Agent</th>
              <th style={{ textAlign: "left", padding: "0.7rem" }}>Rôle</th>
              <th style={{ textAlign: "center", padding: "0.7rem" }}>Statut</th>
              <th style={{ textAlign: "center", padding: "0.7rem" }}>Latence</th>
              <th style={{ textAlign: "center", padding: "0.7rem" }}>Uptime</th>
              <th style={{ textAlign: "center", padding: "0.7rem" }}>Action</th>
            </tr>
          </thead>
          <tbody>
            {agents.map((agent, idx) => (
              <motion.tr
                key={idx}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: idx * 0.1 }}
                style={{
                  borderBottom: "1px solid #111",
                  color: agent.status === "🟢 Actif" ? "#0f0" : "#f33",
                }}
              >
                <td style={{ padding: "0.7rem", fontWeight: "600", color: "#fff" }}>{agent.id}</td>
                <td style={{ padding: "0.7rem", color: "#ccc" }}>{agent.role}</td>
                <td style={{ textAlign: "center" }}>{agent.status}</td>
                <td style={{ textAlign: "center", color: "#0ff" }}>{agent.latency}</td>
                <td style={{ textAlign: "center", color: "#ff0" }}>{agent.uptime}</td>
                <td style={{ textAlign: "center" }}>
                  <motion.button
                    whileHover={{ scale: 1.05 }}
                    disabled={loading}
                    onClick={() => restartAgent(agent.id)}
                    style={{
                      backgroundColor: "#00ffcc",
                      color: "#000",
                      border: "none",
                      borderRadius: "8px",
                      padding: "0.5rem 0.8rem",
                      cursor: "pointer",
                      fontWeight: "bold",
                    }}
                  >
                    🔄 Redémarrer
                  </motion.button>
                </td>
              </motion.tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Section logs */}
      <div
        style={{
          backgroundColor: "#0a0a0a",
          border: "1px solid #222",
          borderRadius: "10px",
          margin: "3rem auto",
          padding: "1rem",
          width: "90%",
          maxWidth: "800px",
          boxShadow: "0 0 15px #00ffcc22",
        }}
      >
        <h3 style={{ color: "#00ffcc", marginBottom: "1rem" }}>📜 Journal d’activité des agents</h3>
        <div
          style={{
            backgroundColor: "#111",
            borderRadius: "8px",
            padding: "1rem",
            height: "250px",
            overflowY: "auto",
            fontFamily: "monospace",
            fontSize: "0.9rem",
            color: "#aaa",
          }}
        >
          {logs.length > 0 ? (
            logs.map((l, i) => (
              <div key={i} style={{ marginBottom: "0.4rem" }}>
                <span style={{ color: "#00ffcc" }}>{l.time}</span> — {l.msg}
              </div>
            ))
          ) : (
            <p style={{ color: "#555" }}>Aucune activité enregistrée pour le moment.</p>
          )}
        </div>
      </div>

      {/* Footer */}
      <footer
        style={{
          textAlign: "center",
          color: "#555",
          fontSize: "0.9rem",
          marginTop: "2rem",
        }}
      >
        Sentinel Quantum Vanguard AI Pro © 2025 — Console Admin Réseau IA
      </footer>
    </div>
  );
}
