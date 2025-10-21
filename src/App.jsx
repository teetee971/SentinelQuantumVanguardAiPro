import React, { useEffect, useState } from "react";

function InstallButton() {
  const [prompt, setPrompt] = useState(null);
  const [visible, setVisible] = useState(false);
  const [installed, setInstalled] = useState(false);

  useEffect(() => {
    const handler = (e) => {
      e.preventDefault();
      setPrompt(e);
      setVisible(true);
    };
    window.addEventListener("beforeinstallprompt", handler);
    window.addEventListener("appinstalled", () => {
      setInstalled(true);
      setVisible(false);
    });
    return () => {
      window.removeEventListener("beforeinstallprompt", handler);
    };
  }, []);

  const handleInstall = async () => {
    if (!prompt) return;
    prompt.prompt();
    const { outcome } = await prompt.userChoice;
    if (outcome === "accepted") {
      setVisible(false);
      setInstalled(true);
    }
  };

  if (installed) {
    return (
      <div className="fixed bottom-6 right-6 bg-green-600 text-white font-semibold px-5 py-3 rounded-2xl shadow-[0_0_25px_rgba(34,197,94,0.6)] backdrop-blur-md border border-white/10">
        ✅ Installé
      </div>
    );
  }

  if (!visible) return null;

  return (
    <button
      onClick={handleInstall}
      className="fixed bottom-6 right-6 text-white font-semibold px-5 py-3 rounded-2xl 
      border border-white/10 backdrop-blur-md 
      bg-gradient-to-r from-blue-500 via-indigo-500 to-purple-600 
      shadow-[0_0_30px_rgba(99,102,241,0.7)] 
      hover:shadow-[0_0_45px_rgba(99,102,241,0.9)] 
      transition-all duration-500 ease-in-out 
      animate-[pulse_2s_ease-in-out_infinite]"
    >
      Installer Sentinel AI Pro
    </button>
  );
}

function App() {
  return (
    <div className="min-h-screen bg-[#0d1117] text-white font-sans flex flex-col items-center justify-center p-6">
      <header className="text-center mb-10">
        <img
          src="/assets/sentinel_logo.png"
          alt="Sentinel Logo"
          className="w-24 h-24 mx-auto mb-4 drop-shadow-[0_0_25px_rgba(59,130,246,0.6)]"
        />
        <h1 className="text-3xl font-bold text-blue-400 mb-2">
          Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-gray-400 italic">
          "La sécurité du futur, dès aujourd’hui"
        </p>
      </header>

      <main className="max-w-2xl text-center space-y-6">
        <div className="bg-[#111827] rounded-2xl p-6 shadow-lg border border-blue-900/30">
          <h2 className="text-xl font-semibold text-blue-300 mb-2">
            Modules actifs
          </h2>
          <ul className="space-y-2 text-gray-300">
            <li>🧠 IA Prédictive (analyse comportementale)</li>
            <li>🛡️ Quantum Shield (protection réseau)</li>
            <li>🧬 CognitiveTraceAgent (surveillance IA)</li>
            <li>🌐 GlobalFailoverWatcher (redondance mondiale)</li>
          </ul>
        </div>

        <div className="bg-[#111827] rounded-2xl p-6 shadow-lg border border-blue-900/30">
          <h2 className="text-xl font-semibold text-blue-300 mb-2">
            Statut du Système
          </h2>
          <p className="text-green-400 font-medium">
            ✅ Système IA opérationnel et stable
          </p>
          <p className="text-gray-400 text-sm mt-1">
            Dernière vérification : en temps réel
          </p>
        </div>
      </main>

      {/* Bouton d’installation flottant */}
      <InstallButton />
    </div>
  );
}

export default App;
