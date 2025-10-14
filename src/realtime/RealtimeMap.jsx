import React, { useEffect, useState } from "react";
import { MapContainer, TileLayer, CircleMarker, Tooltip } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { Card, CardContent } from "@/components/ui/card";

export default function RealtimeMap() {
  const [agents, setAgents] = useState([]);
  const [orchestrator, setOrchestrator] = useState(null);

  useEffect(() => {
    const load = async () => {
      try {
        const ag = await fetch("/telemetry/agents.json?cache=no-store").then(r => r.json());
        const oc = await fetch("/telemetry/orchestrator.json?cache=no-store").then(r => r.json());
        setAgents(ag);
        setOrchestrator(oc);
      } catch (e) {
        console.error("Erreur de chargement des données IA", e);
      }
    };
    load();
    const i = setInterval(load, 60000);
    return () => clearInterval(i);
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-950 to-gray-900 text-gray-100 flex flex-col items-center p-4">
      <h1 className="text-3xl font-bold text-cyan-400 mb-2">🌍 Sentinel Global Health Monitor</h1>
      <p className="text-sm text-gray-400 mb-4">
        Carte en temps réel du réseau IA Sentinel – Wave 21
      </p>

      <Card className="w-full max-w-5xl bg-gray-900/80 border border-cyan-500/20 shadow-xl mb-6">
        <CardContent className="p-3">
          {orchestrator && (
            <div className="mb-2 text-sm text-gray-300">
              Dernière synchronisation : {orchestrator.timestamp} | Waves OK : {orchestrator.waves_ok} | Erreurs : {orchestrator.waves_err}
            </div>
          )}
          <MapContainer center={[15, 0]} zoom={2} style={{ height: "500px", width: "100%", borderRadius: "1rem" }}>
            <TileLayer
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution="© OpenStreetMap contributors"
            />
            {agents.map((a, i) => (
              <CircleMarker
                key={i}
                center={[a.lat, a.lng]}
                radius={8}
                pathOptions={{
                  color: a.status === "online" ? "#00FFE0" : "#F87171",
                  fillOpacity: 0.6
                }}
              >
                <Tooltip>
                  <div>
                    <strong>{a.name}</strong><br />
                    Statut : {a.status}<br />
                    Latence : {a.latency} ms<br />
                    Dernière MAJ : {a.updated}
                  </div>
                </Tooltip>
              </CircleMarker>
            ))}
          </MapContainer>
        </CardContent>
      </Card>

      <footer className="mt-6 text-gray-500 text-sm">
        Sentinel Quantum Vanguard AI Pro – Wave 21 Realtime Map
      </footer>
    </div>
  );
}
