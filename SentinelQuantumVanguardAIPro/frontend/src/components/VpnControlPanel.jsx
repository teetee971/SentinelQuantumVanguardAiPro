import { useState, useEffect } from "react";
import { doc, updateDoc, onSnapshot, serverTimestamp } from "firebase/firestore";
import { db } from "../firebaseConfig";

export default function VpnControlPanel() {
  const [status, setStatus] = useState("indéterminé");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  // Écoute en temps réel de l'état VPN Firestore
  useEffect(() => {
    const ref = doc(db, "vpn_control", "main");
    const unsub = onSnapshot(ref, (snap) => {
      if (snap.exists()) {
        setStatus(snap.data().status || "indéterminé");
      }
    });
    return () => unsub();
  }, []);

  const handleAction = async (action) => {
    setLoading(true);
    try {
      const ref = doc(db, "vpn_control", "main");
      await updateDoc(ref, {
        action,
        updatedAt: serverTimestamp(),
      });
      setMessage(`Commande "${action}" envoyée ✅`);
    } catch (err) {
      console.error(err);
      setMessage("Erreur de communication avec le serveur ❌");
    }
    setLoading(false);
  };

  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-lg shadow-lg p-6 mt-10 text-center">
      <h2 className="text-lg font-semibold text-white mb-4">
        Panneau de Contrôle VPN
      </h2>

      <div className="flex items-center justify-center mb-4">
        <span
          className={`w-3 h-3 rounded-full mr-2 ${
            status === "actif"
              ? "bg-green-500 animate-pulse"
              : status === "inactif"
              ? "bg-red-500"
              : "bg-yellow-500"
          }`}
        ></span>
        <p className="text-sm text-zinc-400">
          État du VPN :{" "}
          <span className="text-white font-semibold">{status}</span>
        </p>
      </div>

      <div className="flex flex-col md:flex-row justify-center gap-4">
        <button
          onClick={() => handleAction("start")}
          disabled={loading}
          className="bg-green-600 hover:bg-green-500 text-white font-medium px-4 py-2 rounded-md transition"
        >
          Démarrer VPN
        </button>
        <button
          onClick={() => handleAction("stop")}
          disabled={loading}
          className="bg-red-600 hover:bg-red-500 text-white font-medium px-4 py-2 rounded-md transition"
        >
          Arrêter VPN
        </button>
        <button
          onClick={() => handleAction("refresh")}
          disabled={loading}
          className="bg-blue-600 hover:bg-blue-500 text-white font-medium px-4 py-2 rounded-md transition"
        >
          Rafraîchir Serveurs
        </button>
      </div>

      {message && (
        <p className="text-sm text-zinc-400 mt-4">{message}</p>
      )}
    </div>
  );
}
