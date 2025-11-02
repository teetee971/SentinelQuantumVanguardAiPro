import { useState } from "react";

export default function Telechargement() {
  const [pdfStatus, setPdfStatus] = useState("checking");

  const pdfUrl = "/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf";

  // Vérifier si le PDF existe
  useState(() => {
    fetch(pdfUrl, { method: "HEAD" })
      .then(response => {
        if (response.ok) {
          setPdfStatus("available");
        } else {
          setPdfStatus("unavailable");
        }
      })
      .catch(() => setPdfStatus("unavailable"));
  }, []);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pt-24 pb-12 px-6">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="text-center mb-12">
          <div className="text-6xl mb-4">📄</div>
          <h1 className="text-4xl md:text-5xl font-bold text-blue-400 mb-4">
            Téléchargement
          </h1>
          <p className="text-zinc-400 text-lg">
            Documentation officielle Sentinel Quantum Vanguard AI Pro
          </p>
        </div>

        {/* Main Document Card */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8 mb-8 hover:border-blue-500/50 transition-all">
          <div className="flex items-start space-x-4">
            <div className="text-5xl">📑</div>
            <div className="flex-1">
              <h2 className="text-2xl font-bold text-white mb-2">
                Executive Brief - Version Officielle
              </h2>
              <p className="text-zinc-400 mb-4">
                Présentation exécutive complète du système Sentinel Quantum Vanguard AI Pro.
                Document bilingue (Français/English) contenant l'architecture, les modules, 
                les agents IA, et la feuille de route du projet.
              </p>
              
              <div className="flex flex-wrap gap-2 mb-4">
                <span className="bg-blue-900/30 text-blue-300 px-3 py-1 rounded-full text-sm">
                  📄 PDF
                </span>
                <span className="bg-green-900/30 text-green-300 px-3 py-1 rounded-full text-sm">
                  🌍 Bilingue FR/EN
                </span>
                <span className="bg-purple-900/30 text-purple-300 px-3 py-1 rounded-full text-sm">
                  🔒 Document Officiel
                </span>
              </div>

              {pdfStatus === "available" ? (
                <a
                  href={pdfUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-block bg-blue-600 hover:bg-blue-500 text-white font-semibold px-6 py-3 rounded-lg transition-all shadow-lg shadow-blue-600/30 hover:shadow-blue-500/40"
                >
                  ⬇️ Télécharger le PDF
                </a>
              ) : pdfStatus === "checking" ? (
                <div className="bg-zinc-800 text-zinc-400 px-6 py-3 rounded-lg inline-block">
                  <span className="animate-pulse">⏳ Vérification...</span>
                </div>
              ) : (
                <div className="bg-yellow-900/30 border border-yellow-700 text-yellow-300 px-6 py-3 rounded-lg">
                  ⚠️ Document en préparation - Disponible prochainement
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Additional Resources */}
        <div className="grid md:grid-cols-2 gap-6">
          {/* Documentation en ligne */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 hover:border-blue-500/50 transition-all">
            <div className="text-3xl mb-3">📚</div>
            <h3 className="text-xl font-bold text-white mb-2">
              Documentation en ligne
            </h3>
            <p className="text-zinc-400 text-sm mb-4">
              Accédez à la documentation technique complète directement en ligne.
            </p>
            <a
              href="https://github.com/teetee971/SentinelQuantumVanguardAiPro"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-400 hover:text-blue-300 transition flex items-center space-x-1"
            >
              <span>Voir sur GitHub</span>
              <span>→</span>
            </a>
          </div>

          {/* Status Page */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 hover:border-green-500/50 transition-all">
            <div className="text-3xl mb-3">📊</div>
            <h3 className="text-xl font-bold text-white mb-2">
              Status Dashboard
            </h3>
            <p className="text-zinc-400 text-sm mb-4">
              Consultez le tableau de bord en temps réel du système.
            </p>
            <a
              href="/status.html"
              target="_blank"
              rel="noopener noreferrer"
              className="text-green-400 hover:text-green-300 transition flex items-center space-x-1"
            >
              <span>Voir le status</span>
              <span>→</span>
            </a>
          </div>
        </div>

        {/* Info Box */}
        <div className="mt-8 bg-blue-900/20 border border-blue-700/50 rounded-lg p-6">
          <div className="flex items-start space-x-3">
            <div className="text-2xl">ℹ️</div>
            <div>
              <h4 className="font-bold text-white mb-2">À propos des documents</h4>
              <p className="text-zinc-300 text-sm">
                Tous les documents officiels de Sentinel Quantum Vanguard AI Pro sont régulièrement mis à jour 
                pour refléter les dernières évolutions du système. Pour toute question ou demande de documentation 
                spécifique, consultez notre repository GitHub.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
