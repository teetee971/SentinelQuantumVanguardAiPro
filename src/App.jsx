import React from "react";
import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import Dashboard from "./dashboard/Dashboard";
import Agents from "./admin/Agents";

export default function App() {
  return (
    <Router>
      <div className="min-h-screen bg-[#0b0d12] text-gray-100">
        {/* Barre de navigation */}
        <nav className="fixed top-0 w-full flex items-center justify-between px-6 py-3 bg-[#0b0d12]/90 backdrop-blur-md border-b border-blue-500/30 z-50">
          <div className="flex items-center space-x-3">
            <img
              src="/assets/sentinel_logo.png"
              alt="Sentinel Logo"
              className="w-10 h-10 rounded-full shadow-lg border border-blue-500/40"
            />
            <h1 className="text-xl font-semibold text-blue-400 tracking-wide">
              Sentinel Quantum Vanguard AI Pro
            </h1>
          </div>
          <div className="flex space-x-5 text-sm">
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
        <div className="pt-20 px-6">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/admin/agents" element={<Agents />} />
          </Routes>
        </div>
      </div>
    </Router>
  );
}
