import React from "react"
import AgentHealthCard from "./components/AgentHealthCard"

function App() {
  const agents = [
    "PegasusHunter",
    "QuantumPublisher",
    "NeoPackager",
    "ThreatMapGlobal",
    "InfraGuard",
    "BuildPilot",
    "Frontline-UI",
  ]

  return (
    <div className="min-h-screen bg-neutral-950 text-gray-100 relative overflow-hidden flex flex-col items-center">
      {/* Fond dynamique */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(34,211,238,0.08)_0%,transparent_70%)] animate-pulse-slow"></div>
      <div className="absolute inset-0 bg-[linear-gradient(45deg,rgba(0,255,255,0.05)_1px,transparent_1px)] bg-[length:40px_40px] opacity-30"></div>

      {/* En-tête */}
      <header className="relative z-10 w-full text-center mt-12">
        <h1 className="text-4xl md:text-5xl font-bold text-cyan-400 drop-shadow-[0_0_20px_rgba(34,211,238,0.6)]">
          Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-gray-400 mt-3">
          Réseau d'agents IA – Supervision et Orchestration en temps réel
        </p>
      </header>

      {/* Tableau des agents */}
      <main className="relative z-10 mt-10 w-full max-w-5xl px-4">
        <h2 className="text-xl text-cyan-300 mb-5 font-semibold">
          État des Agents IA
        </h2>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
          {agents.map((agent, i) => (
            <AgentHealthCard key={i} name={agent} status="online" />
          ))}
        </div>
      </main>

      {/* Pied de page */}
      <footer className="relative z-10 mt-12 text-xs text-gray-500 text-center pb-8">
        © Sentinel Quantum Vanguard AI Network — Live Supervision Mode
      </footer>
    </div>
  )
}

export default App

// === Section Logs IA Live ===
import { useEffect, useState } from "react"

function LiveLogs() {
  const [logs, setLogs] = useState([])

  useEffect(() => {
    const agents = [
      "PegasusHunter",
      "QuantumPublisher",
      "NeoPackager",
      "ThreatMapGlobal",
      "InfraGuard",
      "BuildPilot",
      "Frontline-UI",
    ]
    const interval = setInterval(() => {
      const agent = agents[Math.floor(Math.random() * agents.length)]
      const latency = (Math.random() * 80 + 20).toFixed(1)
      const msg = `[${new Date().toLocaleTimeString()}] ${agent} → ping=${latency}ms | status=OK`
      setLogs((prev) => [msg, ...prev.slice(0, 9)]) // garde 10 lignes max
    }, 1800)
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="relative z-10 w-full max-w-4xl mt-10 p-3 bg-neutral-900/60 rounded-lg border border-cyan-700/40 backdrop-blur-sm text-xs text-green-400 font-mono overflow-hidden">
      <div className="h-40 overflow-y-auto scrollbar-thin scrollbar-thumb-cyan-700/40 pr-2">
        {logs.map((log, i) => (
          <p key={i} className="animate-fade-in">{log}</p>
        ))}
      </div>
    </div>
  )
}

// Injecte la section logs en bas de App()
