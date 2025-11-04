import { useEffect, useState } from "react";
import { collection, onSnapshot, orderBy, query } from "firebase/firestore";
import { db } from "../firebaseConfig";

export default function VpnLogs() {
  const [logs, setLogs] = useState([]);

  useEffect(() => {
    const q = query(collection(db, "vpn_logs"), orderBy("timestamp", "desc"));
    const unsub = onSnapshot(q, (snapshot) => {
      const data = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data(),
      }));
      setLogs(data);
    });
    return () => unsub();
  }, []);

  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-lg shadow-xl p-4 max-h-[400px] overflow-y-auto">
      <h2 className="text-lg font-semibold text-white mb-4">Logs de Connexion VPN</h2>
      {logs.length === 0 ? (
        <p className="text-zinc-500 text-sm">Aucune connexion active pour le moment.</p>
      ) : (
        <ul className="space-y-3">
          {logs.map((log) => (
            <li
              key={log.id}
              className="flex justify-between items-center bg-zinc-800 px-3 py-2 rounded-lg hover:bg-zinc-700 transition"
            >
              <div>
                <p className="text-white text-sm font-medium">
                  {log.user || "Utilisateur inconnu"}
                </p>
                <p className="text-zinc-400 text-xs">
                  {log.country || "Pays non détecté"} • {new Date(log.timestamp.seconds * 1000).toLocaleString()}
                </p>
              </div>
              <span
                className={`text-xs font-bold px-2 py-1 rounded ${
                  log.status === "connected" ? "bg-green-600 text-white" : "bg-red-600 text-white"
                }`}
              >
                {log.status === "connected" ? "Connecté" : "Déconnecté"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
