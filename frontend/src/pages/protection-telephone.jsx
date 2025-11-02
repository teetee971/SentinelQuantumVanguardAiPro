import React, { useState } from "react";
import { PhoneCall, AlertTriangle } from "lucide-react";
import { motion } from "framer-motion";

export default function ProtectionTelephone() {
  const [status, setStatus] = useState("Prêt à surveiller vos appels.");
  const [alerts, setAlerts] = useState([]);

  const simulateCall = () => {
    const fakeCall = Math.random() > 0.7;
    if (fakeCall) {
      setAlerts((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          type: "Appel suspect détecté",
          number: "+33 6 75 23 98 41",
          level: "Fraude probable",
        },
      ]);
      setStatus("⚠️ Alerte IA : appel frauduleux bloqué.");
    } else {
      setStatus("✅ Aucun appel suspect détecté.");
    }
  };

  return (
    <section className="min-h-screen bg-black text-white flex flex-col items-center justify-center p-6">
      <div className="max-w-md w-full text-center border border-blue-800 bg-blue-950/20 rounded-2xl p-6 shadow-lg">
        <PhoneCall className="w-12 h-12 text-blue-400 mx-auto mb-4" />
        <h1 className="text-2xl font-bold text-blue-400 mb-2">📞 Protection Téléphonique Sentinel</h1>
        <p className="text-gray-400 mb-6">
          Détection IA des appels suspects, fraudes et usurpations d'identité en temps réel.
        </p>

        <button
          onClick={simulateCall}
          className="bg-blue-600 hover:bg-blue-700 px-6 py-3 rounded-xl text-white font-semibold transition-all"
        >
          📲 Lancer la surveillance IA
        </button>

        <p className="text-sm text-gray-400 mt-4">{status}</p>

        <div className="mt-6 text-left space-y-3">
          {alerts.map((a) => (
            <motion.div
              key={a.id}
              className="border border-red-600 bg-red-900/20 p-3 rounded-xl"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
            >
              <AlertTriangle className="inline w-4 h-4 text-red-400 mr-2" />
              <strong>{a.type}</strong> — {a.number} ({a.level})
            </motion.div>
          ))}
        </div>
      </div>

      <p className="text-gray-500 text-xs mt-8">
        Simulation IA — Module téléphonique Sentinel Quantum Vanguard AI Pro
      </p>

      {/* Navigation */}
      <div className="mt-8 flex gap-4">
        <a
          href="/"
          className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
        >
          ← Retour à l'accueil
        </a>
      </div>
    </section>
  );
}
