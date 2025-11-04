import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";

export default function Console() {
  const [cpu, setCpu] = useState(45);
  const [ram, setRam] = useState({ used: 3.2, total: 8 });
  const [storage, setStorage] = useState({ used: 40, total: 128 });
  const [temperature, setTemperature] = useState(38);
  const [systemHealth, setSystemHealth] = useState("🟢 Stable");
  const [latency, setLatency] = useState(80);
  const [data, setData] = useState([]);
  const [activeAgents, setActiveAgents] = useState([]);

  // === Simulation dynamique ===
  useEffect(() => {
    const interval = setInterval(() => {
      setCpu((v) => Math.min(100, Math.max(5, v + (Math.random() * 10 - 5))));
      setTemperature((t) => Math.min(85, Math.max(20, t + (Math.random() * 4 - 2))));
      setLatency((l) => Math.min(200, Math.max(20, l + (Math.random() * 30 - 15))));

      setRam((r) => {
        const newUsed = Math.min(r.total, Math.max(1, r.used + (Math.random() * 0.3 - 0.15)));
        return { ...r, used: newUsed };
      });

      setStorage((s) => {
        const newUsed = Math.min(s.total, Math.max(10, s.used + (Math.random() * 0.5 - 0.25)));
        return { ...s, used: newUsed };
      });

      setSystemHealth(cpu > 85 || temperature > 70 ? "🔴 Alerte thermique" : cpu > 70 ? "🟠 Surveillance" : "🟢 Stable");

      const newPoint = {
        time: new Date().toLocaleTimeString("fr-FR", { minute: "2-digit", second: "2-digit" }),
        cpu,
        temp: temperature,
        latency,
      };
      setData((prev) => [...prev.slice(-15), newPoint]);

      const agents = [
        { name: "GPT-4", status: "🟢 Actif", latency: latency },
        { name: "Gemini", status: "🟢 Actif", latency: latency * 1.1 },
        { name: "DeepL", status: "🟢 Actif", latency: latency * 0.9 },
        { name: "Modération", status: "🟢 Actif", latency: latency * 1.05 },
      ];
      setActiveAgents(agents);
    }, 3000);
    return () => clearInterval(interval);
  }, [cpu, temperature, latency]);

  const CircularBar = ({ value, color }) => (
    <div
      style={{
        width: "100px",
        height: "100px",
        borderRadius: "50%",
        border: `6px solid ${color}`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: color,
        fontWeight: "bold",
      }}
    >
      {Math.round(value)}%
    </div>
  );

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "radial-gradient(circle at 20% 20%, #050505 0%, #000 100%)",
        color: "#fff",
        padding: "6rem 1rem 3rem",
        fontFamily: "Inter, sans-serif",
      }}
    >
      <motion.h1
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        style={{
          fontSize: "2.2rem",
          fontWeight: "700",
          color: "#00ffcc",
          textAlign: "center",
          textShadow: "0 0 15px #00ffcc55",
          marginBottom: "2rem",
        }}
      >
        🧠 Console IA – Sentinel Quantum Vanguard AI Pro
      </motion.h1>

      {/* === AGENTS === */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
          gap: "1rem",
          marginBottom: "2rem",
        }}
      >
        {activeAgents.map((agent, i) => (
          <motion.div
            key={i}
            whileHover={{ scale: 1.05 }}
            style={{
              background: "#0a0a0a",
              padding: "1rem",
              borderRadius: "10px",
              border: "1px solid #00ffcc33",
              boxShadow: "0 0 10px #00ffcc22",
            }}
          >
            <h3 style={{ color: "#00ffcc", fontWeight: "600" }}>{agent.name}</h3>
            <p style={{ color: "#999", fontSize: "0.9rem" }}>{agent.status}</p>
            <p style={{ color: "#0f0", marginTop: "0.5rem" }}>⏱ Latence : {agent.latency.toFixed(0)} ms</p>
          </motion.div>
        ))}
      </div>

      {/* === GRAPHIQUE LIVE === */}
      <div
        style={{
          background: "#0a0a0a",
          borderRadius: "12px",
          padding: "1rem",
          marginBottom: "3rem",
          border: "1px solid #00ffcc33",
          boxShadow: "0 0 20px #00ffcc22",
        }}
      >
        <h3 style={{ color: "#00ffcc", textAlign: "center", marginBottom: "1rem" }}>
          📊 Surveillance en direct (CPU / Température / Latence)
        </h3>
        <ResponsiveContainer width="100%" height={250}>
          <LineChart data={data}>
            <XAxis dataKey="time" stroke="#555" />
            <YAxis stroke="#555" />
            <Tooltip />
            <Line type="monotone" dataKey="cpu" stroke="#00ffcc" strokeWidth={2} />
            <Line type="monotone" dataKey="temp" stroke="#ff3366" strokeWidth={2} />
            <Line type="monotone" dataKey="latency" stroke="#ffff33" strokeWidth={2} />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* === DIAGNOSTIC SYSTÈME === */}
      <div
        style={{
          background: "#0b0b0b",
          borderRadius: "12px",
          padding: "1.5rem",
          textAlign: "center",
          border: "1px solid #111",
          boxShadow: "0 0 20px #00ffcc33",
        }}
      >
        <h3 style={{ color: "#00ffcc", marginBottom: "1.5rem" }}>⚙️ Diagnostic matériel</h3>
        <div style={{ display: "flex", justifyContent: "center", flexWrap: "wrap", gap: "1.5rem" }}>
          <div>
            <CircularBar value={cpu} color={cpu > 80 ? "#f33" : cpu > 60 ? "#ff0" : "#0f0"} />
            <p style={{ marginTop: "0.5rem" }}>CPU</p>
          </div>
          <div>
            <CircularBar value={(ram.used / ram.total) * 100} color="#00ccff" />
            <p style={{ marginTop: "0.5rem" }}>
              RAM {ram.used.toFixed(1)} / {ram.total} Go
            </p>
          </div>
          <div>
            <CircularBar value={(storage.used / storage.total) * 100} color="#ffaa00" />
            <p style={{ marginTop: "0.5rem" }}>
              Stockage {storage.used.toFixed(0)} / {storage.total} Go
            </p>
          </div>
        </div>
        <p style={{ marginTop: "1.5rem", color: "#aaa" }}>
          🌡 Température : {temperature.toFixed(1)} °C <br />
          💡 Santé générale : {systemHealth}
        </p>
      </div>
    </div>
  );
}
