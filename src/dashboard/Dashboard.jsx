import React, { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Line } from "react-chartjs-2";
import "chart.js/auto";
import { initializeApp } from "firebase/app";
import { getFirestore, collection, onSnapshot } from "firebase/firestore";

// ✅ Configuration Firebase (à vérifier selon ton .env ou firebase.json)
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

export default function Dashboard() {
  const [status, setStatus] = useState("Initialisation du réseau IA...");
  const [activity, setActivity] = useState([12, 18, 9, 14, 22, 19, 25]);
  const [agents, setAgents] = useState([]);

  // 🔄 Synchronisation Firestore en temps réel
  useEffect(() => {
    const unsub = onSnapshot(collection(db, "agents_status"), (snapshot) => {
      const data = snapshot.docs.map((doc) => ({
        id: doc.id,
        ...doc.data(),
      }));
      setAgents(data);
      setStatus("🧠 Réseau IA connecté à Firebase");
    });

    return () => unsub();
  }, []);

  const data = {
    labels: ["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"],
    datasets: [
      {
        label: "Activité IA (agents connectés)",
        data: activity,
        fill: true,
        borderColor: "#00BFFF",
        backgroundColor: "rgba(0,191,255,0.1)",
        tension: 0.3,
      },
    ],
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-950 via-gray-900 to-black text-white px-6 py-12">
      <motion.h1
        className="text-4xl font-bold text-center mb-6 text-blue-400"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        Sentinel Quantum Vanguard AI Pro — Tableau de Bord
      </motion.h1>

      <div className="text-center text-gray-300 mb-8">{status}</div>

      {/* 📊 Graphique d’activité IA */}
      <div className="max-w-3xl mx-auto bg-gray-900/60 rounded-2xl shadow-lg p-6 backdrop-blur-sm border border-gray-800">
        <h2 className="text-xl font-semibold mb-4 text-blue-300">Activité Réseau IA</h2>
        <Line data={data} />
      </div>

      {/* 🧠 Liste agents Firebase */}
      <div className="max-w-3xl mx-auto mt-10 bg-gray-900/60 rounded-2xl p-6 border border-gray-800">
        <h2 className="text-xl text-blue-300 mb-4 font-semibold">Agents connectés</h2>
        {agents.length === 0 ? (
          <p className="text-gray-500 text-sm">Aucun agent actif détecté...</p>
        ) : (
          <ul className="divide-y divide-gray-700">
            {agents.map((agent) => (
              <li key={agent.id} className="py-3 flex justify-between">
                <span className="text-gray-200 font-medium">{agent.name || agent.id}</span>
                <span className="text-blue-400 text-sm">
                  {agent.status || "inactif"}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* ⚙️ Modules système */}
      <div className="max-w-3xl mx-auto mt-10 grid grid-cols-1 md:grid-cols-3 gap-4">
        {[
          { title: "Quantum Shield", desc: "Bouclier de cybersécurité quantique." },
          { title: "OSINT Scanner", desc: "Veille IA sur les menaces globales." },
          { title: "Auto-Healing", desc: "Réparation et rollback intelligents." },
        ].map((item, i) => (
          <motion.div
            key={i}
            className="bg-gray-800/60 p-4 rounded-xl border border-gray-700 hover:border-blue-500 transition"
            whileHover={{ scale: 1.05 }}
          >
            <h3 className="text-lg text-blue-400 font-semibold">{item.title}</h3>
            <p className="text-gray-400 text-sm mt-2">{item.desc}</p>
          </motion.div>
        ))}
      </div>
    </div>
  );
}
