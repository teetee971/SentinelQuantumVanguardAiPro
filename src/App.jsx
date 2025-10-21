import React from "react";
import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import Dashboard from "./dashboard/Dashboard";
import Agents from "./admin/Agents";

export default function App() {
  return (
    <Router>
      <div className="min-h-screen bg-[#0f172a] text-white">
        {/* Barre de navigation */}
        <nav className="flex justify-between items-center p-4 bg-[#1e293b] shadow-md">
          <div className="flex items-center space-x-3">
            <img
              src="/assets/sentinel_logo.png"
              alt="Sentinel Quantum Vanguard AI Pro"
              className="w-10 h-10 rounded-full border border-blue-500/40 shadow-lg"
            />
            <h1 className="text-lg font-semibold tracking-wide">
              Sentinel Quantum Vanguard AI Pro
            </h1>
          </div>

          <div className="flex space-x-6 text-sm">
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

        {/* Contenu principal */}
        <div className="pt-6 px-4">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/admin/agents" element={<Agents />} />
          </Routes>
        </div>
      </div>
    </Router>
  );
}
