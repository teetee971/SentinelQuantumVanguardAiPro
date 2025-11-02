import { useState, useEffect } from "react";
import { Link } from "react-router-dom";

export default function ThreatMapGlobal() {
  const [threats, setThreats] = useState([
    { id: 1, location: "Paris, France", type: "DDoS", severity: "high", lat: 48.8566, lon: 2.3522 },
    { id: 2, location: "Tokyo, Japan", type: "Malware", severity: "critical", lat: 35.6762, lon: 139.6503 },
    { id: 3, location: "New York, USA", type: "Phishing", severity: "medium", lat: 40.7128, lon: -74.0060 },
    { id: 4, location: "London, UK", type: "Ransomware", severity: "high", lat: 51.5074, lon: -0.1278 },
  ]);

  const [stats, setStats] = useState({
    totalThreats: 1247,
    activeAttacks: 23,
    countriesAffected: 67,
    predictedTrends: 89
  });

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <header className="border-b border-zinc-800 bg-zinc-900/50 backdrop-blur">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Link to="/dashboard" className="text-zinc-400 hover:text-zinc-100">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </Link>
              <div className="text-3xl">🌐</div>
              <div>
                <h1 className="text-2xl font-bold text-sentinel-blue">ThreatMap Global</h1>
                <p className="text-xs text-zinc-400">Cartographie mondiale des menaces en temps réel</p>
              </div>
            </div>
            <div className="flex items-center space-x-2">
              <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
              <span className="text-sm text-green-400">Actif</span>
            </div>
          </div>
        </div>
      </header>

      <div className="border-b border-zinc-800 bg-zinc-900/30">
        <div className="container mx-auto px-6 py-6">
          <div className="grid grid-cols-4 gap-4">
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-red-400">{stats.totalThreats}</div>
              <div className="text-xs text-zinc-400">Menaces totales</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-orange-400">{stats.activeAttacks}</div>
              <div className="text-xs text-zinc-400">Attaques actives</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-blue-400">{stats.countriesAffected}</div>
              <div className="text-xs text-zinc-400">Pays affectés</div>
            </div>
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
              <div className="text-2xl font-bold text-purple-400">{stats.predictedTrends}</div>
              <div className="text-xs text-zinc-400">Tendances prédites</div>
            </div>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-6 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          <div className="lg:col-span-2 bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4">Carte Mondiale des Menaces</h2>
            <div className="bg-zinc-800 rounded-lg h-96 flex items-center justify-center">
              <div className="text-center text-zinc-500">
                <svg className="w-16 h-16 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <p>Carte interactive avec visualisation des menaces</p>
                <p className="text-xs mt-2">Intégration React-Leaflet pour géolocalisation</p>
              </div>
            </div>
          </div>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4">Menaces Actives</h2>
            <div className="space-y-3">
              {threats.map((threat) => (
                <div key={threat.id} className="bg-zinc-800/50 border border-zinc-700 rounded-lg p-3">
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-semibold text-sm">{threat.location}</span>
                    <span className={`text-xs px-2 py-1 rounded ${
                      threat.severity === "critical" ? "bg-red-500/20 text-red-400" :
                      threat.severity === "high" ? "bg-orange-500/20 text-orange-400" :
                      "bg-yellow-500/20 text-yellow-400"
                    }`}>
                      {threat.severity}
                    </span>
                  </div>
                  <div className="text-xs text-zinc-400">{threat.type}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h3 className="text-lg font-semibold mb-3 text-sentinel-blue">✓ Visualisation dynamique</h3>
            <p className="text-sm text-zinc-400">
              Affichage temps réel des menaces avec filtrage géographique et par intensité.
            </p>
          </div>
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h3 className="text-lg font-semibold mb-3 text-purple-400">✓ Projection IA</h3>
            <p className="text-sm text-zinc-400">
              Prédiction des tendances futures basée sur l'analyse comportementale.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
