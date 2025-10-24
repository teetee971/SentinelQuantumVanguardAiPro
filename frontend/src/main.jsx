// === Sentinel Quantum Vanguard AI Pro – Core Router ===
// Version : Full +++ React Router / Tailwind Ready
// Auteur : Sentinel DevOps AI Network
// Description : Gestionnaire global des routes et du rendu principal
// ======================================================

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import "./index.css";

// === Import des pages principales ===
import App from "./App";
import ThreatMap from "./pages/ThreatMap";
import Documentation from "./pages/Documentation"; // (optionnelle, tu peux créer le fichier plus tard)

// === Rendu principal ===
createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>

        {/* Page d'accueil */}
        <Route path="/" element={<App />} />

        {/* Carte de Menaces IA Globale */}
        <Route path="/threatmap" element={<ThreatMap />} />

        {/* Documentation Sentinel */}
        <Route path="/documentation" element={<Documentation />} />

        {/* Redirection par défaut */}
        <Route path="*" element={<Navigate to="/" replace />} />

      </Routes>
    </BrowserRouter>
  </StrictMode>
);
