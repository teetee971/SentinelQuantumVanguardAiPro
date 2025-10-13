// src/SentinelAI.jsx
import React, { useEffect, useState } from "react";
import { db } from "./firebaseConfig";
import { collection, onSnapshot, query, orderBy, limit } from "firebase/firestore";

export default function SentinelAI() {
  const [logs, setLogs] = useState([]);

  useEffect(() => {
    const q = query(collection(db, "sentinel_logs"), orderBy("timestamp", "desc"), limit(10));
    const unsub = onSnapshot(q, (snapshot) => {
      const entries = snapshot.docs.map((doc) => ({
        id: doc.id,
        ...doc.data(),
      }));
      setLogs(entries.reverse());
    });
    return () => unsub();
  }, []);

  return (
    <div className="w-full max-w-2xl bg-gray-900/50 border border-cyan-700/30 rounded-xl p-5 backdrop-blur-md shadow-lg font-mono text-sm">
      <h3 className="text-cyan-400 font-semibold mb-3">🧠 Journal IA Live</h3>
      <div className="h-64 overflow-y-auto space-y-1 text-gray-300">
        {logs.length === 0 ? (
          <p className="text-gray-500">[•] En attente des premiers logs IA...</p>
        ) : (
          logs.map((log) => (
            <p key={log.id}>
              <span className="text-cyan-500">[{new Date(log.timestamp?.seconds * 1000).toLocaleTimeString()}]</span>{" "}
              {log.message}
            </p>
          ))
        )}
      </div>
    </div>
  );
}