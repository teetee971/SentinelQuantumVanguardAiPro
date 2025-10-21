import React, { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  LineElement,
  CategoryScale,
  LinearScale,
  PointElement,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(LineElement, CategoryScale, LinearScale, PointElement, Tooltip, Legend);

function Dashboard() {
  const [logs, setLogs] = useState([
    "[22:31:04] CognitiveTraceAgent : Détection d’activité IA",
    "[22:31:22] AutoRollbackCommander : Isolation d’erreur système",
    "[22:32:10] HeuristicPredictorAI : Risque évalué à 2.4%",
    "[22:33:18] GlobalFailoverWatcher : Synchronisation Cloud OK",
    "[22:34:01] SentinelHealer : Module réparé avec succès",
  ]);

  const [chartData, setChartData] = useState({
    labels: ["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"],
    datasets: [
      {
        label: "Activité IA (agents connectés)",
        data: [12, 17, 9, 14, 20, 18, 22],
        borderColor: "#3b82f6",
        backgroundColor: "rgba(59, 130, 246, 0.2)",
        tension: 0.4,
        fill: true,
      },
    ],
  });

  // 🔄 Simulation de nouveaux logs toutes les 5 secondes
  useEffect(() => {
    const interval = setInterval(() => {
      const newLog = `[${new Date().toLocaleTimeString()}] SentinelCore : Activité réseau stable`;
      setLogs((prevLogs) => [...prevLogs.slice(-6), newLog]);
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  return (
    <motion.div
      className="w-full max-w-5xl mx-auto p-6 space-y-8"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 1 }}
    >
      <motion.div
        className="bg-[#0f172a]/80 p-6 rounded-2xl shadow-lg backdrop-blur-lg"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
      >
        <h2 className="text-2xl font-semibold text-blue-400 mb-4">
          Activité Réseau IA
        </h2>
        <Line data={chartData} />
      </motion.div>

      <motion.div
        className="bg-[#0f172a]/80 p-6 rounded-2xl shadow-lg backdrop-blur-lg"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8, delay: 0.3 }}
      >
        <h2 className="text-2xl font-semibold text-blue-400 mb-4">
          Agents connectés
        </h2>

        <div className="space-y-4">
          <div className="p-4 rounded-lg border border-blue-900/30">
            <h3 className="text-lg font-semibold text-white">QuantumFailoverAI</h3>
            <p className="text-green-400">Statut : Actif</p>
            <p className="text-sm text-gray-400">Uptime : 99.98%</p>
          </div>
          <div className="p-4 rounded-lg border border-blue-900/30">
            <h3 className="text-lg font-semibold text-white">CognitiveTraceAgent</h3>
            <p className="text-green-400">Statut : Opérationnel</p>
            <p className="text-sm text-gray-400">Uptime : 99.95%</p>
          </div>
        </div>
      </motion.div>

      <motion.div
        className="bg-[#0f172a]/80 p-6 rounded-2xl shadow-lg backdrop-blur-lg"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 1, delay: 0.5 }}
      >
        <h2 className="text-2xl font-semibold text-blue-400 mb-4">Journal IA</h2>
        <div className="space-y-2 text-sm font-mono text-gray-300 overflow-y-auto max-h-64">
          {logs.map((log, i) => (
            <motion.div
              key={i}
              className="border-b border-blue-900/30 pb-1"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: i * 0.1 }}
            >
              {log}
            </motion.div>
          ))}
        </div>
      </motion.div>
    </motion.div>
  );
}

export default Dashboard;
