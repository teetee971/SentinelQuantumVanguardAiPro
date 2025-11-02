import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import App from "./App.jsx";
import VpnConsole from "./pages/admin/vpn-console.jsx";
import Diagnostic from "./pages/Diagnostic.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import PegasusScan from "./pages/PegasusScan.jsx";
import ThreatMap from "./pages/ThreatMap.jsx";
import Download from "./pages/Download.jsx";
import MobileDefender from "./pages/MobileDefender.jsx";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/admin/vpn-console" element={<VpnConsole />} />
      <Route path="/diagnostic" element={<Diagnostic />} />
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/pegasus-scan" element={<PegasusScan />} />
      <Route path="/threatmap" element={<ThreatMap />} />
      <Route path="/download" element={<Download />} />
      <Route path="/mobile-defender" element={<MobileDefender />} />
    </Routes>
  </BrowserRouter>
);
