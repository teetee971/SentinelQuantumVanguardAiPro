import { useState, useEffect } from "react";
import { Link } from "react-router-dom";

export default function Dashboard() {
  const [systemHealth, setSystemHealth] = useState({
    overall: 99.8,
    threats: 0,
    activeAgents: 15,
    uptime: 99.99
  });

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      {/* Header */}
      <header className="border-b border-zinc-800 bg-zinc-900/50 backdrop-blur">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <div className="text-3xl">🛡️</div>
              <div>
                <h1 className="text-2xl font-bold text-sentinel-blue">
                  Sentinel Quantum Vanguard AI Pro
                </h1>
                <p className="text-xs text-zinc-400">
                  Système mondial de cybersécurité autonome
                </p>
              </div>
            </div>
            <div className="flex items-center space-x-4">
              <div className="px-4 py-2 bg-green-500/10 border border-green-500/30 rounded-lg">
                <div className="flex items-center space-x-2">
                  <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                  <span className="text-sm text-green-400">Système opérationnel</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Global Status Bar */}
      <div className="border-b border-zinc-800 bg-zinc-900/30">
        <div className="container mx-auto px-6 py-4">
          <div className="grid grid-cols-4 gap-6">
            <div className="text-center">
              <div className="text-2xl font-bold text-green-400">{systemHealth.overall}%</div>
              <div className="text-xs text-zinc-400">Santé globale</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-400">{systemHealth.threats}</div>
              <div className="text-xs text-zinc-400">Menaces actives</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-purple-400">{systemHealth.activeAgents}</div>
              <div className="text-xs text-zinc-400">Agents IA actifs</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-cyan-400">{systemHealth.uptime}%</div>
              <div className="text-xs text-zinc-400">Disponibilité</div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="container mx-auto px-6 py-8">
        {/* Module 1: Cybersecurity & Defense AI */}
        <section className="mb-12">
          <div className="mb-6">
            <h2 className="text-3xl font-bold mb-2 flex items-center">
              <span className="text-4xl mr-3">🔒</span>
              MODULE 1 — Cybersécurité & Défense IA
            </h2>
            <p className="text-zinc-400">
              Détection, défense et résilience autonome
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* Sentinel Core Defense */}
            <Link to="/modules/sentinel-core-defense" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-sentinel-blue rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🧠</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-sentinel-blue transition">
                  Sentinel Core Defense
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Cœur défensif avec détection IA, isolation et neutralisation des menaces
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* Quantum Failover AI */}
            <Link to="/modules/quantum-failover" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-purple-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🔬</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-purple-400 transition">
                  Quantum Failover AI
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Continuité quantique avec basculement instantané et réplication IA
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* FireGuard */}
            <Link to="/modules/fireguard" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-orange-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🔥</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-orange-400 transition">
                  FireGuard
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Protection cloud Firebase, AdonisJS et Railway avec surveillance continue
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* AutoVerifier */}
            <Link to="/modules/autoverifier" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-cyan-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🧩</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-cyan-400 transition">
                  AutoVerifier
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Audit IA SSL/TLS, DNS, certificats et intégrité des fichiers
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* CloudArmorian */}
            <Link to="/modules/cloudarmorian" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-indigo-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🧱</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-indigo-400 transition">
                  CloudArmorian
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Bouclier IA anti-DDoS et anti-injection auto-apprenant
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>
          </div>
        </section>

        {/* Module 2: Intelligence & Cartographie mondiale */}
        <section className="mb-12">
          <div className="mb-6">
            <h2 className="text-3xl font-bold mb-2 flex items-center">
              <span className="text-4xl mr-3">🌍</span>
              MODULE 2 — Intelligence & Cartographie mondiale
            </h2>
            <p className="text-zinc-400">
              Surveillance planétaire et analyse prédictive
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* ThreatMap Global */}
            <Link to="/modules/threatmap-global" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-red-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🌐</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-red-400 transition">
                  ThreatMap Global
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Cartographie mondiale temps réel des menaces et intrusions
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* Realtime Health Mapper */}
            <Link to="/modules/health-mapper" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-green-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🛰️</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-green-400 transition">
                  Realtime Health Mapper
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Santé réseau en temps réel: latence, charge et synchronisation
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* GeoFailover Coordinator */}
            <Link to="/modules/geofailover" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-yellow-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🧭</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-yellow-400 transition">
                  GeoFailover Coordinator
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Redondance géographique et continuité inter-régions
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* Incident Predictor AI */}
            <Link to="/modules/incident-predictor" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-pink-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">📊</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-pink-400 transition">
                  Incident Predictor AI
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Prédiction IA des incidents avec contre-mesures préventives
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* Dynamic LoadBalancer AI */}
            <Link to="/modules/loadbalancer" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-teal-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">⚖️</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-teal-400 transition">
                  Dynamic LoadBalancer AI
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Équilibrage IA intelligent des charges réseau multi-cloud
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* Global Failover Watcher */}
            <Link to="/modules/failover-watcher" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-blue-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🌐</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-blue-400 transition">
                  Global Failover Watcher
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Surveillance mondiale 24/7 avec reprise automatique
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>
          </div>
        </section>

        {/* Module 3: Advanced Analysis & Detection */}
        <section className="mb-12">
          <div className="mb-6">
            <h2 className="text-3xl font-bold mb-2 flex items-center">
              <span className="text-4xl mr-3">🔍</span>
              MODULE 3 — Analyse & Détection avancée
            </h2>
            <p className="text-zinc-400">
              Détection spécialisée et analyse comportementale
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* Pegasus Scan IA */}
            <Link to="/modules/pegasus-scan" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-purple-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🧬</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-purple-400 transition">
                  Pegasus Scan IA
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Détection spyware, malware et comportements type Pegasus
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* PegasusHunter */}
            <Link to="/modules/pegasus-hunter" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-red-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🧠</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-red-400 transition">
                  PegasusHunter
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Chasseur comportemental IA pour Pegasus et FinFisher
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* LieSense Connector */}
            <Link to="/modules/liesense" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-amber-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🔎</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-amber-400 transition">
                  LieSense Connector
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Analyse vocale et détection d'anomalies comportementales
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* LipScan Vision */}
            <Link to="/modules/lipscan-vision" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-cyan-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🧩</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-cyan-400 transition">
                  LipScan Vision
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Détection deepfake et analyse d'authenticité vidéo
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>

            {/* BioSnap AI Connector */}
            <Link to="/modules/biosnap" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-lime-500 rounded-lg p-6 transition h-full">
                <div className="text-4xl mb-4">🌿</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-lime-400 transition">
                  BioSnap AI Connector
                </h3>
                <p className="text-sm text-zinc-400 mb-4">
                  Reconnaissance biométrique et signaux environnementaux
                </p>
                <div className="flex items-center text-xs text-zinc-500">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  Actif
                </div>
              </div>
            </Link>
          </div>
        </section>

        {/* Legacy Systems */}
        <section className="mb-12">
          <div className="mb-6">
            <h2 className="text-2xl font-bold mb-2 flex items-center">
              <span className="text-3xl mr-3">⚙️</span>
              Systèmes existants
            </h2>
            <p className="text-zinc-400">
              Modules de diagnostic et gestion VPN
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Link to="/diagnostic" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-sentinel-blue rounded-lg p-6 transition">
                <div className="text-4xl mb-4">🔍</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-sentinel-blue transition">
                  Diagnostic Système
                </h3>
                <p className="text-sm text-zinc-400">
                  Surveillance en temps réel des performances système
                </p>
              </div>
            </Link>

            <Link to="/admin/vpn-console" className="group">
              <div className="bg-zinc-900 border border-zinc-800 hover:border-green-500 rounded-lg p-6 transition">
                <div className="text-4xl mb-4">🛡️</div>
                <h3 className="text-xl font-semibold mb-2 group-hover:text-green-400 transition">
                  Console VPN
                </h3>
                <p className="text-sm text-zinc-400">
                  Gestion et contrôle des nœuds VPN Sentinel
                </p>
              </div>
            </Link>
          </div>
        </section>
      </div>

      {/* Footer */}
      <footer className="border-t border-zinc-800 bg-zinc-900/30 mt-12">
        <div className="container mx-auto px-6 py-6">
          <div className="text-center text-sm text-zinc-500">
            <p>© 2024 Sentinel Quantum Vanguard AI Pro</p>
            <p className="mt-1">Système mondial de cybersécurité autonome</p>
          </div>
        </div>
      </footer>
    </div>
  );
}
