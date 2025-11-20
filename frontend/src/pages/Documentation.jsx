import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";

export default function Documentation() {
  const navigate = useNavigate();
  const [dots, setDots] = useState("");

  // Animation de points de chargement
  useEffect(() => {
    const interval = setInterval(() => {
      setDots((prev) => (prev.length < 3 ? prev + "." : ""));
    }, 500);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-[#0a0a0a] text-gray-200 flex flex-col items-center justify-center px-4">
      {/* Logo & Titre */}
      <div className="flex flex-col items-center space-y-4">
        <img
          src="/logo192.png"
          alt="Sentinel Logo"
          className="w-16 h-16 animate-pulse opacity-90"
          loading="lazy"
          width="64"
          height="64"
        />
        <h1 className="text-3xl font-bold text-center text-[#00BFFF]">
          Sentinel Quantum Vanguard AI Pro™
        </h1>
        <p className="text-gray-400 text-center italic text-sm">
          “La sécurité du futur, dès aujourd’hui”
        </p>
      </div>

      {/* Zone de chargement */}
      <div className="mt-10 text-center">
        <p className="text-lg text-gray-300">
          Chargement de la documentation Sentinel Quantum Vanguard AI Pro{dots}
        </p>
        <div className="mt-6 animate-spin rounded-full h-10 w-10 border-t-2 border-[#00BFFF] border-opacity-70 mx-auto"></div>
      </div>

      {/* Bouton retour */}
      <button
        onClick={() => navigate("/")}
        className="btn mt-10 px-6 py-3 rounded-lg bg-[#00BFFF] hover:bg-[#0099cc] text-black font-semibold transition duration-200"
      >
        ← Retour Console
      </button>

      {/* Footer */}
      <footer className="absolute bottom-4 text-xs text-gray-600">
        © 2025 Sentinel Quantum Vanguard AI Pro – Système IA supervisé
      </footer>
    </div>
  );
}
