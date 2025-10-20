import React, { useEffect, useRef, useState } from "react";
import Globe from "globe.gl";
import { initializeApp } from "firebase/app";
import { getFirestore, collection, onSnapshot, query, orderBy } from "firebase/firestore";

// === 🔐 Configuration Firebase ===
const firebaseConfig = {
  apiKey: "AIzaSyXXXXXX",
  authDomain: "sentinelquantumvanguardai.firebaseapp.com",
  projectId: "sentinelquantumvanguardai",
  storageBucket: "sentinelquantumvanguardai.appspot.com",
  messagingSenderId: "XXXXXXX",
  appId: "1:XXXXXXX:web:XXXXXX",
};

// === Initialisation Firebase ===
const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

export default function HealthMap() {
  const globeRef = useRef();
  const [agents, setAgents] = useState([]);
  const [lastUpdate, setLastUpdate] = useState(null);
  const [loading, setLoading] = useState(true);

  // === Initialisation du globe 3D ===
  useEffect(() => {
    const globe = Globe()(globeRef.current)
      .globeImageUrl("https://unpkg.com/three-globe/example/img/earth-night.jpg")
      .backgroundImageUrl("https://unpkg.com/three-globe/example/img/night-sky.png")
      .pointAltitude("size")
      .pointColor("color")
      .pointLabel("label")
      .pointsTransitionDuration(1000);

    const nodes = [
      { lat: 48.8566, lng: 2.3522, color: "lime", size: 0.7, label: "🇪🇺 EU Node – Paris" },
      { lat: 37.7749, lng: -122.4194, color: "cyan", size: 0.7, label: "🇺🇸 US Node – San Francisco" },
      { lat: -33.8688, lng: 151.2093, color: "orange", size: 0.7, label: "🇦🇺 APAC Node – Sydney" },
      { lat: 16.265, lng: -61.551, color: "gold", size: 0.7, label: "🇬🇵 Caribbean Node – Guadeloupe" },
      { lat: 35.6895, lng: 139.6917, color: "violet", size: 0.7, label: "🇯🇵 Asia Node – Tokyo" },
    ];
    globe.pointsData(nodes);

    globe.controls().autoRotate = true;
    globe.controls().autoRotateSpeed = 0.55;
    globe.controls().enableZoom = true;

    // === Écoute temps réel Firestore ===
    const q = query(collection(db, "agents_status"), orderBy("updatedAt", "desc"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const updatedAgents = snapshot.docs.map((doc) => ({
        id: doc.id,
        ...doc.data(),
      }));
      setAgents(updatedAgents);
      setLastUpdate(new Date().toLocaleTimeString());
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  return (
    <div className="relative min-h-screen bg-black text-white overflow-hidden font-inter">
      <div ref={globeRef} className="absolute inset-0" />
      <div className="absolute top-0 left-0 w-full p-6 backdrop-blur-xl bg-black/40 border-b border-cyan-900/40 z-10">
        <h1 className="text-2xl font-semibold text-cyan-400 drop-shadow-md">
          🌍 Sentinel Quantum Vanguard AI Pro – HealthMap 3D
        </h1>
        <p className="text-sm text-gray-400">Supervision Firestore temps réel – Agents IA & Nœuds actifs</p>
      </div>

      {/* === Tableau des Agents IA === */}
      <div className="absolute bottom-4 left-4 right-4 max-w-4xl mx-auto bg-black/60 backdrop-blur-xl border border-cyan-900/40 rounded-2xl p-5 z-10 shadow-lg glass">
        <div className="flex justify-between items-center mb-3">
          <h2 className="text-lg font-semibold text-cyan-300">
            🧠 Agents IA – Statut en direct
          </h2>
          <span className="text-xs text-gray-500">
            Dernière mise à jour : {lastUpdate || "—"}
          </span>
        </div>

        {loading ? (
          <p className="text-gray-400 text-sm text-center animate-pulse">Chargement des données Firestore...</p>
        ) : agents.length === 0 ? (
          <p className="text-gray-500 text-sm text-center italic">Aucun agent IA détecté.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {agents.map((a) => (
              <div
                key={a.id}
                className={`p-3 rounded-xl border transition duration-500 shadow-inner ${
                  a.status?.includes("🟢")
                    ? "border-green-500/40 bg-green-900/10"
                    : a.status?.includes("🟡")
                    ? "border-yellow-400/40 bg-yellow-900/10"
                    : "border-red-500/40 bg-red-900/10"
                }`}
              >
                <div className="flex justify-between items-center mb-1">
                  <span className="text-gray-200 font-medium">{a.name}</span>
                  <span className="font-semibold">{a.status}</span>
                </div>
                <div className="text-xs text-gray-400">
                  Zone : {a.zone || "—"} • Ping : {a.ping || "—"}
                </div>
                <div className="text-xs text-gray-500 mt-1">
                  MAJ :{" "}
                  {a.updatedAt
                    ? new Date(a.updatedAt.seconds * 1000).toLocaleTimeString()
                    : "—"}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* === Bandeau supérieur d’état global === */}
      <div className="absolute top-0 right-0 p-4 text-xs text-gray-400">
        <div className="flex gap-3">
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span> Stable
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 bg-yellow-400 rounded-full animate-pulse"></span> Latence
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 bg-red-500 rounded-full animate-pulse"></span> Panne
          </span>
        </div>
      </div>
    </div>
  );
}
