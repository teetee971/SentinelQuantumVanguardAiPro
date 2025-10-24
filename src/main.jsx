// == Sentinel Quantum Vanguard AI Pro — Core Router ==
// Version : Full web React Router / Tailwind Ready
// Auteur : Sentinel DevOps AI Network
// Description : Gestionnaire global des routes et du rendu principal
// =====================================================

// ✅ Import Tailwind CSS
import './index.css'

import React from "react"
import ReactDOM from "react-dom/client"
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import App from "./App"
import ThreatMap from "./pages/ThreatMap"
import Documentation from "./pages/Documentation" // Optionnel

// === Rendu principal ===
ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        {/* Page d'accueil */}
        <Route path="/" element={<App />} />

        {/* Carte des Menaces IA Globale */}
        <Route path="/threatmap" element={<ThreatMap />} />

        {/* Documentation Sentinel */}
        <Route path="/documentation" element={<Documentation />} />

        {/* Redirection par défaut */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
)
