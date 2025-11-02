import { useState, useEffect } from "react";
import { Link } from "react-router-dom";

export default function SentinelCoreDefense() {
  const [stats, setStats] = useState({
    threatsDetected: 247,
    threatsNeutralized: 247,
    activeScans: 12,
    systemsProtected: 1584,
    anomaliesTracked: 23,
    autoPatches: 156
  });

  const [recentThreats, setRecentThreats] = useState([
    { id: 1, type: "Intrusion Attempt", severity: "high", status: "neutralized", timestamp: Date.now() - 300000 },
    { id: 2, type: "Malware Detected", severity: "critical", status: "neutralized", timestamp: Date.now() - 600000 },
    { id: 3, type: "Anomaly Behavior", severity: "medium", status: "monitoring", timestamp: Date.now() - 900000 },
    { id: 4, type: "SQL Injection", severity: "high", status: "neutralized", timestamp: Date.now() - 1200000 },
  ]);

  const [subModules, setSubModules] = useState([
    { name: "Intrusion Detection AI", status: "active", scans: 1247, threats: 45 },
    { name: "Quantum Firewall System", status: "active", blocked: 892, rules: 2341 },
    { name: "AutoPatch Repair Engine", status: "active", patches: 156, pending: 3 },
    { name: "Behavioral Anomaly Tracker", status: "active", tracked: 23, flagged: 2 }
  ]);

  const getSeverityColor = (severity) => {
    switch(severity) {
      case "critical": return "text-red-400 bg-red-500/20 border-red-500";
      case "high": return "text-orange-400 bg-orange-500/20 border-orange-500";
      case "medium": return "text-yellow-400 bg-yellow-500/20 border-yellow-500";
      case "low": return "text-blue-400 bg-blue-500/20 border-blue-500";
      default: return "text-zinc-400 bg-zinc-500/20 border-zinc-500";
    }
  };

  const formatTimestamp = (timestamp) => {
    const diff = Date.now() - timestamp;
    const minutes = Math.floor(diff / 60000);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    return `${hours}h ago`;
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      {/* Header */}
      <header className="border-b border-zinc-800 bg-zinc-900/50 backdrop-blur">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Link to="/dashboard" className="text-zinc-400 hover:text-zinc-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </Link>
              <div className="text-3xl">🧠</div>
              <div>
                <h1 className="text-2xl font-bold text-sentinel-blue">
                  Sentinel Core Defense
                </h1>
                <p className="text-xs text-zinc-400">
                  Cœur défensif du système — Détection, isolation et neutralisation IA
                </p>
              </div>
            </div>
            <div className="flex items-center space-x-2">
              <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
              <span className="text-sm text-green-400">Opérationnel</span>
            </div>
          </div>
        </div>
      </header>

      {/* Stats Overview */}
      <div className="border-b border-zinc-800 bg-zinc-900/30">
        <div className="container mx-auto px-6 py-6">
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-red-400">{stats.threatsDetected}</div>
              <div className="text-xs text-zinc-400">Menaces détectées</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-green-400">{stats.threatsNeutralized}</div>
              <div className="text-xs text-zinc-400">Neutralisées</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-blue-400">{stats.activeScans}</div>
              <div className="text-xs text-zinc-400">Scans actifs</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-purple-400">{stats.systemsProtected}</div>
              <div className="text-xs text-zinc-400">Systèmes protégés</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-yellow-400">{stats.anomaliesTracked}</div>
              <div className="text-xs text-zinc-400">Anomalies suivies</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-cyan-400">{stats.autoPatches}</div>
              <div className="text-xs text-zinc-400">Auto-corrections</div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="container mx-auto px-6 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
          {/* Sub-Modules Status */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4 flex items-center">
              <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
              Sous-Modules
            </h2>
            <div className="space-y-3">
              {subModules.map((module, idx) => (
                <div key={idx} className="bg-zinc-800/50 border border-zinc-700 rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                      <span className="font-semibold">{module.name}</span>
                    </div>
                    <span className="text-xs text-green-400 uppercase">{module.status}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs text-zinc-400">
                    {module.scans && <div>Scans: <span className="text-zinc-200">{module.scans}</span></div>}
                    {module.threats && <div>Menaces: <span className="text-red-400">{module.threats}</span></div>}
                    {module.blocked && <div>Bloqués: <span className="text-zinc-200">{module.blocked}</span></div>}
                    {module.rules && <div>Règles: <span className="text-zinc-200">{module.rules}</span></div>}
                    {module.patches && <div>Patches: <span className="text-zinc-200">{module.patches}</span></div>}
                    {module.pending && <div>En attente: <span className="text-yellow-400">{module.pending}</span></div>}
                    {module.tracked && <div>Suivis: <span className="text-zinc-200">{module.tracked}</span></div>}
                    {module.flagged && <div>Signalés: <span className="text-orange-400">{module.flagged}</span></div>}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Recent Threats */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4 flex items-center">
              <svg className="w-5 h-5 mr-2 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              Menaces Récentes
            </h2>
            <div className="space-y-3">
              {recentThreats.map((threat) => (
                <div key={threat.id} className={`border rounded-lg p-4 ${getSeverityColor(threat.severity)}`}>
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-semibold">{threat.type}</span>
                    <span className="text-xs">{formatTimestamp(threat.timestamp)}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-xs uppercase font-medium">{threat.severity}</span>
                    <span className={`text-xs px-2 py-1 rounded ${
                      threat.status === "neutralized" 
                        ? "bg-green-500/20 text-green-400" 
                        : "bg-yellow-500/20 text-yellow-400"
                    }`}>
                      {threat.status}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Features Grid */}
        <div className="mb-8">
          <h2 className="text-2xl font-bold mb-4">Fonctions Clés</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-lg font-semibold mb-3 text-sentinel-blue">
                ✓ Analyse IA en temps réel
              </h3>
              <p className="text-sm text-zinc-400">
                Surveillance continue des flux réseau avec détection comportementale avancée utilisant des modèles d'apprentissage machine.
              </p>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-lg font-semibold mb-3 text-green-400">
                ✓ Isolement automatique
              </h3>
              <p className="text-sm text-zinc-400">
                Isolation instantanée des processus malveillants sans intervention humaine, garantissant la continuité des services légitimes.
              </p>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-lg font-semibold mb-3 text-purple-400">
                ✓ Réparation autonome
              </h3>
              <p className="text-sm text-zinc-400">
                AutoPatch Repair Engine corrige automatiquement les vulnérabilités détectées et applique les patches de sécurité.
              </p>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <h3 className="text-lg font-semibold mb-3 text-orange-400">
                ✓ Journalisation détaillée
              </h3>
              <p className="text-sm text-zinc-400">
                Logs complets de toutes les menaces avec traçabilité complète pour audit et conformité ISO/IEC 27001 & RGPD.
              </p>
            </div>
          </div>
        </div>

        {/* Benefits */}
        <div className="bg-gradient-to-r from-zinc-900 to-zinc-800 border border-zinc-700 rounded-lg p-6">
          <h2 className="text-2xl font-bold mb-4">Bénéfices Clients</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="flex items-start space-x-3">
              <div className="text-2xl">🛡️</div>
              <div>
                <div className="font-semibold mb-1">Protection 24/7</div>
                <div className="text-sm text-zinc-400">Contre toutes les intrusions</div>
              </div>
            </div>
            <div className="flex items-start space-x-3">
              <div className="text-2xl">📋</div>
              <div>
                <div className="font-semibold mb-1">Conformité totale</div>
                <div className="text-sm text-zinc-400">ISO/IEC 27001 & RGPD</div>
              </div>
            </div>
            <div className="flex items-start space-x-3">
              <div className="text-2xl">⚡</div>
              <div>
                <div className="font-semibold mb-1">Uptime 99.999%</div>
                <div className="text-sm text-zinc-400">Disponibilité garantie</div>
              </div>
            </div>
            <div className="flex items-start space-x-3">
              <div className="text-2xl">📉</div>
              <div>
                <div className="font-semibold mb-1">-90% risques</div>
                <div className="text-sm text-zinc-400">Réduction cybersécurité</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
