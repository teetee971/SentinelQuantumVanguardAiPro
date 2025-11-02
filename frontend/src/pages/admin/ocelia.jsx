import React, { useEffect, useState, useMemo } from "react";
import { collection, onSnapshot, doc } from "firebase/firestore";
import { db } from "../../firebase/config";
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from "recharts";
import { ShieldCheck, AlertTriangle, PhoneIncoming, Activity, TrendingUp } from "lucide-react";
import { motion } from "framer-motion";
import Navbar from "../../components/Navbar";

export default function OceliaAdmin() {
  const [calls, setCalls] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubCalls = onSnapshot(
      collection(db, "ocelia_calls"),
      (snapshot) => {
        const data = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
        setCalls(data.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)));
        setLoading(false);
      },
      (error) => {
        console.error("Erreur Firestore calls:", error);
        setLoading(false);
      }
    );

    const unsubSummary = onSnapshot(
      doc(db, "sentinel_reports", "ocelia_summary"),
      (docSnap) => {
        if (docSnap.exists()) {
          setSummary(docSnap.data());
        }
      },
      (error) => {
        console.error("Erreur Firestore summary:", error);
      }
    );

    return () => {
      unsubCalls();
      unsubSummary();
    };
  }, []);

  const COLORS = ["#FF4C4C", "#FFD93D", "#00DFA2"];

  const stats = useMemo(() => {
    const completed = calls.filter(c => c.status === "call_completed");
    const frauds = completed.filter(c => c.category === "fraud").length;
    const moderate = completed.filter(c => c.category === "moderate").length;
    const legit = completed.filter(c => c.category === "legit").length;
    
    return {
      total: completed.length,
      frauds,
      moderate,
      legit,
      fraudRate: completed.length > 0 ? ((frauds / completed.length) * 100).toFixed(1) : 0
    };
  }, [calls]);

  const chartData = [
    { name: "Fraudes détectées", value: stats.frauds },
    { name: "Indéterminés", value: stats.moderate },
    { name: "Légitimes", value: stats.legit },
  ].filter(item => item.value > 0);

  const recentCalls = calls.filter(c => c.status === "call_completed").slice(0, 10);

  return (
    <>
      <Navbar />
      <section className="min-h-screen bg-black text-white p-6 md:p-12 pt-24">
        <div className="max-w-7xl mx-auto">
          <motion.h1
            className="text-3xl md:text-4xl font-bold text-blue-400 mb-6"
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7 }}
          >
            🎙️ Admin OCELIA – IA Vocale Défensive
          </motion.h1>
          
          <p className="text-gray-400 mb-8">
            Supervision en temps réel des appels traités par l'assistant vocal OCELIA Sentinel
          </p>

          {/* Statistics Cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-10">
            <motion.div
              className="bg-gradient-to-br from-blue-950/40 to-blue-900/20 border border-blue-800 rounded-xl p-6 shadow-lg"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 }}
            >
              <PhoneIncoming className="w-8 h-8 text-blue-400 mb-2" />
              <p className="text-3xl font-bold text-blue-300">{stats.total}</p>
              <p className="text-sm text-gray-400">Appels analysés</p>
            </motion.div>

            <motion.div
              className="bg-gradient-to-br from-red-950/40 to-red-900/20 border border-red-800 rounded-xl p-6 shadow-lg"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
            >
              <AlertTriangle className="w-8 h-8 text-red-400 mb-2" />
              <p className="text-3xl font-bold text-red-300">{stats.frauds}</p>
              <p className="text-sm text-gray-400">Fraudes détectées</p>
            </motion.div>

            <motion.div
              className="bg-gradient-to-br from-green-950/40 to-green-900/20 border border-green-800 rounded-xl p-6 shadow-lg"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
            >
              <ShieldCheck className="w-8 h-8 text-green-400 mb-2" />
              <p className="text-3xl font-bold text-green-300">{stats.legit}</p>
              <p className="text-sm text-gray-400">Appels légitimes</p>
            </motion.div>

            <motion.div
              className="bg-gradient-to-br from-yellow-950/40 to-yellow-900/20 border border-yellow-800 rounded-xl p-6 shadow-lg"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.4 }}
            >
              <TrendingUp className="w-8 h-8 text-yellow-400 mb-2" />
              <p className="text-3xl font-bold text-yellow-300">{stats.fraudRate}%</p>
              <p className="text-sm text-gray-400">Taux de fraude</p>
            </motion.div>
          </div>

          {/* Chart and Recent Calls */}
          <div className="grid md:grid-cols-2 gap-8 mb-10">
            {/* Pie Chart */}
            <motion.div
              className="bg-blue-950/30 border border-blue-800 rounded-2xl p-6 shadow-lg"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.5 }}
            >
              <h3 className="text-blue-300 font-semibold mb-4 flex items-center">
                <Activity className="w-5 h-5 mr-2" />
                Répartition des appels analysés
              </h3>
              {chartData.length > 0 ? (
                <ResponsiveContainer width="100%" height={280}>
                  <PieChart>
                    <Pie
                      data={chartData}
                      cx="50%"
                      cy="50%"
                      outerRadius={100}
                      dataKey="value"
                      label={(entry) => `${entry.value}`}
                    >
                      {chartData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#0f172a",
                        border: "1px solid #3b82f6",
                        borderRadius: "8px",
                      }}
                    />
                    <Legend wrapperStyle={{ color: "#93c5fd" }} />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <p className="text-gray-500 text-center py-20">Aucune donnée disponible</p>
              )}
            </motion.div>

            {/* Recent Calls List */}
            <motion.div
              className="bg-blue-950/30 border border-blue-800 rounded-2xl p-6 shadow-lg"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.6 }}
            >
              <h3 className="text-blue-300 font-semibold mb-4">Derniers appels traités</h3>
              <div className="space-y-3 max-h-[300px] overflow-y-auto">
                {recentCalls.length > 0 ? (
                  recentCalls.map((call, index) => (
                    <motion.div
                      key={call.id}
                      className={`p-3 rounded-lg border ${
                        call.category === "fraud"
                          ? "bg-red-950/20 border-red-800"
                          : call.category === "moderate"
                          ? "bg-yellow-950/20 border-yellow-800"
                          : "bg-green-950/20 border-green-800"
                      }`}
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 0.7 + index * 0.05 }}
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <p className={`font-semibold text-sm ${
                            call.category === "fraud"
                              ? "text-red-400"
                              : call.category === "moderate"
                              ? "text-yellow-300"
                              : "text-green-400"
                          }`}>
                            {call.result || "Analyse IA"}
                          </p>
                          <p className="text-xs text-gray-500">
                            {new Date(call.timestamp).toLocaleString("fr-FR")}
                          </p>
                        </div>
                        {call.category === "fraud" ? (
                          <AlertTriangle className="w-5 h-5 text-red-400" />
                        ) : call.category === "moderate" ? (
                          <Activity className="w-5 h-5 text-yellow-300" />
                        ) : (
                          <ShieldCheck className="w-5 h-5 text-green-400" />
                        )}
                      </div>
                    </motion.div>
                  ))
                ) : (
                  <p className="text-gray-500 text-center py-10">Aucun appel enregistré</p>
                )}
              </div>
            </motion.div>
          </div>

          {/* Summary Report */}
          {summary && (
            <motion.div
              className="bg-blue-950/20 border border-blue-800 rounded-2xl p-6 shadow-lg"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.8 }}
            >
              <h3 className="text-blue-300 font-semibold mb-4">Rapport IA automatique</h3>
              <div className="grid md:grid-cols-3 gap-4">
                <div>
                  <p className="text-gray-400 text-sm">Total fraudes</p>
                  <p className="text-2xl font-bold text-red-400">{summary.frauds || 0}</p>
                </div>
                <div>
                  <p className="text-gray-400 text-sm">Total légitimes</p>
                  <p className="text-2xl font-bold text-green-400">{summary.legit || 0}</p>
                </div>
                <div>
                  <p className="text-gray-400 text-sm">Dernière mise à jour</p>
                  <p className="text-sm text-gray-500">
                    {summary.updatedAt ? new Date(summary.updatedAt).toLocaleString("fr-FR") : "N/A"}
                  </p>
                </div>
              </div>
            </motion.div>
          )}

          <p className="text-gray-500 text-xs mt-8 text-center">
            Données synchronisées en temps réel — Sentinel Quantum Vanguard AI Pro
          </p>
        </div>
      </section>
    </>
  );
}
