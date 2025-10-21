import React, { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend);

export default function Dashboard() {
  const [loading, setLoading] = useState(true);
  const [agents, setAgents] = useState([]);

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 2000);
    setTimeout(() => {
      setAgents([
        { id: 1, name: "QuantumFailoverAI", status: "Actif", uptime: "99.98%" },
        { id: 2, name: "CognitiveTraceAgent", status: "Actif", uptime: "99.91%" },
        { id: 3, name: "AutoRollbackCommander", status: "En veille", uptime: "98.77%" },
      ]);
    }, 3000);
    return () => clearTimeout(timer);
  }, []);

  const chartData = {
    labels: ["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"],
    datasets: [
      {
        label: "Activité IA (agents connectés)",
        data: [12, 18, 9, 14, 21, 19, 24],
        borderColor: "#60a5fa",
        backgroundColor: "rgba(96,165,250,0.15)",
        pointBackgroundColor: "#3b82f6",
        borderWidth: 2,
        fill: true,
        tension: 0.4,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    plugins: {
      legend: { display: true, labels: { color: "#9ca3af" } },
    },
    scales: {
      x: { ticks: { color: "#9ca3af" }, grid: { color: "#1e293b" } },
      y: { ticks: { color: "#9ca3af" }, grid: { color: "#1e293b" } },
    },
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-[#020617] via-[#0f172a] to-[#1e293b] text-gray-100 flex flex-col items-center py-10 px-4">
      <motion.h1
        className="text-4xl md:text-5xl font-bold text-blue-400 mb-3 text-center"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        Sentinel Quantum Vanguard AI Pro
      </motion.h1>

      <motion.p
        className="text-gray-400 mb-10 text-center text-lg"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
      >
        “La sécurité du futur, dès aujourd’hui”
      </motion.p>

      {loading ? (
        <motion.div
          className="w-14 h-14 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mt-20"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        />
      ) : (
        <div className="w-full max-w-6xl space-y-8">
          <motion.div
            className="bg-[#1e293b]/60 p-6 rounded-2xl shadow-lg backdrop-blur-xl"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8 }}
          >
            <h2 className="text-2xl font-semibold text-blue-400 mb-4">
              Activité Réseau IA
            </h2>
            <Line data={chartData} options={chartOptions} />
          </motion.div>

          <motion.div
            className="bg-[#1e293b]/60 p-6 rounded-2xl shadow-lg backdrop-blur-xl"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.9 }}
          >
            <h2 className="text-2xl font-semibold text-blue-400 mb-4">
              Agents connectés
            </h2>
            {agents.length === 0 ? (
              <p className="text-gray-400 italic">Aucun agent actif détecté...</p>
            ) : (
              <div className="grid md:grid-cols-3 gap-4">
                {agents.map((a) => (
                  <motion.div
                    key={a.id}
                    className="bg-[#0f172a]/80 border border-blue-800/40 p-4 rounded-xl hover:shadow-blue-500/20 transition duration-300"
                    whileHover={{ scale: 1.05 }}
                  >
                    <h3 className="text-lg font-semibold text-blue-300 mb-1">{a.name}</h3>
                    <p className="text-sm text-gray-400">
                      Statut :{" "}
                      <span
                        className={
                          a.status === "Actif" ? "text-green-400" : "text-yellow-400"
                        }
                      >
                        {a.status}
                      </span>
                    </p>
                    <p className="text-sm text-gray-400">Uptime : {a.uptime}</p>
                  </motion.div>
                ))}
              </div>
            )}
          </motion.div>

          <motion.div
            className="bg-[#1e293b]/60 p-6 rounded-2xl shadow-lg text-center"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 1 }}
          >
            <h2 className="text-xl text-blue-400 font-semibold mb-2">
              Statut global du réseau IA
            </h2>
            <p className="text-gray-300">
              Tous les modules Sentinel fonctionnent normalement.
            </p>
          </motion.div>
        </div>
      )}
    </div>
  );
}
