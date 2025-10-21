import React, { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend);

// Fonction qui génère une ligne de log aléatoire
const generateLog = () => {
  const logs = [
    "⚙️ Agent QuantumFailoverAI → OK (latency 38 ms)",
    "🧠 CognitiveTraceAgent synchronisé avec MetaCore",
    "🛡️ AntiExploitSentinel scanning kernel threads...",
    "🔁 CDNConsistencyAgent → all mirrors updated",
    "🚨 SessionHijackGuardian : 0 intrusion détectée",
    "📊 PerformanceAutoTuner recalibrage CPU...",
    "🌐 CloudflarePropagateWatcher propagation stable",
  ];
  return logs[Math.floor(Math.random() * logs.length)];
};

export default function Agents() {
  const [timestamp, setTimestamp] = useState(new Date().toLocaleTimeString());
  const [logs, setLogs] = useState([]);
  const [agents, setAgents] = useState([
    { name: "QuantumFailoverAI", status: "Actif", uptime: 99.98, load: 40 },
    { name: "CognitiveTraceAgent", status: "Opérationnel", uptime: 99.95, load: 55 },
    { name: "AutoRollbackCommander", status: "Analyse", uptime: 99.70, load: 60 },
    { name: "HeuristicPredictorAI", status: "Actif", uptime: 98.93, load: 50 },
    { name: "SentinelHealer", status: "En veille", uptime: 97.80, load: 20 },
  ]);

  const [chartData, setChartData] = useState({
    labels: ["T-1", "T-0"],
    datasets: [
      {
        label: "Charge moyenne (%)",
        data: [45, 50],
        borderColor: "#3b82f6",
        backgroundColor: "rgba(59,130,246,0.2)",
        tension: 0.4,
        fill: true,
      },
    ],
  });

  // 🔄 Rafraîchissement automatique toutes les 10 s
  useEffect(() => {
    const interval = setInterval(() => {
      const newAgents = agents.map((a) => {
        const delta = Math.random() * 10 - 5;
        const load = Math.min(Math.max(a.load + delta, 10), 100);
        const uptime = Math.min(Math.max(a.uptime + (Math.random() * 0.1 - 0.05), 95), 100);
        const status =
          load > 85 ? "Surcharge" : load < 20 ? "En veille" : "Actif";
        return { ...a, load, uptime, status };
      });
      setAgents(newAgents);

      const avgLoad = newAgents.reduce((s, a) => s + a.load, 0) / newAgents.length;
      setChartData((prev) => ({
        labels: [...prev.labels.slice(-9), new Date().toLocaleTimeString()],
        datasets: [
          {
            ...prev.datasets[0],
            data: [...prev.datasets[0].data.slice(-9), avgLoad],
          },
        ],
      }));
      setTimestamp(new Date().toLocaleTimeString());
    }, 10000);
    return () => clearInterval(interval);
  }, [agents]);

  // 🔰 Génération des logs IA toutes les 2 s
  useEffect(() => {
    const interval = setInterval(() => {
      setLogs((prev) => [generateLog(), ...prev.slice(0, 20)]);
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  const totalActive = agents.filter((a) => a.status === "Actif").length;
  const avgUptime = (
    agents.reduce((sum, a) => sum + a.uptime, 0) / agents.length
  ).toFixed(2);

  return (
    <motion.div
      className="w-full max-w-5xl mx-auto p-6 space-y-6"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.8 }}
    >
      {/* HEADER */}
      <div className="bg-[#0f172a]/80 p-6 rounded-2xl shadow-lg backdrop-blur-lg">
        <h2 className="text-3xl font-semibold text-blue-400 mb-1">
          Sentinel AI Console — Supervision en direct
        </h2>
        <p className="text-gray-400 text-sm">Mise à jour : {timestamp}</p>
      </div>

      {/* GRAPH */}
      <motion.div
        className="bg-[#0f172a]/80 p-6 rounded-2xl shadow-lg backdrop-blur-lg"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 1 }}
      >
        <h3 className="text-2xl text-blue-400 font-semibold mb-3">
          Charge moyenne du réseau IA
        </h3>
        <Line data={chartData} />
      </motion.div>

      {/* GRID AGENTS */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {agents.map((agent, i) => (
          <motion.div
            key={i}
            className={`p-4 rounded-xl border backdrop-blur-lg shadow-lg transition-all duration-500 ${
              agent.status === "Actif"
                ? "border-green-500/40"
                : agent.status === "Surcharge"
                ? "border-red-500/40"
                : "border-yellow-500/40"
            }`}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
          >
            <h3 className="text-lg font-semibold text-white">{agent.name}</h3>
            <p
              className={`font-medium ${
                agent.status === "Actif"
                  ? "text-green-400"
                  : agent.status === "Surcharge"
                  ? "text-red-400"
                  : "text-yellow-400"
              }`}
            >
              Statut : {agent.status}
            </p>
            <p className="text-sm text-gray-400">
              Uptime : {agent.uptime.toFixed(2)} % — Charge : {agent.load.toFixed(1)} %
            </p>
          </motion.div>
        ))}
      </div>

      {/* SUMMARY */}
      <motion.div
        className="bg-[#0f172a]/80 p-4 rounded-xl text-center text-gray-300 border border-blue-900/40"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
      >
        <p className="text-blue-400 text-lg font-semibold">
          {totalActive}/{agents.length} agents IA actifs
        </p>
        <p className="text-sm">Uptime moyen global : {avgUptime} %</p>
      </motion.div>

      {/* TERMINAL LIVE */}
      <motion.div
        className="bg-black/70 p-4 mt-8 rounded-lg font-mono text-sm h-48 overflow-y-auto border border-green-600/40 shadow-inner text-green-400"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.8 }}
      >
        <p className="text-green-300 font-semibold mb-2">[Console IA Live]</p>
        {logs.map((log, idx) => (
          <p key={idx} className="animate-pulse">{log}</p>
        ))}
      </motion.div>
    </motion.div>
  );
}
