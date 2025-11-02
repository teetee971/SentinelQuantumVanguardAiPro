import { useState, useEffect } from "react";
import { collection, onSnapshot } from "firebase/firestore";
import { db } from "../firebaseConfig";

export default function Dashboard() {
  const [threats, setThreats] = useState([]);
  const [agents, setAgents] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [analytics, setAnalytics] = useState({
    totalThreats: 0,
    activeAgents: 0,
    blockedAttacks: 0,
    systemHealth: 100
  });

  // Écoute en temps réel des menaces
  useEffect(() => {
    const unsubscribe = onSnapshot(
      collection(db, "threats"),
      (snapshot) => {
        const data = snapshot.docs.map(doc => ({
          id: doc.id,
          ...doc.data(),
        }));
        setThreats(data.slice(0, 10));
      },
      (error) => {
        console.error("Erreur lors de la récupération des menaces:", error);
      }
    );
    return () => unsubscribe();
  }, []);

  // Écoute des agents IA
  useEffect(() => {
    const unsubscribe = onSnapshot(
      collection(db, "ai_agents"),
      (snapshot) => {
        const data = snapshot.docs.map(doc => ({
          id: doc.id,
          ...doc.data(),
        }));
        setAgents(data);
        setAnalytics(prev => ({ ...prev, activeAgents: data.filter(a => a.status === "active").length }));
      },
      (error) => {
        console.error("Erreur lors de la récupération des agents:", error);
      }
    );
    return () => unsubscribe();
  }, []);

  // Écoute des alertes
  useEffect(() => {
    const unsubscribe = onSnapshot(
      collection(db, "alerts"),
      (snapshot) => {
        const data = snapshot.docs.map(doc => ({
          id: doc.id,
          ...doc.data(),
        }));
        setAlerts(data.slice(0, 20));
      },
      (error) => {
        console.error("Erreur lors de la récupération des alertes:", error);
      }
    );
    return () => unsubscribe();
  }, []);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-4xl font-bold text-sentinel-blue mb-2">
              Sentinel Quantum Dashboard
            </h1>
            <p className="text-zinc-400">
              Interface centrale du système de cybersécurité IA
            </p>
          </div>
          <div className="flex gap-3">
            <a
              href="/"
              className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
            >
              ← Accueil
            </a>
          </div>
        </div>

        {/* Quantum Analytics Board */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">Total Menaces</h3>
              <svg className="w-5 h-5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <div className="text-3xl font-bold text-red-400">{threats.length}</div>
            <p className="text-xs text-zinc-500 mt-2">Détectées aujourd'hui</p>
          </div>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">Agents Actifs</h3>
              <svg className="w-5 h-5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>
            <div className="text-3xl font-bold text-green-400">{analytics.activeAgents}</div>
            <p className="text-xs text-zinc-500 mt-2">IA opérationnelles</p>
          </div>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">Attaques Bloquées</h3>
              <svg className="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
              </svg>
            </div>
            <div className="text-3xl font-bold text-blue-400">{analytics.blockedAttacks}</div>
            <p className="text-xs text-zinc-500 mt-2">En temps réel</p>
          </div>

          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-medium text-zinc-300">Santé Système</h3>
              <svg className="w-5 h-5 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div className="text-3xl font-bold text-green-400">{analytics.systemHealth}%</div>
            <p className="text-xs text-zinc-500 mt-2">Tous systèmes OK</p>
          </div>
        </div>

        {/* ThreatMap View & Agents Overview */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
          {/* ThreatMap View */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4 flex items-center">
              <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              ThreatMap View
            </h2>
            <div className="bg-zinc-800/50 rounded-lg p-4 h-64 flex items-center justify-center">
              <div className="text-center">
                <svg className="w-16 h-16 mx-auto mb-4 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
                </svg>
                <p className="text-zinc-400 mb-2">Carte mondiale des menaces</p>
                <a href="/threatmap" className="text-sentinel-blue hover:underline text-sm">
                  Voir la carte complète →
                </a>
              </div>
            </div>
          </div>

          {/* Agents Overview */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4 flex items-center">
              <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
              </svg>
              Agents Overview
            </h2>
            <div className="space-y-3 max-h-64 overflow-y-auto">
              {agents.length === 0 ? (
                <p className="text-zinc-500 text-sm">Aucun agent disponible</p>
              ) : (
                agents.map((agent) => (
                  <div
                    key={agent.id}
                    className="flex items-center justify-between bg-zinc-800/50 px-4 py-3 rounded-lg"
                  >
                    <div className="flex items-center">
                      <div
                        className={`w-2 h-2 rounded-full mr-3 ${
                          agent.status === "active" ? "bg-green-500 animate-pulse" :
                          agent.status === "idle" ? "bg-yellow-500" : "bg-red-500"
                        }`}
                      ></div>
                      <div>
                        <span className="font-medium">{agent.name}</span>
                        <p className="text-xs text-zinc-500">{agent.type}</p>
                      </div>
                    </div>
                    <span className={`text-sm ${
                      agent.status === "active" ? "text-green-400" :
                      agent.status === "idle" ? "text-yellow-400" : "text-red-400"
                    }`}>
                      {agent.status}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Alerts Stream */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4 flex items-center">
            <svg className="w-5 h-5 mr-2 text-sentinel-blue" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
            Alerts Stream
          </h2>
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {alerts.length === 0 ? (
              <p className="text-zinc-500 text-sm">Aucune alerte récente</p>
            ) : (
              alerts.map((alert) => (
                <div
                  key={alert.id}
                  className={`flex items-start gap-3 px-4 py-3 rounded-lg ${
                    alert.severity === "critical" ? "bg-red-900/20 border border-red-800" :
                    alert.severity === "high" ? "bg-orange-900/20 border border-orange-800" :
                    alert.severity === "medium" ? "bg-yellow-900/20 border border-yellow-800" :
                    "bg-zinc-800/50"
                  }`}
                >
                  <div
                    className={`w-2 h-2 rounded-full mt-2 ${
                      alert.severity === "critical" ? "bg-red-500 animate-pulse" :
                      alert.severity === "high" ? "bg-orange-500" :
                      alert.severity === "medium" ? "bg-yellow-500" : "bg-blue-500"
                    }`}
                  ></div>
                  <div className="flex-1">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-medium">{alert.title}</span>
                      <span className="text-xs text-zinc-500">
                        {alert.timestamp ? new Date(alert.timestamp.seconds * 1000).toLocaleTimeString() : "N/A"}
                      </span>
                    </div>
                    <p className="text-sm text-zinc-400">{alert.message}</p>
                    <div className="flex items-center gap-2 mt-2">
                      <span className={`text-xs px-2 py-1 rounded ${
                        alert.severity === "critical" ? "bg-red-500/20 text-red-400" :
                        alert.severity === "high" ? "bg-orange-500/20 text-orange-400" :
                        alert.severity === "medium" ? "bg-yellow-500/20 text-yellow-400" :
                        "bg-blue-500/20 text-blue-400"
                      }`}>
                        {alert.severity}
                      </span>
                      {alert.source && (
                        <span className="text-xs text-zinc-500">
                          Source: {alert.source}
                        </span>
                      )}
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
