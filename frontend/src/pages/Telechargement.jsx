import Navbar from "../components/Navbar";

export default function Telechargement() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <Navbar />
      
      <div className="pt-24 p-8">
        <div className="max-w-4xl mx-auto">
          <h1 className="text-4xl font-bold mb-2 text-sentinel-blue">
            📄 Téléchargement
          </h1>
          <p className="text-zinc-400 mb-8">
            Documentation et ressources officielles Sentinel Quantum Vanguard AI Pro
          </p>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8">
            <h2 className="text-2xl font-semibold mb-4 text-blue-400">
              Fiche PDF Bilingue Officielle
            </h2>
            <p className="text-zinc-300 mb-6">
              Téléchargez la documentation complète du système Sentinel Quantum Vanguard AI Pro 
              incluant les spécifications techniques, le guide d'utilisation et les fonctionnalités avancées.
            </p>
            
            <div className="flex flex-col gap-4">
              <a
                href="/documents/sentinel-guide.pdf"
                download
                className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-sentinel-blue hover:bg-blue-600 text-white font-semibold rounded-lg transition"
              >
                <svg 
                  className="w-5 h-5" 
                  fill="none" 
                  stroke="currentColor" 
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path 
                    strokeLinecap="round" 
                    strokeLinejoin="round" 
                    strokeWidth={2} 
                    d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" 
                  />
                </svg>
                Télécharger la documentation PDF
              </a>
              
              <div className="text-sm text-zinc-500 text-center">
                Format: PDF • Taille: ~2 MB • Langues: Français / English
              </div>
            </div>
          </div>

          <div className="mt-8 grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-lg font-semibold mb-3 text-blue-400">
                📚 Contenu inclus
              </h3>
              <ul className="space-y-2 text-zinc-300 text-sm">
                <li className="flex items-start gap-2">
                  <span className="text-green-400">✓</span>
                  <span>Architecture système complète</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-green-400">✓</span>
                  <span>Guide d'installation et configuration</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-green-400">✓</span>
                  <span>Documentation API et intégrations</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="text-green-400">✓</span>
                  <span>Procédures de sécurité et bonnes pratiques</span>
                </li>
              </ul>
            </div>

            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-lg font-semibold mb-3 text-blue-400">
                ℹ️ Informations
              </h3>
              <ul className="space-y-2 text-zinc-300 text-sm">
                <li>
                  <strong>Version:</strong> 1.0.0
                </li>
                <li>
                  <strong>Dernière mise à jour:</strong> Novembre 2025
                </li>
                <li>
                  <strong>Licence:</strong> Propriétaire
                </li>
                <li>
                  <strong>Support:</strong> contact@sentinel-ai.pro
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
