import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import App from "./App.jsx";
import VpnConsole from "./pages/admin/vpn-console.jsx";
import SentinelConsole from "./pages/admin/sentinel-console.jsx";
import Diagnostic from "./pages/Diagnostic.jsx";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/admin/vpn-console" element={<VpnConsole />} />
      <Route path="/admin/sentinel-console" element={<SentinelConsole />} />
      <Route path="/diagnostic" element={<Diagnostic />} />
    </Routes>
  </BrowserRouter>
);
