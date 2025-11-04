import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";
import {
  LineChart, Line, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell
} from "recharts";

// Palette néon
const COLORS = ["#00ffaa", "#00baff", "#ff0099"];

// ===============================
//  SENTINEL QUANTUM VANGUARD  — CONSOLE OMNI-CONTROL v1
// ===============================
export default function Console() {
  const [darkMode, setDarkMode] = useState(true);
  const [logs, setLogs] = useState([]);
  const [latences, setLatences] = useState({ GPT4: 80, Gemini: 110, DeepL: 90, Moderation: 100 });
  const [cpu, setCpu] = useState(40);
  const [ram, setRam] = useState(60);
  const [disk, setDisk] = useState(55);
  const [uptime, setUptime] = useState(0);
  const [agents, setAgents] = useState([]);
  const [scanResult, setScanResult] = useState(null);
  const [scanning, setScanning] = useState(false);
  const [command, setCommand] = useState("");
  const [consoleOutput, setConsoleOutput] = useState(["🧠 Console IA prête."]);

  // simulation périodique
  useEffect(() => {
    const t = setInterval(() => {
      const time = new Date().toLocaleTimeString();
      const events = ["✅ GPT-4 stable", "🛰️ Synchronisation réseau", "⚠️ Latence Gemini", "🧩 DeepL actif"];
      setLogs(p => [{ time, msg: events[Math.floor(Math.random() * events.length)] }, ...p.slice(0, 30)]);
      setCpu(Math.round(30 + Math.random() * 60));
      setRam(Math.round(40 + Math.random() * 50));
      setDisk(Math.round(30 + Math.random() * 40));
      setLatences({
        GPT4: 50 + Math.round(Math.random() * 100),
        Gemini: 70 + Math.round(Math.random() * 100),
        DeepL: 60 + Math.round(Math.random() * 80),
        Moderation: 80 + Math.round(Math.random() * 100),
      });
      setUptime(u => u + 5);
      // agents simulés
      const a = ["AI-Core", "Firewall", "Watcher", "Predictor", "Defender"];
      setAgents(a.map(n => ({
        name: n,
        ping: (50 + Math.random() * 80).toFixed(0) + " ms",
        version: "v" + (4 + Math.random()).toFixed(1),
        status: Math.random() > 0.1 ? "🟢" : "🟠"
      })));
    }, 4000);
    return () => clearInterval(t);
  }, []);

  // scan IA Defender
  const handleScan = async e => {
    const f = e.target.files[0];
    if (!f) return;
    setScanning(true); setScanResult(null);
    try {
      const formData = new FormData();
      formData.append("file", f);
      const r = await fetch("https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net/scanThreats", { method: "POST", body: formData });
      const data = await r.json();
      setScanResult(data);
    } catch {
      setScanResult({ error: "Erreur IA Defender : connexion impossible." });
    } finally { setScanning(false); }
  };

  // console IA
  const handleCommand = e => {
    e.preventDefault();
    const cmd = command.trim().toLowerCase();
    if (!cmd) return;
    let out = "";
    if (cmd === "status" || cmd === "agents") out = "🟢 Tous les agents répondent.";
    else if (cmd === "scan now") out = "🧠 IA Defender en cours...";
    else if (cmd === "reboot") out = "♻️ Redémarrage réseau simulé.";
    else if (cmd === "help") out = "📘 status | agents | scan now | reboot | clear";
    else if (cmd === "clear") { setConsoleOutput([]); setCommand(""); return; }
    else out = "⚙️ Commande inconnue : " + cmd;
    setConsoleOutput(p => ["> " + cmd, out, ...p]);
    setCommand("");
  };

  const donutData = [
    { name: "CPU", value: cpu },
    { name: "RAM", value: ram },
    { name: "Stockage", value: disk },
  ];
  const moyenne = Math.round(Object.values(latences).reduce((a, b) => a + b, 0) / Object.keys(latences).length);

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: "2rem",
        background: darkMode
          ? "radial-gradient(circle at 25% 20%, #010101 0%, #000 100%)"
          : "linear-gradient(180deg,#ececec,#bfbfbf)",
        color: darkMode ? "#e5e5e5" : "#111",
        fontFamily: "Inter,sans-serif",
        transition: "0.4s all"
      }}
    >
      {/* HEADER */}
      <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .6 }} className="text-center mb-8">
        <h1 className="text-3xl font-bold text-emerald-400 mb-2">🧠 Sentinel Quantum Vanguard Console Omni-Control</h1>
        <p style={{ color: "#aaa" }}>Supervision IA — Réseau — Sécurité — Agents — Système</p>
        <button onClick={() => setDarkMode(!darkMode)}
          style={{
            marginTop: "1rem", background: darkMode ? "#00ffaa20" : "#111",
            color: darkMode ? "#fff" : "#00ff99", padding: ".4rem 1rem",
            borderRadius: ".5rem", border: "1px solid rgba(0,255,180,.3)"
          }}>
          {darkMode ? "☀️ Mode clair" : "🌙 Mode sombre"}
        </button>
      </motion.div>

      {/* LOGS */}
      <section style={{ background: "rgba(20,20,20,.6)", borderRadius: "1rem", padding: "1.5rem", marginBottom: "2rem", boxShadow: "0 0 15px rgba(0,255,150,.3)" }}>
        <h2 className="text-xl font-semibold mb-3 text-emerald-300">🔔 Logs IA Watchtower</h2>
        <div style={{ maxHeight: 180, overflowY: "auto", background: "rgba(0,0,0,.4)", padding: "1rem", borderRadius: ".5rem" }}>
          {logs.map((l, i) => (<div key={i}><span style={{ color: "#666" }}>{l.time}</span> {l.msg}</div>))}
        </div>
      </section>

      {/* RÉSEAU + MONITOR */}
      <section style={{ background: "rgba(20,20,30,.6)", borderRadius: "1rem", padding: "1.5rem", marginBottom: "2rem", boxShadow: "0 0 20px rgba(0,255,255,.3)" }}>
        <h2 className="text-xl font-semibold mb-3 text-cyan-300">🌐 Réseau & Ressources</h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(150px,1fr))", gap: "1rem" }}>
          {Object.entries(latences).map(([n, v]) => (
            <div key={n} style={{ background: "rgba(0,0,0,.3)", borderRadius: ".8rem", textAlign: "center", padding: "1rem" }}>
              <h3 style={{ color: "#00ffaa" }}>{n}</h3><p>{v} ms</p>
            </div>
          ))}
        </div>
        <div style={{ height: 180, marginTop: "1.5rem", background: "rgba(0,0,0,.3)", borderRadius: ".8rem" }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={Object.keys(latences).map(k => ({ name: k, value: latences[k] }))}>
              <Tooltip /><Line type="monotone" dataKey="value" stroke="#00ffcc" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <div style={{ display: "flex", justifyContent: "center", marginTop: "1rem" }}>
          <PieChart width={220} height={220}>
            <Pie data={donutData} cx="50%" cy="50%" outerRadius={90} dataKey="value">
              {donutData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
            </Pie><Tooltip />
          </PieChart>
        </div>
        <p style={{ textAlign: "center", color: "#00ffaa" }}>
          CPU {cpu}% | RAM {ram}% | Disk {disk}% | Lat moy {moyenne} ms | Uptime {uptime}s
        </p>
      </section>

      {/* TABLE AGENTS */}
      <section style={{ background: "rgba(0,10,30,.6)", borderRadius: "1rem", padding: "1.5rem", marginBottom: "2rem", boxShadow: "0 0 20px rgba(0,200,255,.2)" }}>
        <h2 className="text-xl font-semibold mb-3 text-sky-400">🤖 Agents IA actifs</h2>
        <table style={{ width: "100%", textAlign: "center", fontSize: ".9rem" }}>
          <thead><tr style={{ color: "#0ff" }}>
            <th>Nom</th><th>Ping</th><th>Version</th><th>État</th>
          </tr></thead>
          <tbody>
            {agents.map((a, i) => (
              <tr key={i}><td>{a.name}</td><td>{a.ping}</td><td>{a.version}</td><td>{a.status}</td></tr>
            ))}
          </tbody>
        </table>
      </section>

      {/* ANTIVIRUS */}
      <section style={{ background: "rgba(50,0,0,.5)", borderRadius: "1rem", padding: "1.5rem", boxShadow: scanning ? "0 0 25px rgba(0,255,100,.6)" : "0 0 10px rgba(255,255,255,.2)", textAlign: "center" }}>
        <h2 className="text-xl font-semibold mb-3 text-red-400">🛡️ IA Defender+</h2>
        <input type="file" onChange={handleScan} style={{ margin: "1rem auto", color: "#ddd" }} />
        {scanning && <motion.div animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 2 }} style={{ color: "#00ffaa" }}>🌀 Analyse...</motion.div>}
        {scanResult && (
          <div style={{ marginTop: "1rem" }}>
            {scanResult.error ? <p style={{ color: "#f66" }}>{scanResult.error}</p> :
              <><p><b>Statut :</b> {scanResult.status || "OK"}</p>
              <p><b>Fichier :</b> {scanResult.scanned || "N/A"}</p></>}
          </div>
        )}
      </section>

      {/* THREAT MAP */}
      <section style={{ background: "rgba(0,20,30,.5)", borderRadius: "1rem", padding: "1.5rem", marginTop: "2rem" }}>
        <h2 className="text-xl font-semibold mb-3 text-cyan-400">🌍 Carte mondiale des menaces (IA Threat Map)</h2>
        <div style={{ position: "relative", width: "100%", height: 200, background: "url('https://upload.wikimedia.org/wikipedia/commons/8/80/World_map_-_low_resolution.svg') center/contain no-repeat", filter: "brightness(0.7)" }}>
          {[...Array(10)].map((_, i) => (
            <motion.div key={i}
              animate={{ scale: [1, 1.5, 1], opacity: [1, 0.3, 1] }}
              transition={{ duration: 2 + Math.random() * 2, repeat: Infinity }}
              style={{
                position: "absolute", top: `${Math.random() * 80 + 10}%`, left: `${Math.random() * 80 + 10}%`,
                width: "8px", height: "8px", borderRadius: "50%", background: ["#0ff", "#f0f", "#ff0"][i % 3]
              }}
            />
          ))}
        </div>
      </section>

      {/* CONSOLE IA */}
      <section style={{ marginTop: "2rem", background: "rgba(0,0,0,.6)", borderRadius: "1rem", padding: "1rem" }}>
        <h3 className="text-lg text-emerald-400 mb-2">💻 Console IA interactive</h3>
        <form onSubmit={handleCommand}>
          <input type="text" value={command} onChange={e => setCommand(e.target.value)} placeholder="> Commande (ex : status)"
            style={{ width: "100%", background: "rgba(30,30,30,.9)", color: "#0f0", border: "1px solid #00ff99", borderRadius: ".5rem", padding: ".5rem", marginBottom: ".5rem" }} />
        </form>
        <div style={{ background: "rgba(10,10,10,.8)", padding: "1rem", height: 160, overflowY: "auto", borderRadius: ".5rem", color: "#0f0" }}>
          {consoleOutput.map((l, i) => <div key={i}>{l}</div>)}
        </div>
      </section>

      {/* FOOTER */}
      <section style={{ marginTop: "2rem", textAlign: "center", fontSize: ".9rem", opacity: .8, color: "#aaa" }}>
        <p>Sentinel Quantum Vanguard Omni-Control v6.0</p>
        <p>Supervision totale — Mode {darkMode ? "Nuit" : "Jour"} — © 2025</p>
      </section>
    </div>
  );
}
