import { useEffect } from "react";

export default function SentinelAI() {
  useEffect(() => {
    document.title = "Sentinel Quantum Vanguard AI Pro";
  }, []);

  return (
    <div className="relative min-h-screen flex flex-col items-center justify-center bg-gradient-to-b from-black via-[#00151f] to-black text-cyan-100 overflow-hidden">
      {/* Halo de fond animé */}
      <div className="absolute w-[38rem] h-[38rem] bg-cyan-500/20 rounded-full blur-[150px] animate-pulse-slow -z-10"></div>

      {/* Barre supérieure */}
      <header className="fixed top-0 left-0 w-full bg-black/40 backdrop-blur-md border-b border-cyan-900/40 p-3 flex justify-between items-center z-20">
        <h1 className="text-lg md:text-xl font-semibold tracking-wide text-cyan-400">
          🛡️ Sentinel Core
        </h1>
        <div className="text-sm font-mono text-cyan-300 opacity-80">
          [ STATUS: <span className="text-green-400">ONLINE</span> ]
        </div>
      </header>

      {/* Section principale */}
      <main className="flex flex-col items-center justify-center px-6 py-20 text-center">
        <div className="glass p-10 rounded-2xl shadow-2xl border border-cyan-900/30 max-w-xl">
          <h2 className="text-3xl font-bold text-cyan-400 mb-4">
            Quantum Defense Matrix
          </h2>
          <p className="text-cyan-100/80 leading-relaxed mb-6">
            The Sentinel AI Core monitors real-time threats across all connected networks.
            Each data pulse is analyzed, encrypted, and routed through the Quantum Shield Layer.
          </p>

          <div className="flex gap-4 justify-center">
            <button className="px-5 py-2 bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-400/40 rounded-lg text-cyan-300 font-semibold transition-all duration-300">
              Open Dashboard
            </button>
            <button className="px-5 py-2 bg-cyan-800/30 hover:bg-cyan-700/40 border border-cyan-400/30 rounded-lg text-cyan-200 font-semibold transition-all duration-300">
              System Scan
            </button>
          </div>
        </div>

        {/* Log terminal */}
        <div className="mt-10 font-mono text-cyan-400/70 text-xs sm:text-sm bg-black/40 p-4 rounded-lg border border-cyan-900/40 w-[90%] sm:w-[70%] text-left shadow-inner">
          <p>[SYS] Uplink integrity: 99.998%</p>
          <p>[CORE] Neural sync active on 4 nodes</p>
          <p>[LOG] Telemetry stream nominal</p>
          <p>[SEC] Quantum firewall status: STABLE</p>
        </div>
      </main>

      {/* Pied de page */}
      <footer className="absolute bottom-3 text-xs text-cyan-600/60 font-mono">
        © 2025 Sentinel Quantum Vanguard AI Pro • Secure Infrastructure v3.2
      </footer>
    </div>
  );
}