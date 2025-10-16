import { useState, useEffect } from "react";
import AgentCard from "./AgentCard";
import LogsPanel from "./LogsPanel";
import StatusChart from "./StatusChart";
import MapView from "./MapView";

export default function Dashboard() {
  const [agents, setAgents] = useState([]);
  const [logs, setLogs] = useState([]);

  useEffect(() => {
    // Simulation du réseau Sentinel
    setAgents([
      { name: "QuantumFailoverAI", status: "active", latency: 42 },
      { name: "GlobalFailoverWatcher", status: "active", latency: 65 },
      { name: "SessionIntegritySentinel", status: "idle", latency: 88 },
      { name: "HeuristicPredictorAI", status: "error", latency: 0 },
    ]);

    const interval = setInterval(() => {
      setLogs((prev) => [
        ...prev.slice(-30),
        `[${new Date().toLocaleTimeString()}] Ping OK – ${Math.floor(
          Math.random() * 200
        )}ms`,
      ]);
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 to-black text-white p-6 font-sans">
      <header className="flex justify-between items-center mb-8">
        <h1 className="text-3xl font-bold tracking-wide">
          🛡️ Sentinel Quantum Vanguard AI Dashboard
        </h1>
        <a
          href="/admin"
          className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 transition"
        >
          Accès Admin
        </a>
      </header>

      <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        {agents.map((a) => (
          <AgentCard key={a.name} agent={a} />
        ))}
      </section>

      <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <LogsPanel logs={logs} />
        <StatusChart agents={agents} />
      </section>

      <section className="mt-8">
        <MapView />
      </section>

      <footer className="mt-10 text-gray-500 text-sm text-center">
        Sentinel Quantum Vanguard AI © 2025 – Live Diagnostic Engine
      </footer>
    </div>
  );
}