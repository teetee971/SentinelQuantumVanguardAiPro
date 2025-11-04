import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { MapContainer, TileLayer, CircleMarker, Popup } from "react-leaflet";
import "leaflet/dist/leaflet.css";

export default function Journal() {
  const [events, setEvents] = useState([]);
  const [points, setPoints] = useState([]);

  useEffect(() => {
    // génération de quelques points simulés sur la carte
    const simulated = Array.from({ length: 25 }, () => ({
      id: Math.random().toString(36).substring(2, 8),
      lat: -60 + Math.random() * 120,
      lng: -160 + Math.random() * 320,
      level: Math.random() > 0.7 ? "🔥" : "🟢",
      message:
        Math.random() > 0.5
          ? "Activité IA suspecte détectée"
          : "Agent Sentinel en surveillance active",
    }));
    setPoints(simulated);
  }, []);

  useEffect(() => {
    // simulation du flux d’événements IA
    const interval = setInterval(() => {
      const newEvent = {
        id: Date.now(),
        time: new Date().toLocaleTimeString("fr-FR"),
        type: Math.random() > 0.8 ? "⚠️ Alerte" : "✅ Log normal",
        msg:
          Math.random() > 0.5
            ? "Méta-agent a corrigé une dérive réseau."
            : "Nouvelle clé API vérifiée avec succès.",
      };
      setEvents((prev) => [newEvent, ...prev.slice(0, 40)]);
    }, 3500);
    return () => clearInterval(interval);
  }, []);

  return (
    <div
      style={{
        background: "radial-gradient(circle at 20% 20%, #050505 0%, #000 100%)",
        color: "#fff",
        fontFamily: "Inter, sans-serif",
        minHeight: "100vh",
        padding: "6rem 1rem 3rem",
      }}
    >
      <motion.h1
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        style={{
          fontSize: "2rem",
          fontWeight: "700",
          color: "#00ffcc",
          textAlign: "center",
          textShadow: "0 0 15px #00ffcc55",
          marginBottom: "1.5rem",
        }}
      >
        🌍 Journal d’Activité IA — ThreatMap Sentinel
      </motion.h1>

      {/* Carte mondiale */}
      <div
        style={{
          width: "100%",
          maxWidth: "900px",
          margin: "auto",
          borderRadius: "12px",
          overflow: "hidden",
          boxShadow: "0 0 20px #00ffcc22",
          border: "1px solid #00ffcc33",
          marginBottom: "2rem",
        }}
      >
        <MapContainer
          center={[20, 0]}
          zoom={2}
          scrollWheelZoom={false}
          style={{ height: "400px", width: "100%" }}
        >
          <TileLayer
            attribution='&copy; OpenStreetMap'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          {points.map((p) => (
            <CircleMarker
              key={p.id}
              center={[p.lat, p.lng]}
              radius={6}
              pathOptions={{
                color: p.level === "🔥" ? "#ff3333" : "#00ffcc",
                fillColor: p.level === "🔥" ? "#ff6666" : "#00ffcc",
                fillOpacity: 0.6,
              }}
            >
              <Popup>
                <strong>{p.level}</strong> — {p.message}
              </Popup>
            </CircleMarker>
          ))}
        </MapContainer>
      </div>

      {/* Journal IA */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.4 }}
        style={{
          width: "90%",
          maxWidth: "900px",
          margin: "auto",
          backgroundColor: "#0a0a0a",
          borderRadius: "12px",
          border: "1px solid #00ffcc33",
          padding: "1.5rem",
          boxShadow: "0 0 25px #00ffcc22",
        }}
      >
        <h3 style={{ color: "#00ffcc", marginBottom: "1rem" }}>
          📜 Journal IA en temps réel
        </h3>
        <div
          style={{
            background: "#111",
            borderRadius: "8px",
            padding: "1rem",
            height: "300px",
            overflowY: "auto",
            fontFamily: "monospace",
            fontSize: "0.9rem",
            color: "#ccc",
          }}
        >
          {events.length > 0 ? (
            events.map((e) => (
              <div
                key={e.id}
                style={{
                  marginBottom: "0.5rem",
                  color: e.type.includes("⚠️") ? "#ff6666" : "#00ffcc",
                }}
              >
                {e.time} — {e.type} — {e.msg}
              </div>
            ))
          ) : (
            <p style={{ color: "#555" }}>En attente d’activité IA...</p>
          )}
        </div>
      </motion.div>

      {/* Footer */}
      <footer
        style={{
          textAlign: "center",
          color: "#555",
          fontSize: "0.9rem",
          marginTop: "3rem",
        }}
      >
        Sentinel Quantum Vanguard AI Pro © 2025 — Module Threat Intelligence
      </footer>
    </div>
  );
}
