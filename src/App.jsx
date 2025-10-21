
import React from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Dashboard from "./dashboard/Dashboard";
import Agents from "./admin/Agents";

export default function App() {
  return (
    <Router>
      <div className="min-h-screen bg-[#0f172a] text-white">
        <Routes>
          {/* Tableau de bord principal */}
          <Route path="/" element={<Dashboard />} />

          {/* Console admin IA */}
          <Route path="/admin/agents" element={<Agents />} />
        </Routes>
      </div>
    </Router>
  );
}
