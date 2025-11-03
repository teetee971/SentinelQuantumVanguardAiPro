export default function App() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-12">
          <h1 className="text-5xl font-bold text-sentinel-blue mb-4">
            Sentinel Quantum Vanguard AI Pro
          </h1>
          <p className="text-zinc-400 text-lg mb-2">
            Système de cybersécurité autonome et prédictif
          </p>
          <p className="text-zinc-500 text-sm">
            Protection continue des infrastructures, entreprises et individus
          </p>
        </div>
        
        {/* MODULE 11 - Applications & Extensions */}
        <div className="mb-12">
          <h2 className="text-2xl font-bold mb-6 text-zinc-300 flex items-center">
            <span className="text-sentinel-blue mr-3">📱</span>
            MODULE 11 — Applications & Extensions
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <a
              href="/mobile-defender"
              className="bg-zinc-900 border border-zinc-800 hover:border-sentinel-blue p-6 rounded-lg transition group"
            >
              <div className="text-4xl mb-4">📱</div>
              <h3 className="text-xl font-semibold mb-2 group-hover:text-sentinel-blue transition">
                Mobile Defender
              </h3>
              <p className="text-zinc-500 text-sm">
                Application mobile et PWA avec scan embarqué et alertes en temps réel
              </p>
            </a>

            <a
              href="/dashboard"
              className="bg-zinc-900 border border-zinc-800 hover:border-blue-500 p-6 rounded-lg transition group"
            >
              <div className="text-4xl mb-4">🌐</div>
              <h3 className="text-xl font-semibold mb-2 group-hover:text-blue-400 transition">
                Quantum Dashboard
              </h3>
              <p className="text-zinc-500 text-sm">
                Interface centrale avec ThreatMap, Agents Overview et Analytics
              </p>
            </a>

            <a
              href="/pegasus-scan"
              className="bg-zinc-900 border border-zinc-800 hover:border-purple-500 p-6 rounded-lg transition group"
            >
              <div className="text-4xl mb-4">🔍</div>
              <h3 className="text-xl font-semibold mb-2 group-hover:text-purple-400 transition">
                Pegasus Scan
              </h3>
              <p className="text-zinc-500 text-sm">
                Page publique de test et détection de spyware et malware
              </p>
            </a>

            <a
              href="/threatmap"
              className="bg-zinc-900 border border-zinc-800 hover:border-red-500 p-6 rounded-lg transition group"
            >
              <div className="text-4xl mb-4">🗺️</div>
              <h3 className="text-xl font-semibold mb-2 group-hover:text-red-400 transition">
                ThreatMap
              </h3>
              <p className="text-zinc-500 text-sm">
                Carte mondiale des menaces cyber en temps réel avec export CSV
              </p>
            </a>

            <a
              href="/download"
              className="bg-zinc-900 border border-zinc-800 hover:border-green-500 p-6 rounded-lg transition group"
            >
              <div className="text-4xl mb-4">📲</div>
              <h3 className="text-xl font-semibold mb-2 group-hover:text-green-400 transition">
                Téléchargements
              </h3>
              <p className="text-zinc-500 text-sm">
                Applications Android, Windows et PWA avec QR Code
              </p>
            </a>

            <a
              href="/diagnostic"
              className="bg-zinc-900 border border-zinc-800 hover:border-yellow-500 p-6 rounded-lg transition group"
            >
              <div className="text-4xl mb-4">📊</div>
              <h3 className="text-xl font-semibold mb-2 group-hover:text-yellow-400 transition">
                Diagnostic Système
              </h3>
              <p className="text-zinc-500 text-sm">
                Surveillance en temps réel des performances système
              </p>
            </a>
          </div>
        </div>

        {/* MODULE 12 - Sécurité & Authentification */}
        <div className="mb-12">
          <h2 className="text-2xl font-bold mb-6 text-zinc-300 flex items-center">
            <span className="text-sentinel-blue mr-3">🔐</span>
            MODULE 12 — Sécurité & Authentification
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <a
              href="/security"
              className="bg-gradient-to-br from-sentinel-blue/20 to-purple-900/20 border border-sentinel-blue/30 hover:border-sentinel-blue p-6 rounded-lg transition group col-span-full md:col-span-2 lg:col-span-3"
            >
              <div className="flex items-center gap-6">
                <div className="text-5xl">🔐</div>
                <div className="flex-1">
                  <h3 className="text-2xl font-semibold mb-2 group-hover:text-sentinel-blue transition">
                    Security Dashboard
                  </h3>
                  <p className="text-zinc-400 text-sm mb-3">
                    Centre de contrôle complet pour tous les modules de sécurité: AuthGuardian, SecureFormWatcher, TokenAutoRefresher, SessionHijackGuardian, LicenseManager et MonetizerAI
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {["🧩 AuthGuardian", "🔒 FormWatcher", "🔁 TokenRefresher", "🧱 SessionGuard", "🪪 License", "💰 Monetizer"].map((tag) => (
                      <span key={tag} className="text-xs px-2 py-1 bg-zinc-800 rounded-full text-zinc-400">
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </a>
          </div>
        </div>

        {/* Admin Section */}
        <div>
          <h2 className="text-2xl font-bold mb-6 text-zinc-300 flex items-center">
            <span className="text-sentinel-blue mr-3">⚙️</span>
            Administration
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <a
              href="/admin/vpn-console"
              className="bg-zinc-900 border border-zinc-800 hover:border-green-500 p-6 rounded-lg transition group"
            >
              <div className="text-4xl mb-4">🛡️</div>
              <h3 className="text-xl font-semibold mb-2 group-hover:text-green-400 transition">
                Console VPN
              </h3>
              <p className="text-zinc-500 text-sm">
                Gestion et contrôle des nœuds VPN Sentinel
              </p>
            </a>
          </div>
        </div>

        {/* Footer Info */}
        <div className="mt-12 p-6 bg-zinc-900 border border-zinc-800 rounded-lg text-center">
          <p className="text-zinc-400 text-sm mb-2">
            ✅ Tous les modules sont actifs, interconnectés et certifiés
          </p>
          <p className="text-zinc-600 text-xs">
            © 2025 Sentinel Quantum Vanguard AI Pro — Cybersécurité autonome et prédictive
          </p>
        </div>
      </div>
    </div>
  );
}
