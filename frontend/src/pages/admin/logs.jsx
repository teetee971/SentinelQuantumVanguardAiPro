import React, { useEffect, useState } from "react";
import { collection, query, orderBy, onSnapshot } from "firebase/firestore";
import { db } from "../../firebaseConfig";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from "recharts";
import { motion } from "framer-motion";
import { Shield, Download, Clock, Smartphone, Monitor, Filter } from "lucide-react";
import Navbar from "../../components/Navbar";

export default function LogsAdmin() {
  const [logs, setLogs] = useState([]);
  const [chartData, setChartData] = useState([]);
  const [filteredLogs, setFilteredLogs] = useState([]);
  const [selectedDevice, setSelectedDevice] = useState("all");
  const [dateFilter, setDateFilter] = useState("");
  const [stats, setStats] = useState({ total: 0, today: 0, mobile: 0, desktop: 0 });

  useEffect(() => {
    const q = query(collection(db, "telechargements_logs"), orderBy("utc_timestamp", "desc"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const data = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      setLogs(data);
      setFilteredLogs(data);

      // Statistiques
      const today = new Date().toISOString().split("T")[0];
      const todayLogs = data.filter((log) => log.utc_timestamp?.startsWith(today));
      const mobileLogs = data.filter((log) => log.device_type === "mobile");
      const desktopLogs = data.filter((log) => log.device_type === "desktop");

      setStats({
        total: data.length,
        today: todayLogs.length,
        mobile: mobileLogs.length,
        desktop: desktopLogs.length,
      });

      // Regrouper les logs par heure pour le graphique
      const counts = {};
      data.forEach((log) => {
        if (log.utc_timestamp) {
          const date = new Date(log.utc_timestamp);
          const hour = date.getHours();
          counts[hour] = (counts[hour] || 0) + 1;
        }
      });
      const formatted = Object.entries(counts)
        .map(([hour, count]) => ({ hour: `${hour}h`, count }))
        .sort((a, b) => parseInt(a.hour) - parseInt(b.hour));
      setChartData(formatted);
    });

    return () => unsubscribe();
  }, []);

  useEffect(() => {
    let filtered = logs;

    // Filtre par type d'appareil
    if (selectedDevice !== "all") {
      filtered = filtered.filter((log) => log.device_type === selectedDevice);
    }

    // Filtre par date
    if (dateFilter) {
      filtered = filtered.filter((log) => log.utc_timestamp?.startsWith(dateFilter));
    }

    setFilteredLogs(filtered);
  }, [selectedDevice, dateFilter, logs]);

  return (
    <div className="min-h-screen bg-black text-white">
      <Navbar />
      
      <div className="pt-20 px-4 md:px-8 max-w-7xl mx-auto">
        {/* En-tête */}
        <motion.div
          className="mb-8"
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7 }}
        >
          <div className="flex items-center gap-3 mb-2">
            <Shield className="w-8 h-8 text-blue-400" />
            <h1 className="text-3xl md:text-4xl font-bold text-blue-400">
              Console Admin - Logs de Téléchargement
            </h1>
          </div>
          <p className="text-gray-400 text-sm">
            Supervision en temps réel des accès au document officiel Sentinel
          </p>
        </motion.div>

        {/* Statistiques */}
        <motion.div
          className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.2 }}
        >
          <div className="bg-gradient-to-br from-blue-900/40 to-blue-950/40 border border-blue-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm">Total</p>
                <p className="text-3xl font-bold text-blue-300">{stats.total}</p>
              </div>
              <Download className="w-10 h-10 text-blue-400 opacity-50" />
            </div>
          </div>

          <div className="bg-gradient-to-br from-green-900/40 to-green-950/40 border border-green-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm">Aujourd'hui</p>
                <p className="text-3xl font-bold text-green-300">{stats.today}</p>
              </div>
              <Clock className="w-10 h-10 text-green-400 opacity-50" />
            </div>
          </div>

          <div className="bg-gradient-to-br from-purple-900/40 to-purple-950/40 border border-purple-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm">Mobile</p>
                <p className="text-3xl font-bold text-purple-300">{stats.mobile}</p>
              </div>
              <Smartphone className="w-10 h-10 text-purple-400 opacity-50" />
            </div>
          </div>

          <div className="bg-gradient-to-br from-cyan-900/40 to-cyan-950/40 border border-cyan-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm">Desktop</p>
                <p className="text-3xl font-bold text-cyan-300">{stats.desktop}</p>
              </div>
              <Monitor className="w-10 h-10 text-cyan-400 opacity-50" />
            </div>
          </div>
        </motion.div>

        {/* Graphique d'activité */}
        <motion.div
          className="bg-blue-950/20 border border-blue-700/50 rounded-xl p-6 mb-8 shadow-lg"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8, delay: 0.4 }}
        >
          <h2 className="text-xl font-semibold text-blue-300 mb-4">
            📊 Activité par heure (24h)
          </h2>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e3a5f" />
              <XAxis dataKey="hour" stroke="#60a5fa" />
              <YAxis stroke="#60a5fa" />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#0c1821",
                  border: "1px solid #1e40af",
                  borderRadius: "8px",
                }}
                labelStyle={{ color: "#60a5fa" }}
              />
              <Bar dataKey="count" fill="#3b82f6" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </motion.div>

        {/* Filtres */}
        <motion.div
          className="bg-blue-950/20 border border-blue-700/50 rounded-xl p-4 mb-6 shadow-lg"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, delay: 0.6 }}
        >
          <div className="flex flex-col md:flex-row gap-4 items-center">
            <div className="flex items-center gap-2">
              <Filter className="w-5 h-5 text-blue-400" />
              <span className="text-blue-300 font-semibold">Filtres :</span>
            </div>

            <select
              value={selectedDevice}
              onChange={(e) => setSelectedDevice(e.target.value)}
              className="bg-blue-900/40 border border-blue-700 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="all">Tous les appareils</option>
              <option value="mobile">Mobile uniquement</option>
              <option value="desktop">Desktop uniquement</option>
            </select>

            <input
              type="date"
              value={dateFilter}
              onChange={(e) => setDateFilter(e.target.value)}
              className="bg-blue-900/40 border border-blue-700 rounded-lg px-4 py-2 text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />

            {(selectedDevice !== "all" || dateFilter) && (
              <button
                onClick={() => {
                  setSelectedDevice("all");
                  setDateFilter("");
                }}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg text-sm font-semibold transition-colors"
              >
                Réinitialiser
              </button>
            )}
          </div>
        </motion.div>

        {/* Table des logs */}
        <motion.div
          className="bg-blue-950/20 border border-blue-700/50 rounded-xl overflow-hidden shadow-lg"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.8 }}
        >
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-blue-900/40 border-b border-blue-700">
                <tr>
                  <th className="px-4 py-3 text-left text-blue-300 font-semibold">
                    Horodatage UTC
                  </th>
                  <th className="px-4 py-3 text-left text-blue-300 font-semibold">
                    Appareil
                  </th>
                  <th className="px-4 py-3 text-left text-blue-300 font-semibold">
                    User Agent
                  </th>
                  <th className="px-4 py-3 text-left text-blue-300 font-semibold">
                    IP / Région
                  </th>
                </tr>
              </thead>
              <tbody>
                {filteredLogs.length === 0 ? (
                  <tr>
                    <td colSpan="4" className="px-4 py-8 text-center text-gray-400">
                      Aucun log disponible
                    </td>
                  </tr>
                ) : (
                  filteredLogs.map((log, index) => (
                    <motion.tr
                      key={log.id}
                      className="border-b border-blue-900/30 hover:bg-blue-900/20 transition-colors"
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ duration: 0.4, delay: index * 0.05 }}
                    >
                      <td className="px-4 py-3 text-gray-300 font-mono text-sm">
                        {log.utc_timestamp || "N/A"}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-semibold ${
                            log.device_type === "mobile"
                              ? "bg-purple-900/40 text-purple-300"
                              : "bg-cyan-900/40 text-cyan-300"
                          }`}
                        >
                          {log.device_type === "mobile" ? (
                            <Smartphone className="w-3 h-3" />
                          ) : (
                            <Monitor className="w-3 h-3" />
                          )}
                          {log.device_type || "inconnu"}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-400 text-sm max-w-xs truncate">
                        {log.user_agent || "Non disponible"}
                      </td>
                      <td className="px-4 py-3 text-gray-400 text-sm">
                        {log.ip_address || "N/A"} / {log.region || "N/A"}
                      </td>
                    </motion.tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination info */}
          <div className="bg-blue-900/20 px-4 py-3 text-center text-sm text-gray-400">
            Affichage de {filteredLogs.length} enregistrement(s)
          </div>
        </motion.div>

        {/* Footer */}
        <motion.div
          className="mt-8 mb-4 text-center text-xs text-gray-500"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 1, delay: 1.2 }}
        >
          <p>© 2025 Sentinel Quantum Vanguard AI Pro — Console Admin sécurisée</p>
        </motion.div>
      </div>
    </div>
  );
}
