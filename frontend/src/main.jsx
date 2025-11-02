import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import App from "./App.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import VpnConsole from "./pages/admin/vpn-console.jsx";
import Diagnostic from "./pages/Diagnostic.jsx";

// Cybersecurity modules
import SentinelCoreDefense from "./pages/modules/cybersecurity/SentinelCoreDefense.jsx";
import QuantumFailover from "./pages/modules/cybersecurity/QuantumFailover.jsx";
import FireGuard from "./pages/modules/cybersecurity/FireGuard.jsx";
import AutoVerifier from "./pages/modules/cybersecurity/AutoVerifier.jsx";
import CloudArmorian from "./pages/modules/cybersecurity/CloudArmorian.jsx";

// Intelligence modules
import ThreatMapGlobal from "./pages/modules/intelligence/ThreatMapGlobal.jsx";
import HealthMapper from "./pages/modules/intelligence/HealthMapper.jsx";
import GeoFailover from "./pages/modules/intelligence/GeoFailover.jsx";
import IncidentPredictor from "./pages/modules/intelligence/IncidentPredictor.jsx";
import LoadBalancer from "./pages/modules/intelligence/LoadBalancer.jsx";
import FailoverWatcher from "./pages/modules/intelligence/FailoverWatcher.jsx";

// Detection modules
import PegasusScan from "./pages/modules/detection/PegasusScan.jsx";
import PegasusHunter from "./pages/modules/detection/PegasusHunter.jsx";
import LieSense from "./pages/modules/detection/LieSense.jsx";
import LipScanVision from "./pages/modules/detection/LipScanVision.jsx";
import BioSnap from "./pages/modules/detection/BioSnap.jsx";

import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/admin/vpn-console" element={<VpnConsole />} />
      <Route path="/diagnostic" element={<Diagnostic />} />
      
      {/* Cybersecurity modules */}
      <Route path="/modules/sentinel-core-defense" element={<SentinelCoreDefense />} />
      <Route path="/modules/quantum-failover" element={<QuantumFailover />} />
      <Route path="/modules/fireguard" element={<FireGuard />} />
      <Route path="/modules/autoverifier" element={<AutoVerifier />} />
      <Route path="/modules/cloudarmorian" element={<CloudArmorian />} />
      
      {/* Intelligence modules */}
      <Route path="/modules/threatmap-global" element={<ThreatMapGlobal />} />
      <Route path="/modules/health-mapper" element={<HealthMapper />} />
      <Route path="/modules/geofailover" element={<GeoFailover />} />
      <Route path="/modules/incident-predictor" element={<IncidentPredictor />} />
      <Route path="/modules/loadbalancer" element={<LoadBalancer />} />
      <Route path="/modules/failover-watcher" element={<FailoverWatcher />} />
      
      {/* Detection modules */}
      <Route path="/modules/pegasus-scan" element={<PegasusScan />} />
      <Route path="/modules/pegasus-hunter" element={<PegasusHunter />} />
      <Route path="/modules/liesense" element={<LieSense />} />
      <Route path="/modules/lipscan-vision" element={<LipScanVision />} />
      <Route path="/modules/biosnap" element={<BioSnap />} />
    </Routes>
  </BrowserRouter>
);
