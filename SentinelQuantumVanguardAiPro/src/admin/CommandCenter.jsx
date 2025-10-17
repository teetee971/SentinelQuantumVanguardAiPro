import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";

export default function CommandCenter() {
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const reloadData = async () => {
    try {
      const data = await fetch("/telemetry/agents.json?cache=no-store").then(r => r.json());
      setAgents(data);
    } catch (e) {
      console.error("Erreur de chargement des agents", e);
    }
  };

  const runAction = async (agent, action) => {
    setLoading(true);
    setMessage(`⏳ ${action} sur ${agent.name}...`);
    try {
      // Simulation d'action IA (à relier à l'API Render/Cloudflare)
      await new Promise(r => setTimeout(r, 1200));
      setMessage(`✅ ${agent.name} : action ${action} exécutée.`);
    } catch (e) {
      setMessage(`❌ ${agent.name} : échec ${action}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { reloadData(); }, []);

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-950 to-gray-900 text-gray-100 p-6 flex flex-col items-center">
      <h1 className="text-3xl font-bold text-cyan-400 mb-4">🧠 Sentinel Command Center</h1>
      <p className="text-sm text-gray-400 mb-6">
        Gestion centralisée du réseau IA (Wave 22)
      </p>

      {message && (
        <div className="bg-gray-800 border border-cyan-500/20 rounded-lg px-4 py-2 mb-4 text-sm">
          {message}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 w-full max-w-6xl">
        {agents.map((a, i) => (
          <Card key={i} className="bg-gray-900/70 border border-cyan-500/20 shadow-lg">
            <CardContent className="p-4">
              <div className="flex items-center justify-between mb-2">
                <h2 className="font-semibold text-lg text-cyan-300">{a.name}</h2>
                <span
                  className={`text-xs px-2 py-1 rounded-full ${
                    a.status === "online"
                      ? "bg-cyan-500/20 text-cyan-300"
                      : "bg-red-500/20 text-red-400"
                  }`}
                >
                  {a.status}
                </span>
              </div>
              <p className="text-sm text-gray-400 mb-2">Latence : {a.latency} ms</p>
              <p className="text-xs text-gray-500 mb-3">Dernière MAJ : {a.updated}</p>

              <div className="flex gap-2">
                <button
                  onClick={() => runAction(a, "🔄 Restart")}
                  disabled={loading}
                  className="px-3 py-1 text-sm bg-cyan-600/30 hover:bg-cyan-600/50 rounded-lg transition"
                >
                  Redémarrer
                </button>
                <button
                  onClick={() => runAction(a, "🧹 Purge Cache")}
                  disabled={loading}
                  className="px-3 py-1 text-sm bg-purple-600/30 hover:bg-purple-600/50 rounded-lg transition"
                >
                  Purger
                </button>
                <button
                  onClick={() => runAction(a, "📡 Sync Render")}
                  disabled={loading}
                  className="px-3 py-1 text-sm bg-blue-600/30 hover:bg-blue-600/50 rounded-lg transition"
                >
                  Sync Render
                </button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <footer className="mt-8 text-gray-500 text-sm">
        Sentinel Quantum Vanguard AI Pro — Wave 22 Command Center
      </footer>
    </div>
  );
}
