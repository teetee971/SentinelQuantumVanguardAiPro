import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import App from "./App.jsx";
import VpnConsole from "./pages/admin/vpn-console.jsx";
import LogsAdmin from "./pages/admin/logs.jsx";
import Diagnostic from "./pages/Diagnostic.jsx";
import Telechargement from "./pages/Telechargement.jsx";
import Journal from "./pages/Journal.jsx";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/admin/vpn-console" element={<VpnConsole />} />
      <Route path="/admin/logs" element={<LogsAdmin />} />
      <Route path="/diagnostic" element={<Diagnostic />} />
      <Route path="/telechargement" element={<Telechargement />} />
      <Route path="/journal" element={<Journal />} />
    </Routes>
  </BrowserRouter>
);
