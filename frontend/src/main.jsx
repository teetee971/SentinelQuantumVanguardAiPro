import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import App from "./App.jsx";
import VpnConsole from "./pages/admin/vpn-console.jsx";
import Diagnostic from "./pages/Diagnostic.jsx";
import Journal from "./pages/Journal.jsx";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/admin/vpn-console" element={<VpnConsole />} />
      <Route path="/diagnostic" element={<Diagnostic />} />
      <Route path="/journal" element={<Journal />} />
    </Routes>
  </BrowserRouter>
);
