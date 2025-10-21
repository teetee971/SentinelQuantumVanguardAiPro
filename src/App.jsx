import React from "react";
import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import Dashboard from "./dashboard/Dashboard";
import Agents from "./admin/Agents";

export default function App() {
  return (
    <Router>
      <div className="min-h-screen bg-[#0f172a] text-white">
        {/* Barre de navigation */}
        <nav className="bg-[#1e293b] shadow-lg border-b border-blue-800/40 px-6 py-3 flex justify-between items-center sticky top-0 z-50">
          <h1 className="text-xl font-bold text-blue-400 tracking-wide">
            Sentinel Quantum Vanguard AI Pro
          </h1>
          <div className="flex gap-6">
            <Link
              to="/"
              className="hover:text-blue-400 transition-colors duration-200"
            >
              Accueil
            </Link>
            <Link
              to="/admin/agents"
              className="hover:text-blue-400 transition-colors duration-200"
            >
              Console IA Admin
            </Link>
          </div>
        </nav>

        {/* Routes principales */}
        <div className="p-6">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/admin/agents" element={<Agents />} />
          </Routes>
        </div>
      </div>
    </Router>
  );
}
