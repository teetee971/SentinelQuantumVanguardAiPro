import React, { useState, useEffect } from "react";
import { LineChart, Line, CartesianGrid, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";

const modules = [
  { name: "GPT-4 (OpenAI)", key: "gpt4", url: "https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net/generateText" },
  { name: "Gemini (Google)", key: "gemini", url: "https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net/gemini" },
  { name: "DeepL (Traduction)", key: "deepl", url: "https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net/translate" },
  { name: "Modération IA", key: "moderation", url: "https://us-central1-sentinel-vanguard-ai-pro.cloudfunctions.net/moderate" },
];

export default function Admin() {
  const [status, setStatus] = useState({});
  const [logs, setLogs] = useState([]);
  const [latencyData, setLatencyData] = useState([]);

  // 🔄 Fonction de ping pour chaque module
  const checkModules = async () => {
    const results = {};
    const latencies = [];

    for (const mod of modules) {
      const start = performance.now();
      try {
        const res = await fetch(mod.url, { method: "POST", body: "{}" });
        const end = performance.now();
        const latency = Math.round(end - start);
        latencies.push({ module: mod.key, latency });
        results[mod.key] = res.ok ? "🟢 Actif" : "🔴 Erreur";
        setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ✅ ${mod.name} actif (${latency} ms)`, ...prev]);
      } catch {
        const end = performance.now();
        const latency = Math.round(end - start);
        latencies.push({ module: mod.key, latency });
        results[mod.key] = "🔴 Injoignable";
        setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ❌ ${mod.name} injoignable`, ...prev]);
      }
    }

    setStatus(results);
    setLatencyData((prev) => [...prev.slice(-10), { time: new Date().toLocaleTimeString(), ...Object.fromEntries(latencies.map(l => [l.module, l.latency])) }]);
  };

  // 🔁 Mise à jour automatique toutes les 15 s
  useEffect(() => {
    checkModules();
    const interval = setInterval(checkModules, 15000);
    return () => clearInterval(interval);
  }, []);

  const relancer = async (mod) => {
    setLogs((prev) => [`[${new Date().toLocaleTimeString()}] 🔄 Relance de ${mod.name}...`, ...prev]);
    try {
      await fetch(mod.url, { method: "POST" });
      setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ✅ ${mod.name} relancé avec succès`, ...prev]);
      checkModules();
    } catch {
      setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ❌ Échec relance ${mod.name}`, ...prev]);
    }
  };

  return (
    <div className="min-h-screen bg-black text-white flex flex-col items-center p-6">
      <h1 className="text-3xl font-bold text-red-500 mb-6">🛰️ Sentinel IA Admin Console</h1>

      {/* Tableau de statut */}
      <div className="w-full max-w-4xl bg-neutral-900 rounded-xl p-4 mb-6 shadow-lg">
        <h2 className="text-xl font-semibold mb-3">📊 État des modules IA</h2>
        <table className="w-full border-collapse">
          <thead>
            <tr className="border-b border-neutral-700 text-left">
              <th className="p-2">Module</th>
              <th className="p-2">Statut</th>
              <th className="p-2">Action</th>
            </tr>
          </thead>
          <tbody>
            {modules.map((mod) => (
              <tr key={mod.key} className="border-b border-neutral-800">
                <td className="p-2">{mod.name}</td>
                <td className="p-2">{status[mod.key] || "⏳ Vérification..."}</td>
                <td className="p-2">
                  <button
                    onClick={() => relancer(mod)}
                    className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded text-sm"
                  >
                    Relancer
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Graphique de latence */}
      <div className="w-full max-w-4xl bg-neutral-900 rounded-xl p-4 mb-6 shadow-lg">
        <h2 className="text-xl font-semibold mb-3">📈 Latence des modules IA (ms)</h2>
        <div className="w-full h-64">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={latencyData}>
              <CartesianGrid stroke="#333" />
              <XAxis dataKey="time" stroke="#aaa" />
              <YAxis stroke="#aaa" />
              <Tooltip />
              {modules.map((mod, i) => (
                <Line
                  key={i}
                  type="monotone"
                  dataKey={mod.key}
                  stroke={["#0f0", "#0af", "#ff0", "#f00"][i]}
                  strokeWidth={2}
                  dot={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Logs */}
      <div className="w-full max-w-4xl bg-neutral-900 rounded-xl p-4 shadow-lg">
        <h2 className="text-xl font-semibold mb-3">🧠 Journal d’activité</h2>
        <div className="bg-black border border-neutral-800 rounded p-3 h-72 overflow-y-scroll text-sm font-mono">
          {logs.length === 0 ? (
            <p className="text-neutral-500">Aucune activité pour le moment.</p>
          ) : (
            logs.map((log, i) => <div key={i}>{log}</div>)
          )}
        </div>
      </div>
    </div>
  );
}
