import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function App() {
  const navigate = useNavigate();

  useEffect(() => {
    navigate("/dashboard");
  }, [navigate]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex items-center justify-center">
      <div className="text-center">
        <div className="text-6xl mb-4 animate-pulse">🛡️</div>
        <h1 className="text-3xl font-bold text-sentinel-blue mb-2">
          Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-zinc-400">Chargement du système...</p>
      </div>
    </div>
  );
}
