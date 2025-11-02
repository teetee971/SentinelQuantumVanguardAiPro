import { useState, useEffect } from "react";

export default function ThreatMap() {
  const [threats, setThreats] = useState([]);
  const [filter, setFilter] = useState({ continent: "all", severity: "all" });
  const [stats, setStats] = useState({
    total: 0,
    critical: 0,
    high: 0,
    medium: 0,
    low: 0
  });

  useEffect(() => {
    // Simulation de données de menaces en temps réel
    const mockThreats = [
      { id: 1, continent: "Europe", country: "France", city: "Paris", lat: 48.8566, lng: 2.3522, severity: "high", type: "DDoS", time: new Date() },
      { id: 2, continent: "Asia", country: "China", city: "Beijing", lat: 39.9042, lng: 116.4074, severity: "critical", type: "Ransomware", time: new Date() },
      { id: 3, continent: "North America", country: "USA", city: "New York", lat: 40.7128, lng: -74.0060, severity: "medium", type: "Phishing", time: new Date() },
      { id: 4, continent: "Europe", country: "Germany", city: "Berlin", lat: 52.5200, lng: 13.4050, severity: "low", type: "Scan", time: new Date() },
      { id: 5, continent: "Asia", country: "Japan", city: "Tokyo", lat: 35.6762, lng: 139.6503, severity: "high", type: "SQL Injection", time: new Date() },
      { id: 6, continent: "South America", country: "Brazil", city: "São Paulo", lat: -23.5505, lng: -46.6333, severity: "medium", type: "XSS", time: new Date() },
      { id: 7, continent: "Africa", country: "South Africa", city: "Johannesburg", lat: -26.2041, lng: 28.0473, severity: "low", type: "Port Scan", time: new Date() },
      { id: 8, continent: "Oceania", country: "Australia", city: "Sydney", lat: -33.8688, lng: 151.2093, severity: "high", type: "Malware", time: new Date() }
    ];

    setThreats(mockThreats);
    
    const stats = {
      total: mockThreats.length,
      critical: mockThreats.filter(t => t.severity === "critical").length,
      high: mockThreats.filter(t => t.severity === "high").length,
      medium: mockThreats.filter(t => t.severity === "medium").length,
      low: mockThreats.filter(t => t.severity === "low").length
    };
    setStats(stats);

    // Mise à jour toutes les 5 secondes
    const interval = setInterval(() => {
      const newThreat = {
        id: Date.now(),
        continent: ["Europe", "Asia", "North America", "South America", "Africa", "Oceania"][Math.floor(Math.random() * 6)],
        country: "Unknown",
        city: "Unknown",
        lat: Math.random() * 180 - 90,
        lng: Math.random() * 360 - 180,
        severity: ["critical", "high", "medium", "low"][Math.floor(Math.random() * 4)],
        type: ["DDoS", "Ransomware", "Phishing", "SQL Injection", "XSS", "Malware"][Math.floor(Math.random() * 6)],
        time: new Date()
      };
      setThreats(prev => [newThreat, ...prev].slice(0, 50));
    }, 5000);

    return () => clearInterval(interval);
  }, []);

  const filteredThreats = threats.filter(threat => {
    if (filter.continent !== "all" && threat.continent !== filter.continent) return false;
    if (filter.severity !== "all" && threat.severity !== filter.severity) return false;
    return true;
  });

  const exportToCSV = () => {
    const headers = ["ID", "Continent", "Country", "City", "Latitude", "Longitude", "Severity", "Type", "Time"];
    const rows = filteredThreats.map(t => [
      t.id,
      t.continent,
      t.country,
      t.city,
      t.lat,
      t.lng,
      t.severity,
      t.type,
      t.time.toISOString()
    ]);

    const csv = [headers, ...rows].map(row => row.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `threatmap-export-${Date.now()}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-4xl font-bold text-sentinel-blue mb-2">
              ThreatMap
            </h1>
            <p className="text-zinc-400">
              Carte mondiale des menaces cyber en temps réel
            </p>
          </div>
          <div className="flex gap-3">
            <button
              onClick={exportToCSV}
              className="px-4 py-2 bg-green-700 hover:bg-green-600 rounded-lg transition"
            >
              📊 Export CSV
            </button>
            <a
              href="/"
              className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
            >
              ← Accueil
            </a>
          </div>
        </div>

        {/* Statistiques */}
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-8">
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
            <div className="text-2xl font-bold text-blue-400">{stats.total}</div>
            <div className="text-sm text-zinc-400">Total</div>
          </div>
          <div className="bg-red-900/20 border border-red-800 rounded-lg p-4">
            <div className="text-2xl font-bold text-red-400">{stats.critical}</div>
            <div className="text-sm text-zinc-400">Critique</div>
          </div>
          <div className="bg-orange-900/20 border border-orange-800 rounded-lg p-4">
            <div className="text-2xl font-bold text-orange-400">{stats.high}</div>
            <div className="text-sm text-zinc-400">Haute</div>
          </div>
          <div className="bg-yellow-900/20 border border-yellow-800 rounded-lg p-4">
            <div className="text-2xl font-bold text-yellow-400">{stats.medium}</div>
            <div className="text-sm text-zinc-400">Moyenne</div>
          </div>
          <div className="bg-green-900/20 border border-green-800 rounded-lg p-4">
            <div className="text-2xl font-bold text-green-400">{stats.low}</div>
            <div className="text-sm text-zinc-400">Basse</div>
          </div>
        </div>

        {/* Filtres */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-8">
          <h2 className="text-xl font-semibold mb-4">Filtres</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-zinc-400 mb-2">Continent</label>
              <select
                value={filter.continent}
                onChange={(e) => setFilter({ ...filter, continent: e.target.value })}
                className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 focus:outline-none focus:border-sentinel-blue"
              >
                <option value="all">Tous les continents</option>
                <option value="Europe">Europe</option>
                <option value="Asia">Asie</option>
                <option value="North America">Amérique du Nord</option>
                <option value="South America">Amérique du Sud</option>
                <option value="Africa">Afrique</option>
                <option value="Oceania">Océanie</option>
              </select>
            </div>
            <div>
              <label className="block text-sm text-zinc-400 mb-2">Sévérité</label>
              <select
                value={filter.severity}
                onChange={(e) => setFilter({ ...filter, severity: e.target.value })}
                className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-4 py-2 text-zinc-100 focus:outline-none focus:border-sentinel-blue"
              >
                <option value="all">Toutes les sévérités</option>
                <option value="critical">Critique</option>
                <option value="high">Haute</option>
                <option value="medium">Moyenne</option>
                <option value="low">Basse</option>
              </select>
            </div>
          </div>
        </div>

        {/* Map 3D Placeholder */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 mb-8">
          <h2 className="text-xl font-semibold mb-4 flex items-center">
            <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Carte Interactive 3D
          </h2>
          <div className="bg-zinc-800 rounded-lg h-96 flex items-center justify-center relative overflow-hidden">
            <img
              src="https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Equirectangular_projection_SW.jpg/1200px-Equirectangular_projection_SW.jpg"
              alt="Carte du monde"
              className="w-full h-full object-cover opacity-30"
            />
            <div className="absolute inset-0 flex items-center justify-center">
              {filteredThreats.slice(0, 8).map((threat) => {
                const x = ((threat.lng + 180) / 360) * 100;
                const y = ((90 - threat.lat) / 180) * 100;
                return (
                  <div
                    key={threat.id}
                    className={`absolute w-4 h-4 rounded-full animate-ping ${
                      threat.severity === "critical" ? "bg-red-500" :
                      threat.severity === "high" ? "bg-orange-500" :
                      threat.severity === "medium" ? "bg-yellow-500" : "bg-green-500"
                    }`}
                    style={{ left: `${x}%`, top: `${y}%` }}
                    title={`${threat.city}, ${threat.country} - ${threat.type}`}
                  ></div>
                );
              })}
            </div>
            <div className="absolute top-4 right-4 bg-zinc-900/90 px-4 py-2 rounded-lg text-sm">
              <span className="text-green-400 animate-pulse">●</span> Live • Mise à jour toutes les 5s
            </div>
          </div>
        </div>

        {/* Liste des menaces */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">Flux de menaces ({filteredThreats.length})</h2>
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {filteredThreats.length === 0 ? (
              <p className="text-zinc-500 text-sm text-center py-8">
                Aucune menace correspondant aux filtres
              </p>
            ) : (
              filteredThreats.map((threat) => (
                <div
                  key={threat.id}
                  className={`flex items-center gap-4 px-4 py-3 rounded-lg ${
                    threat.severity === "critical" ? "bg-red-900/20 border border-red-800" :
                    threat.severity === "high" ? "bg-orange-900/20 border border-orange-800" :
                    threat.severity === "medium" ? "bg-yellow-900/20 border border-yellow-800" :
                    "bg-zinc-800/50"
                  }`}
                >
                  <div
                    className={`w-3 h-3 rounded-full ${
                      threat.severity === "critical" ? "bg-red-500 animate-pulse" :
                      threat.severity === "high" ? "bg-orange-500" :
                      threat.severity === "medium" ? "bg-yellow-500" : "bg-green-500"
                    }`}
                  ></div>
                  <div className="flex-1 grid grid-cols-1 md:grid-cols-4 gap-2">
                    <div>
                      <div className="text-sm text-zinc-400">Localisation</div>
                      <div className="font-medium">
                        {threat.city}, {threat.country}
                      </div>
                    </div>
                    <div>
                      <div className="text-sm text-zinc-400">Type</div>
                      <div className="font-medium">{threat.type}</div>
                    </div>
                    <div>
                      <div className="text-sm text-zinc-400">Sévérité</div>
                      <div className={`font-medium ${
                        threat.severity === "critical" ? "text-red-400" :
                        threat.severity === "high" ? "text-orange-400" :
                        threat.severity === "medium" ? "text-yellow-400" : "text-green-400"
                      }`}>
                        {threat.severity.toUpperCase()}
                      </div>
                    </div>
                    <div>
                      <div className="text-sm text-zinc-400">Heure</div>
                      <div className="font-medium text-zinc-300">
                        {threat.time.toLocaleTimeString()}
                      </div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
