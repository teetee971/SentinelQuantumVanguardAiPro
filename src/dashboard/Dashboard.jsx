import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";

export default function Dashboard() {
  const [telemetry, setTelemetry] = useState(null);
  const [orchestrator, setOrchestrator] = useState(null);

  useEffect(() => {
    const load = async () => {
      try {
        const t = await fetch("/telemetry/data.json?cache=no-store").then(r => r.json());
        const o = await fetch("/telemetry/orchestrator.json?cache=no-store").then(r => r.json());
        setTelemetry(t);
        setOrchestrator(o);
      } catch (e) {
        console.error("Erreur chargement données", e);
      }
    };
    load();
    const interval = setInterval(load, 300000); // rafraîchit toutes les 5 min
    return () => clearInterval(interval);
  }, []);

  const chartData = telemetry?.raw
    ? telemetry.raw.split("\n").slice(-10).map((l, i) => ({
        name: `T${i}`,
        value: l.length
      }))
    : [];

  return (
    <div className="min
# 1️⃣ Se placer à la racine
cd ~/SentinelQuantumVanguardAiPro

# 2️⃣ Créer le frontend React (si non présent)
mkdir -p src/dashboard
cat > src/dashboard/Dashboard.jsx <<'EOF'
import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";

export default function Dashboard() {
  const [telemetry, setTelemetry] = useState(null);
  const [orchestrator, setOrchestrator] = useState(null);

  useEffect(() => {
    const load = async () => {
      try {
        const t = await fetch("/telemetry/data.json?cache=no-store").then(r => r.json());
        const o = await fetch("/telemetry/orchestrator.json?cache=no-store").then(r => r.json());
        setTelemetry(t);
        setOrchestrator(o);
      } catch (e) {
        console.error("Erreur chargement données", e);
      }
    };
    load();
    const interval = setInterval(load, 300000); // rafraîchit toutes les 5 min
    return () => clearInterval(interval);
  }, []);

  const chartData = telemetry?.raw
    ? telemetry.raw.split("\n").slice(-10).map((l, i) => ({
        name: `T${i}`,
        value: l.length
      }))
    : [];

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-950 to-gray-900 text-gray-100 flex flex-col items-center p-4">
      <h1 className="text-3xl font-bold text-cyan-400 mb-2">🧠 Sentinel Quantum Dashboard</h1>
      <p className="text-sm text-gray-400 mb-6">
        Suivi temps réel – Waves 9→19 & Auto-Healing
      </p>

      {orchestrator && (
        <Card className="w-full max-w-3xl mb-6 bg-gray-900/80 border border-cyan-500/20 shadow-xl">
          <CardContent className="p-4">
            <p><strong>Date :</strong> {orchestrator.timestamp}</p>
            <p><strong>Waves OK :</strong> {orchestrator.waves_ok}</p>
            <p><strong>Waves Erreur :</strong> {orchestrator.waves_err}</p>
            <p><strong>Orchestrateur :</strong> {orchestrator.orchestrator}</p>
          </CardContent>
        </Card>
      )}

      <Card className="w-full max-w-3xl bg-gray-900/80 border border-cyan-500/20 shadow-xl">
        <CardContent className="p-4">
          <h2 className="text-xl text-cyan-300 mb-3">Activité IA (dernières 10 entrées)</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
              <XAxis dataKey="name" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
              <Tooltip />
              <Line type="monotone" dataKey="value" stroke="#00ffe0" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>
      <footer className="mt-8 text-gray-500 text-sm">© Sentinel Quantum Vanguard AI Pro – Wave 20 UI Module</footer>
    </div>
  );
}
