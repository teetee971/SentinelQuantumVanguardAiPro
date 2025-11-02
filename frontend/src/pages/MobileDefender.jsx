import { useState, useEffect } from "react";

export default function MobileDefender() {
  const [activeTab, setActiveTab] = useState("alerts");
  const [alerts, setAlerts] = useState([
    { id: 1, type: "security", severity: "high", message: "Tentative d'accès non autorisé détectée", time: new Date() },
    { id: 2, type: "scan", severity: "low", message: "Scan automatique terminé - Aucune menace", time: new Date() },
    { id: 3, type: "update", severity: "medium", message: "Mise à jour de sécurité disponible", time: new Date() }
  ]);
  const [scanStatus, setScanStatus] = useState({
    lastScan: new Date(),
    nextScan: new Date(Date.now() + 3600000),
    threatsFound: 0,
    filesScanned: 0
  });
  const [threatJournal, setThreatJournal] = useState([
    { id: 1, date: new Date(), type: "Phishing", severity: "high", status: "blocked", source: "Email" },
    { id: 2, date: new Date(Date.now() - 86400000), type: "Malware", severity: "critical", status: "quarantined", source: "Download" },
    { id: 3, date: new Date(Date.now() - 172800000), type: "Suspicious App", severity: "medium", status: "removed", source: "Play Store" }
  ]);
  const [authStatus, setAuthStatus] = useState({
    twoFactor: true,
    biometric: true,
    lastAuth: new Date()
  });

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-4 md:p-6">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-4">
            <h1 className="text-3xl font-bold text-sentinel-blue">
              Mobile Defender
            </h1>
            <div className="flex gap-2">
              <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
              <span className="text-sm text-zinc-400">Protégé</span>
            </div>
          </div>
          <p className="text-zinc-400 text-sm">
            Protection mobile complète avec IA
          </p>
        </div>

        {/* Status Card */}
        <div className="bg-gradient-to-r from-sentinel-blue/20 to-purple-900/20 border border-sentinel-blue/30 rounded-lg p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-xl font-semibold mb-1">État de protection</h2>
              <p className="text-sm text-zinc-400">Tous les systèmes opérationnels</p>
            </div>
            <div className="text-4xl">🛡️</div>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-zinc-900/50 p-3 rounded-lg">
              <div className="text-2xl font-bold text-green-400">100%</div>
              <div className="text-xs text-zinc-400">Sécurité</div>
            </div>
            <div className="bg-zinc-900/50 p-3 rounded-lg">
              <div className="text-2xl font-bold text-blue-400">{scanStatus.threatsFound}</div>
              <div className="text-xs text-zinc-400">Menaces</div>
            </div>
            <div className="bg-zinc-900/50 p-3 rounded-lg">
              <div className="text-2xl font-bold text-purple-400">{scanStatus.filesScanned}</div>
              <div className="text-xs text-zinc-400">Fichiers</div>
            </div>
            <div className="bg-zinc-900/50 p-3 rounded-lg">
              <div className="text-2xl font-bold text-yellow-400">24/7</div>
              <div className="text-xs text-zinc-400">Actif</div>
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
          {[
            { id: "alerts", label: "Alertes", icon: "🔔" },
            { id: "scan", label: "Scan", icon: "🔍" },
            { id: "journal", label: "Journal", icon: "📋" },
            { id: "auth", label: "Auth", icon: "🔐" }
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition whitespace-nowrap ${
                activeTab === tab.id
                  ? "bg-sentinel-blue text-white"
                  : "bg-zinc-900 text-zinc-400 hover:bg-zinc-800"
              }`}
            >
              <span>{tab.icon}</span>
              <span className="text-sm font-medium">{tab.label}</span>
            </button>
          ))}
        </div>

        {/* Tab Content */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
          {/* Real-Time Alert Center */}
          {activeTab === "alerts" && (
            <div>
              <h2 className="text-xl font-semibold mb-4 flex items-center">
                <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
                Centre d'alertes en temps réel
              </h2>
              <div className="space-y-3">
                {alerts.length === 0 ? (
                  <div className="text-center py-8">
                    <div className="text-4xl mb-3">✅</div>
                    <p className="text-zinc-400">Aucune alerte active</p>
                  </div>
                ) : (
                  alerts.map((alert) => (
                    <div
                      key={alert.id}
                      className={`flex items-start gap-3 p-4 rounded-lg ${
                        alert.severity === "critical" || alert.severity === "high"
                          ? "bg-red-900/20 border border-red-800"
                          : alert.severity === "medium"
                          ? "bg-yellow-900/20 border border-yellow-800"
                          : "bg-zinc-800/50"
                      }`}
                    >
                      <div
                        className={`w-2 h-2 rounded-full mt-2 ${
                          alert.severity === "high" || alert.severity === "critical"
                            ? "bg-red-500 animate-pulse"
                            : alert.severity === "medium"
                            ? "bg-yellow-500"
                            : "bg-blue-500"
                        }`}
                      ></div>
                      <div className="flex-1">
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-medium">{alert.type.toUpperCase()}</span>
                          <span className="text-xs text-zinc-500">
                            {alert.time.toLocaleTimeString()}
                          </span>
                        </div>
                        <p className="text-sm text-zinc-400">{alert.message}</p>
                        <span className={`text-xs px-2 py-1 rounded mt-2 inline-block ${
                          alert.severity === "high" || alert.severity === "critical"
                            ? "bg-red-500/20 text-red-400"
                            : alert.severity === "medium"
                            ? "bg-yellow-500/20 text-yellow-400"
                            : "bg-blue-500/20 text-blue-400"
                        }`}>
                          {alert.severity}
                        </span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* Pegasus Scan Mobile */}
          {activeTab === "scan" && (
            <div>
              <h2 className="text-xl font-semibold mb-4 flex items-center">
                <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                Pegasus Scan Mobile
              </h2>
              <div className="text-center py-8">
                <div className="text-6xl mb-4">🔍</div>
                <h3 className="text-lg font-semibold mb-2">Scan de sécurité embarqué</h3>
                <p className="text-zinc-400 mb-6">
                  Dernier scan: {scanStatus.lastScan.toLocaleString()}
                </p>
                
                <div className="grid grid-cols-2 gap-4 mb-6">
                  <div className="bg-zinc-800/50 p-4 rounded-lg">
                    <div className="text-2xl font-bold text-green-400">
                      {scanStatus.filesScanned}
                    </div>
                    <div className="text-sm text-zinc-400">Fichiers scannés</div>
                  </div>
                  <div className="bg-zinc-800/50 p-4 rounded-lg">
                    <div className="text-2xl font-bold text-red-400">
                      {scanStatus.threatsFound}
                    </div>
                    <div className="text-sm text-zinc-400">Menaces trouvées</div>
                  </div>
                </div>

                <button className="px-6 py-3 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition font-semibold">
                  Lancer un scan complet
                </button>
                
                <p className="text-xs text-zinc-500 mt-4">
                  Prochain scan automatique: {scanStatus.nextScan.toLocaleTimeString()}
                </p>
              </div>
            </div>
          )}

          {/* Threat Journal Sync */}
          {activeTab === "journal" && (
            <div>
              <h2 className="text-xl font-semibold mb-4 flex items-center">
                <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                Journal des menaces
              </h2>
              <div className="space-y-3">
                {threatJournal.length === 0 ? (
                  <div className="text-center py-8">
                    <div className="text-4xl mb-3">📋</div>
                    <p className="text-zinc-400">Aucune menace enregistrée</p>
                  </div>
                ) : (
                  threatJournal.map((threat) => (
                    <div
                      key={threat.id}
                      className="flex items-center justify-between p-4 bg-zinc-800/50 rounded-lg"
                    >
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="font-medium">{threat.type}</span>
                          <span className={`text-xs px-2 py-1 rounded ${
                            threat.severity === "critical"
                              ? "bg-red-500/20 text-red-400"
                              : threat.severity === "high"
                              ? "bg-orange-500/20 text-orange-400"
                              : "bg-yellow-500/20 text-yellow-400"
                          }`}>
                            {threat.severity}
                          </span>
                          <span className={`text-xs px-2 py-1 rounded ${
                            threat.status === "blocked"
                              ? "bg-green-500/20 text-green-400"
                              : threat.status === "quarantined"
                              ? "bg-yellow-500/20 text-yellow-400"
                              : "bg-blue-500/20 text-blue-400"
                          }`}>
                            {threat.status}
                          </span>
                        </div>
                        <p className="text-sm text-zinc-400">
                          Source: {threat.source} • {threat.date.toLocaleDateString()}
                        </p>
                      </div>
                      <button className="text-sentinel-blue hover:text-blue-400 text-sm">
                        Détails →
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* Secure Authenticator */}
          {activeTab === "auth" && (
            <div>
              <h2 className="text-xl font-semibold mb-4 flex items-center">
                <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
                Authentification sécurisée
              </h2>
              <div className="space-y-4">
                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-green-700/20 rounded-lg flex items-center justify-center">
                        <svg className="w-6 h-6 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                        </svg>
                      </div>
                      <div>
                        <h3 className="font-semibold">Authentification 2FA</h3>
                        <p className="text-sm text-zinc-400">Double facteur activé</p>
                      </div>
                    </div>
                    <div className={`w-12 h-6 rounded-full transition ${
                      authStatus.twoFactor ? "bg-green-600" : "bg-zinc-700"
                    } relative`}>
                      <div className={`w-5 h-5 bg-white rounded-full absolute top-0.5 transition ${
                        authStatus.twoFactor ? "right-0.5" : "left-0.5"
                      }`}></div>
                    </div>
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-blue-700/20 rounded-lg flex items-center justify-center">
                        <svg className="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 11c0 3.517-1.009 6.799-2.753 9.571m-3.44-2.04l.054-.09A13.916 13.916 0 008 11a4 4 0 118 0c0 1.017-.07 2.019-.203 3m-2.118 6.844A21.88 21.88 0 0015.171 17m3.839 1.132c.645-2.266.99-4.659.99-7.132A8 8 0 008 4.07M3 15.364c.64-1.319 1-2.8 1-4.364 0-1.457.39-2.823 1.07-4" />
                        </svg>
                      </div>
                      <div>
                        <h3 className="font-semibold">Biométrie</h3>
                        <p className="text-sm text-zinc-400">Empreinte digitale / Face ID</p>
                      </div>
                    </div>
                    <div className={`w-12 h-6 rounded-full transition ${
                      authStatus.biometric ? "bg-green-600" : "bg-zinc-700"
                    } relative`}>
                      <div className={`w-5 h-5 bg-white rounded-full absolute top-0.5 transition ${
                        authStatus.biometric ? "right-0.5" : "left-0.5"
                      }`}></div>
                    </div>
                  </div>
                </div>

                <div className="bg-zinc-800/50 p-4 rounded-lg">
                  <h3 className="font-semibold mb-2">Dernière authentification</h3>
                  <p className="text-sm text-zinc-400">
                    {authStatus.lastAuth.toLocaleString()}
                  </p>
                  <p className="text-xs text-green-400 mt-2">
                    ✓ Connexion vérifiée et sécurisée
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Quick Actions */}
        <div className="grid grid-cols-2 gap-4 mt-6">
          <a
            href="/"
            className="px-4 py-3 bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 rounded-lg transition text-center"
          >
            ← Accueil
          </a>
          <a
            href="/dashboard"
            className="px-4 py-3 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition text-center font-semibold"
          >
            Dashboard →
          </a>
        </div>
      </div>
    </div>
  );
}
