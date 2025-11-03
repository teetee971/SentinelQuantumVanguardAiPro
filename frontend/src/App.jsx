export default function App() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-4xl mx-auto text-center">
        <h1 className="text-4xl font-bold text-sentinel-blue mb-6">
          Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-zinc-400 mb-8">
          Système de surveillance et de gestion VPN avancé
        </p>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-12">
          <a
            href="/diagnostic"
            className="bg-zinc-900 border border-zinc-800 hover:border-sentinel-blue p-8 rounded-lg transition group"
          >
            <div className="text-4xl mb-4">🔍</div>
            <h2 className="text-xl font-semibold mb-2 group-hover:text-sentinel-blue transition">
              Diagnostic Système
            </h2>
            <p className="text-zinc-500 text-sm">
              Surveillance en temps réel des performances et de l'état du système
            </p>
          </a>

          <a
            href="/admin/vpn-console"
            className="bg-zinc-900 border border-zinc-800 hover:border-green-500 p-8 rounded-lg transition group"
          >
            <div className="text-4xl mb-4">🛡️</div>
            <h2 className="text-xl font-semibold mb-2 group-hover:text-green-400 transition">
              Console VPN
            </h2>
            <p className="text-zinc-500 text-sm">
              Gestion et contrôle des nœuds VPN Sentinel
            </p>
          </a>

          <a
            href="/telechargement"
            className="bg-zinc-900 border border-zinc-800 hover:border-blue-500 p-8 rounded-lg transition group"
          >
            <div className="text-4xl mb-4">📄</div>
            <h2 className="text-xl font-semibold mb-2 group-hover:text-blue-400 transition">
              Téléchargement PDF
            </h2>
            <p className="text-zinc-500 text-sm">
              Document officiel Sentinel Quantum Vanguard AI Pro
            </p>
          </a>
        </div>
      </div>
    </div>
  );
}
