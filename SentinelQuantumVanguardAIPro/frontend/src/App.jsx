import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";
import {
  LineChart,
  Line,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

// ===============================
//  MODULE PRINCIPAL SENTINEL
// ===============================
export default function App() {
  const [view, setView] = useState("home");

  return (
    <div
      style={{
        background: "radial-gradient(circle at 20% 20%, #050505 0%, #000 100%)",
        color: "#fff",
        minHeight: "100vh",
        fontFamily: "Inter, sans-serif",
        overflowX: "hidden",
      }}
    >
      {view === "home" && <Home onSwitch={setView} />}
      {view === "console" && <Console onSwitch={setView} />}
      <IANotifications />
    </div>
  );
}

// ===============================
//  PAGE D’ACCUEIL
// ===============================
function Home({ onSwitch }) {
  const modules = [
    { name: "GPT-4", desc: "Génération de texte avancée", color: "#00ff88" },
    { name: "Gemini", desc: "Analyse contextuelle & vision", color: "#00ccff" },
    { name: "DeepL", desc: "Traduction multilingue IA", color: "#ffee33" },
    { name: "Modération", desc: "Filtrage contenu & sécurité", color: "#ff4444" },
  ];

  return (
    <div style={{ textAlign: "center", padding: "4rem 1rem 2rem" }}>
      <motion.h1
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        style={{
          fontSize: "2.2rem",
          fontWeight: "700",
          color: "#00ffcc",
          textShadow: "0 0 15px #00ffcc55",
        }}
      >
        Sentinel Quantum Vanguard AI Pro
      </motion.h1>
      <p style={{ marginTop: "1rem", color: "#aaa" }}>
        Système de supervision et de protection IA autonome
      </p>
      <div style={{ marginTop: "2rem" }}>
        <ActionButton
          label="Accéder à la Console"
          color="#00ffcc"
          onClick={() => onSwitch("console")}
        />
        <ActionButton
          label="Diagnostics Système"
          color="#00ccff"
          onClick={() => onSwitch("console")}
        />
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit,minmax(220px,1fr))",
          gap: "1rem",
          padding: "2rem 1.5rem",
        }}
      >
        {modules.map((m, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.2 }}
            style={{
              backgroundColor: "#111",
              borderRadius: "10px",
              padding: "1rem",
              border: `1px solid ${m.color}55`,
              boxShadow: `0 0 15px ${m.color}33`,
            }}
          >
            <h3 style={{ color: m.color, fontSize: "1.1rem", fontWeight: "600" }}>{m.name}</h3>
            <p style={{ color: "#aaa", marginTop: "0.3rem" }}>{m.desc}</p>
            <p style={{ marginTop: "0.8rem", fontSize: "0.9rem", color: "#0f0" }}>🟢 Online</p>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

// ===============================
//  CONSOLE / DIAGNOSTICS
// ===============================
function Console({ onSwitch }) {
  const [cpu, setCpu] = useState(37);
  const [ram, setRam] = useState({ used: 5.3, total: 8 });
  const [storage, setStorage] = useState({ used: 52, total: 128 });
  const [uptime, setUptime] = useState("3 j 12 h 41 min");
  const [temp, setTemp] = useState(45.8);

  useEffect(() => {
    const interval = setInterval(() => {
      setCpu(Math.floor(20 + Math.random() * 60));
      setRam({ ...ram, used: (4 + Math.random() * 3).toFixed(1) });
      setTemp((40 + Math.random() * 10).toFixed(1));
    }, 6000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div style={{ textAlign: "center", padding: "2rem 1rem" }}>
      <h2 style={{ color: "#00ffcc", marginBottom: "1rem" }}>
        🧠 Diagnostics Système
      </h2>
      <ActionButton label="← Retour" color="#444" onClick={() => onSwitch("home")} />

      <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: "1.5rem", marginTop: "1.5rem" }}>
        <Gauge title="CPU" value={cpu} unit="%" color="#00ff66" />
        <Gauge title="RAM" value={ram.used} unit={`Go / ${ram.total}`} color="#00ccff" />
        <Gauge title="Stockage" value={storage.used} unit={` / ${storage.total} Go`} color="#ffee33" />
      </div>

      <div style={{ marginTop: "1.5rem", color: "#ccc" }}>
        <p>⏱ Uptime : {uptime}</p>
        <p>🌡 Température : {temp} °C</p>
        <p>⚙️ Santé générale : 🟢 Optimal</p>
      </div>
    </div>
  );
}

// ===============================
//  COMPOSANTS UTILITAIRES
// ===============================
const ActionButton = ({ label, onClick, color }) => (
  <button
    onClick={onClick}
    style={{
      backgroundColor: color,
      color: "#000",
      border: "none",
      borderRadius: "8px",
      padding: "0.7rem 1.4rem",
      margin: "0.4rem",
      fontWeight: "bold",
      cursor: "pointer",
    }}
  >
    {label}
  </button>
);

const Gauge = ({ title, value, unit, color }) => (
  <div style={{ width: "120px" }}>
    <div
      style={{
        height: "120px",
        width: "120px",
        borderRadius: "50%",
        border: `6px solid ${color}`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: "1.2rem",
        fontWeight: "bold",
        color,
        margin: "0 auto",
      }}
    >
      {value}
    </div>
    <p style={{ marginTop: "0.5rem", color: "#ccc" }}>
      {title} {unit}
    </p>
  </div>
);

// ===============================
//  WATCHTOWER v7
// ===============================
function IANotifications() {
  const [logs, setLogs] = useState([]);
  const [detailed, setDetailed] = useState(false);
  const [mute, setMute] = useState(() => localStorage.getItem("muteMode") === "true");
  const [alertFlash, setAlertFlash] = useState(false);
  const [agents, setAgents] = useState([
    { name: "GPT-4", region: "🇺🇸", latency: 120, uptime: "99.9%", status: "Actif" },
    { name: "Gemini", region: "🇫🇷", latency: 240, uptime: "97.8%", status: "Actif" },
    { name: "DeepL", region: "🇩🇪", latency: 90, uptime: "98.4%", status: "Actif" },
    { name: "Modération", region: "🇬🇧", latency: 450, uptime: "95.2%", status: "Instable" },
  ]);
  const [latencyHistory, setLatencyHistory] = useState([]);

  const sounds = {
    online: new Audio("/sounds/online.wav"),
    warning: new Audio("/sounds/warning.wav"),
    error: new Audio("/sounds/error.wav"),
  };

  const getColor = (lat) =>
    lat < 150 ? "#00ff99" : lat < 300 ? "#ffff66" : lat < 600 ? "#ff9933" : "#ff3333";

  useEffect(() => {
    const interval = setInterval(() => {
      setAgents((prev) =>
        prev.map((a) => {
          const newLat = Math.max(50, Math.min(900, a.latency + (Math.random() * 200 - 100)));
          const newStatus =
            newLat < 300 ? "Actif" : newLat < 600 ? "Instable" : "Injoignable";

          if (newStatus !== a.status) {
            const symbol =
              newStatus === "Actif" ? "✅" : newStatus === "Instable" ? "⚠️" : "❌";
            const msg = `${symbol} ${a.name} ${newStatus.toLowerCase()} (${newLat.toFixed(0)} ms)`;

            if (!mute) {
              if (newStatus === "Actif") sounds.online.play();
              else if (newStatus === "Instable") sounds.warning.play();
              else sounds.error.play();
            }

            if (newStatus === "Injoignable") {
              setAlertFlash(true);
              setTimeout(() => setAlertFlash(false), 1200);
            }

            setLogs((p) => [
              ...p.slice(-9),
              { msg, color: getColor(newLat), time: new Date().toLocaleTimeString("fr-FR") },
            ]);
          }
          return { ...a, latency: newLat, status: newStatus };
        })
      );

      setLatencyHistory((prev) => {
        const avg = agents.reduce((acc, a) => acc + a.latency, 0) / agents.length;
        return [...prev.slice(-29), { time: new Date().toLocaleTimeString("fr-FR"), avg }];
      });
    }, 4000);
    return () => clearInterval(interval);
  }, [agents, mute]);

  useEffect(() => localStorage.setItem("muteMode", mute), [mute]);

  return (
    <div
      style={{
        position: "fixed",
        bottom: "1rem",
        right: "1rem",
        width: detailed ? "420px" : "340px",
        background: alertFlash ? "#220000" : "#111",
        border: alertFlash ? "2px solid #ff0033" : "1px solid #222",
        borderRadius: "14px",
        boxShadow: alertFlash ? "0 0 20px #ff003355" : "0 0 15px #00ffcc55",
        padding: "1rem",
        color: "#fff",
        zIndex: 9999,
        transition: "all 0.3s ease",
      }}
    >
      <h4 style={{ color: "#00ffcc", display: "flex", justifyContent: "space-between" }}>
        <span>🔔 IA Watchtower</span>
        <div>
          <button
            onClick={() => setMute(!mute)}
            title={mute ? "Activer le son" : "Désactiver le son"}
            style={{
              background: mute ? "#555" : "#00ffcc",
              color: mute ? "#fff" : "#000",
              border: "none",
              borderRadius: "8px",
              marginRight: "0.5rem",
              padding: "0.3rem 0.6rem",
              fontWeight: "bold",
              cursor: "pointer",
            }}
          >
            {mute ? "🔇" : "🔊"}
          </button>
          <button
            onClick={() => setDetailed(!detailed)}
            style={{
              background: detailed ? "#ff0066" : "#00ffcc",
              border: "none",
              borderRadius: "8px",
              color: "#000",
              fontWeight: "bold",
              padding: "0.3rem 0.7rem",
              cursor: "pointer",
            }}
          >
            {detailed ? "Fermer" : "Vue détaillée"}
          </button>
        </div>
      </h4>

      {!detailed ? (
        <>
          <h5 style={{ color: "#00ffcc" }}>📜 Logs en direct</h5>
          {logs.length === 0 ? (
            <p style={{ fontSize: "0.9rem", color: "#777" }}>Aucun événement récent</p>
          ) : (
            <div style={{ fontSize: "0.85rem", maxHeight: "120px", overflowY: "auto" }}>
              {logs.slice().reverse().map((l, i) => (
                <div key={i} style={{ color: l.color }}>
                  <span style={{ color: "#555" }}>{l.time} </span>
                  {l.msg}
                </div>
              ))}
            </div>
          )}

          <h5 style={{ color: "#00ffcc", marginTop: "0.8rem" }}>🧠 Santé réseau IA</h5>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem" }}>
            {agents.map((a, i) => (
              <div
                key={i}
                style={{
                  flex: "1 1 45%",
                  background: "#000",
                  border: `1px solid ${getColor(a.latency)}55`,
                  borderRadius: "10px",
                  textAlign: "center",
                  padding: "0.4rem 0.2rem",
                  boxShadow: `0 0 10px ${getColor(a.latency)}33`,
                }}
              >
                <p style={{ margin: 0, fontSize: "0.9rem", color: getColor(a.latency) }}>
                  {a.region} {a.name}
                </p>
                <p style={{ fontSize: "0.8rem", color: "#aaa" }}>{a.latency.toFixed(0)} ms</p>
              </div>
            ))}
          </div>

          <div style={{ marginTop: "1rem", height: "120px" }}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={latencyHistory}>
                <Line type="monotone" dataKey="avg" stroke="#00ffcc" strokeWidth={2} dot={false} />
                <Tooltip
                  contentStyle={{ background: "#000", border: "1px solid #222", color: "#fff" }}
                  labelStyle={{ color: "#0ff" }}
                  formatter={(v) => [`${v.toFixed(0)} ms`, "Latence moyenne"]}
                />
              </LineChart>
            </ResponsiveContainer>
            <p style={{ textAlign: "center", color: "#777", fontSize: "0.8rem" }}>
              Charge réseau globale (30 s)
            </p>
          </div>
        </>
      ) : (
        <>
          <h5 style={{ color: "#00ffcc" }}>📋 État détaillé des agents IA</h5>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.9rem" }}>
            <thead>
              <tr style={{ background: "#222", color: "#00ffcc" }}>
                <th style={{ textAlign: "left", padding: "0.5rem" }}>Agent</th>
                <th style={{ textAlign: "center", padding: "0.5rem" }}>Latence</th>
                <th style={{ textAlign: "center", padding: "0.5rem" }}>Uptime</th>
                <th style={{ textAlign: "center", padding: "0.5rem" }}>Statut</th>
              </tr>
            </thead>
            <tbody>
              {agents.map((a, i) => (
                <tr key={i} style={{ borderBottom: "1px solid #222" }}>
                  <td style={{ padding: "0.5rem" }}>
                    {a.region} {a.name}
                  </td>
                  <td style={{ textAlign: "center", color: getColor(a.latency) }}>
                    {a.latency.toFixed(0)} ms
                  </td>
                  <td style={{ textAlign: "center", color: "#ccc" }}>{a.uptime}</td>
                  <td style={{ textAlign: "center", color: getColor(a.latency) }}>
                    {a.status}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
