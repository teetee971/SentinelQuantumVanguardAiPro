import { useState, useEffect } from "react";
import { collection, onSnapshot } from "firebase/firestore";
import { db } from "../firebaseConfig";

export default function Diagnostic() {
  const [systemStatus, setSystemStatus] = useState({
    cpu: 0,
    memory: 0,
    disk: 0,
    network: "OK",
    uptime: 0
  });
  const [services, setServices] = useState([]);
  const [logs, setLogs] = useState([]);

  // Écoute en temps réel des diagnostics système
  useEffect(() => {
    const unsubscribe = onSnapshot(collection(db, "system_diagnostics"), (snapshot) => {
      const data = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data(),
      }));
      
      if (data.length > 0) {
        const latest = data[0];
        setSystemStatus({
          cpu: latest.cpu || 0,
          memory: latest.memory || 0,
          disk: latest.disk || 0,
          network: latest.network || "OK",
          uptime: latest.uptime || 0
        });
      }
    });

    return () => unsubscribe();
  }, []);

  // Écoute des services
  useEffect(() => {
    const unsubscribe = onSnapshot(collection(db, "services_status"), (snapshot) => {
      const data = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data(),
      }));
      setServices(data);
    });

    return () => unsubscribe();
  }, []);

  // Écoute des logs système
  useEffect(() => {
    const unsubscribe = onSnapshot(collection(db, "system_logs"), (snapshot) => {
      const data = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data(),
      }));
      setLogs(data.slice(0, 10)); // Derniers 10 logs
    });

    return () => unsubscribe();
  }, []);

  const getStatusColor = (value, type = "percentage") => {
    if (type === "percentage") {
      if (value < 50) return "text-green-400";
      if (value < 80) return "text-yellow-400";
      return "text-red-400";
    }
    return "text-green-400";
  };

  const getStatusBg = (value, type = "percentage") => {
    if (type === "percentage") {
      if (value < 50) return "bg-green-500/20 border-green-600";
      if (value < 80) return "bg-yellow-500/20 border-yellow-600";
      return "bg-red-500/20 border-red-600";
    }
    return "bg-green-500/20 border-green-600";
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-8">
      <div className="max-w-7xl mx-auto">
        <h1 className="text-4xl font-bold mb-2 text-sentinel-blue">
          Diagnostic Système
        </h1>
        <p className="text-zinc-400 mb-8">
          Surveillance en temps réel de l'état du système Sentinel
        </p>

        {/* Métriques système */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          {/* CPU */}
          <div className={`border rounded-lg p-6 ${getStatusBg(systemStatus.cpu)}`}>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">CPU</h3>
              <svg className="w-5 h-5 text-zinc-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
              </svg>
            </div>
            <div className={`text-3xl font-bold ${getStatusColor(systemStatus.cpu)}`}>
              {systemStatus.cpu.toFixed(1)}%
            </div>
            <div className="mt-2 h-2 bg-zinc-800 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all duration-500 ${
                  systemStatus.cpu < 50 ? "bg-green-500" :
                  systemStatus.cpu < 80 ? "bg-yellow-500" : "bg-red-500"
                }`}
                style={{ width: `${systemStatus.cpu}%` }}
              ></div>
            </div>
          </div>

          {/* Mémoire */}
          <div className={`border rounded-lg p-6 ${getStatusBg(systemStatus.memory)}`}>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">Mémoire</h3>
              <svg className="w-5 h-5 text-zinc-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
            </div>
            <div className={`text-3xl font-bold ${getStatusColor(systemStatus.memory)}`}>
              {systemStatus.memory.toFixed(1)}%
            </div>
            <div className="mt-2 h-2 bg-zinc-800 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all duration-500 ${
                  systemStatus.memory < 50 ? "bg-green-500" :
                  systemStatus.memory < 80 ? "bg-yellow-500" : "bg-red-500"
                }`}
                style={{ width: `${systemStatus.memory}%` }}
              ></div>
            </div>
          </div>

          {/* Disque */}
          <div className={`border rounded-lg p-6 ${getStatusBg(systemStatus.disk)}`}>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">Disque</h3>
              <svg className="w-5 h-5 text-zinc-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
              </svg>
            </div>
            <div className={`text-3xl font-bold ${getStatusColor(systemStatus.disk)}`}>
              {systemStatus.disk.toFixed(1)}%
            </div>
            <div className="mt-2 h-2 bg-zinc-800 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all duration-500 ${
                  systemStatus.disk < 50 ? "bg-green-500" :
                  systemStatus.disk < 80 ? "bg-yellow-500" : "bg-red-500"
                }`}
                style={{ width: `${systemStatus.disk}%` }}
              ></div>
            </div>
          </div>

          {/* Réseau */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">Réseau</h3>
              <svg className="w-5 h-5 text-zinc-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9" />
              </svg>
            </div>
            <div className="text-3xl font-bold text-green-400">
              {systemStatus.network}
            </div>
            <p className="text-xs text-zinc-500 mt-2">
              Uptime: {Math.floor(systemStatus.uptime / 60)}m {systemStatus.uptime % 60}s
            </p>
          </div>
        </div>

        {/* Services */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4 flex items-center">
              <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />
              </svg>
              État des Services
            </h2>
            <div className="space-y-3">
              {services.length === 0 ? (
                <p className="text-zinc-500 text-sm">Aucun service surveillé</p>
              ) : (
                services.map((service) => (
                  <div
                    key={service.id}
                    className="flex items-center justify-between bg-zinc-800/50 px-4 py-3 rounded-lg"
                  >
                    <div className="flex items-center">
                      <div
                        className={`w-2 h-2 rounded-full mr-3 ${
                          service.status === "online" ? "bg-green-500 animate-pulse" :
                          service.status === "degraded" ? "bg-yellow-500" : "bg-red-500"
                        }`}
                      ></div>
                      <span className="font-medium">{service.name}</span>
                    </div>
                    <span className={`text-sm ${
                      service.status === "online" ? "text-green-400" :
                      service.status === "degraded" ? "text-yellow-400" : "text-red-400"
                    }`}>
                      {service.status}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Logs système */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4 flex items-center">
              <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              Logs Système
            </h2>
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {logs.length === 0 ? (
                <p className="text-zinc-500 text-sm">Aucun log récent</p>
              ) : (
                logs.map((log) => (
                  <div
                    key={log.id}
                    className="text-xs bg-zinc-800/50 px-3 py-2 rounded font-mono"
                  >
                    <span className="text-zinc-500">
                      [{log.timestamp ? new Date(log.timestamp.seconds * 1000).toLocaleTimeString() : "N/A"}]
                    </span>
                    <span className={`ml-2 ${
                      log.level === "error" ? "text-red-400" :
                      log.level === "warning" ? "text-yellow-400" :
                      log.level === "info" ? "text-blue-400" : "text-zinc-300"
                    }`}>
                      {log.level?.toUpperCase()}
                    </span>
                    <span className="text-zinc-300 ml-2">{log.message}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Graphique de performance */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">Performances sur 24h</h2>
          <iframe
            src="https://quickchart.io/chart?c={type:'line',data:{labels:['00h','04h','08h','12h','16h','20h','24h'],datasets:[{label:'CPU',data:[25,35,45,55,42,38,30],borderColor:'rgb(59,130,246)',fill:false},{label:'Mémoire',data:[40,42,45,48,46,44,42],borderColor:'rgb(34,197,94)',fill:false},{label:'Disque',data:[60,61,62,63,64,65,66],borderColor:'rgb(251,191,36)',fill:false}]},options:{scales:{y:{beginAtZero:true,max:100}}}}"
            width="100%"
            height="300"
            className="border-0 rounded-lg"
            title="Graphique de performance système"
          ></iframe>
        </div>

        {/* Navigation */}
        <div className="mt-8 flex gap-4">
          <a
            href="/"
            className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
          >
            ← Retour à l'accueil
          </a>
          <a
            href="/admin/vpn-console"
            className="px-4 py-2 bg-sentinel-blue hover:bg-blue-600 rounded-lg transition"
          >
            Console VPN →
          </a>
        </div>
      </div>
    </div>
  );
}
