// == Sentinel Quantum Vanguard AI Pro — Core Router ==
// Version : Full web React Router / Tailwind Ready
// Auteur : Sentinel DevOps AI Network
// Description : Gestionnaire global des routes et du rendu principal
// =====================================================

// ✅ Import Tailwind CSS
import './index.css'

// ✅ Import i18n
import './i18n'

import React, { lazy, Suspense } from "react"
import ReactDOM from "react-dom/client"
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import App from "./App"
import LoadingFallback from "./components/LoadingFallback"

// Lazy load heavy components
const ThreatMap = lazy(() => import("./pages/ThreatMap"))
const Documentation = lazy(() => import("./pages/Documentation"))

// === Rendu principal ===
ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <BrowserRouter>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          {/* Page d'accueil */}
          <Route path="/" element={<App />} />

          {/* Carte des Menaces IA Globale */}
          <Route path="/threatmap" element={<ThreatMap />} />

          {/* Documentation Sentinel */}
          <Route path="/documentation" element={<Documentation />} />

          {/* Accès direct vers des pages gérées par App */}
          <Route path="/about" element={<App />} />
          <Route path="/pricing" element={<App />} />           {/* NEW */}
          <Route path="/verification/*" element={<App />} />    {/* NEW */}

          {/* Redirection par défaut */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  </React.StrictMode>
)