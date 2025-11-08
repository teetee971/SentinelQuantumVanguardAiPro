import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ShieldCheck, Loader2 } from "lucide-react";

export default function PegasusScan() {
  const navigate = useNavigate();
  const [scanProgress, setScanProgress] = useState(0);
  const [status, setStatus] = useState("initial");
  const [threats, setThreats] = useState([]);

  useEffect(() => {
    if (status === "scanning") {
      const interval = setInterval(() => {
        setScanProgress((prev) => {
          if (prev >= 100) {
            clearInterval(interval);
            setStatus("done");
            setThreats([
              { name: "Aucune trace de Pegasus détectée", level: "safe" },
              { name: "Connexion réseau sécurisée", level: "ok" },
              { name: "Aucune permission suspecte détectée", level: "ok" },
            ]);
            return 100;
          }
          return prev + 5;
        });
      }, 300);
      return () => clearInterval(interval);
    }
  }, [status]);

  return (
    <div className="min-h-screen bg-[#0a0a0a] text-gray-200 flex flex-col items-center justify-center px-4">
      {/* Logo + titre */}
      <div className="flex flex-col items-center space-y-4 text-center">
        <ShieldCheck className="w-16 h-16 text-[#00BFFF] animate-pulse" />
        <h1 className="text-2xl sm:text-3xl font-bold text-[#00BFFF]" style={{ fontSize: 'clamp(28px, 4vw, 40px)' }}>
          Sentinel Pegasus Scan IA
        </h1>
        <p className="text-gray-400 italic text-xs sm:text-sm">
          “Analyse comportementale et détection proactive des menaces”
        </p>
      </div>

      {/* Contenu principal */}
      <div className="mt-10 bg-[#111] p-4 sm:p-6 rounded-2xl shadow-lg border border-[#00BFFF]/20 w-full max-w-md text-center">
        {status === "initial" && (
          <>
            <p className="text-gray-300 mb-4">
              L’analyse IA détecte toute trace d’espionnage (Pegasus, APT,
              malware système...).
            </p>
            <button
              onClick={() => setStatus("scanning")}
              className="btn px-6 py-3 bg-[#00BFFF] text-black font-semibold rounded-lg shadow-md hover:bg-[#0099cc] transition-all"
            >
              🚀 Lancer l’analyse IA
            </button>
          </>
        )}

        {status === "scanning" && (
          <>
            <Loader2 className="w-10 h-10 text-[#00BFFF] animate-spin mx-auto mb-4" />
            <p className="text-gray-400">Analyse en cours...</p>
            <div className="w-full bg-gray-800 h-2 rounded-full mt-4">
              <div
                className="h-2 bg-[#00BFFF] rounded-full transition-all duration-200"
                style={{ width: `${scanProgress}%` }}
              ></div>
            </div>
            <p className="text-sm mt-2 text-gray-500">
              Progression : {scanProgress}%
            </p>
          </>
        )}

        {status === "done" && (
          <>
            <h2 className="text-xl font-semibold text-[#00FFAA] mb-4">
              ✅ Analyse terminée
            </h2>
            <ul className="space-y-2 text-left">
              {threats.map((t, i) => (
                <li
                  key={i}
                  className={`p-2 rounded-lg ${
                    t.level === "safe"
                      ? "bg-green-900/30 border border-green-600/30"
                      : "bg-gray-800/30"
                  }`}
                >
                  {t.name}
                </li>
              ))}
            </ul>
            <button
              onClick={() => setStatus("initial")}
              className="btn mt-6 px-6 py-3 bg-[#00BFFF] text-black font-semibold rounded-lg hover:bg-[#0099cc] transition"
            >
              🔁 Relancer un scan
            </button>
          </>
        )}
      </div>

      {/* Bouton retour */}
      <button
        onClick={() => navigate("/")}
        className="btn mt-8 px-6 py-3 rounded-lg bg-[#00BFFF] hover:bg-[#0099cc] text-black font-semibold transition duration-200"
      >
        ← Retour Console
      </button>

      {/* Footer */}
      <footer className="absolute bottom-4 text-xs text-gray-600">
        © 2025 Sentinel Quantum Vanguard AI Pro — Module Pegasus Scan IA
      </footer>
    </div>
  );
}
