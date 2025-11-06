import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Shield } from "lucide-react";

import Journal from "./pages/Journal";
import PegasusScan from "./pages/PegasusScan";
import TestIA from "./pages/TestIA";
import Telechargement from "./pages/Telechargement";
import ThreatMap from "./pages/ThreatMap";
import About from "./pages/About";
import Pricing from "./pages/Pricing";
import VerificationParticulier from "./pages/VerificationParticulier";
import VerificationProfessionnel from "./pages/VerificationProfessionnel";

export default function App() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-sentinel-dark text-white font-sans">
      {/* Fond animé IA */}
      <div className="absolute inset-0 opacity-30 pointer-events-none animate-pulse bg-gradient-to-br from-sentinel-dark via-black to-sentinel-glow"></div>

      <Router>
        {/* Barre de navigation */}
        <nav className="relative z-10 bg-black/60 backdrop-blur border-b border-sentinel-glow/20 p-4 flex flex-col md:flex-row items-center justify-between">
          <h1 className="font-bold text-sentinel-accent text-sm md:text-base flex items-center gap-2">
            <Shield size={18} /> Sentinel Quantum Vanguard AI Pro
          </h1>
          <div className="space-x-3 text-xs md:text-sm mt-2 md:mt-0">
            <Link to="/" className="hover:text-sentinel-accent">Journal</Link>
            <Link to="/pegasus-scan" className="hover:text-sentinel-accent">Pegasus Scan</Link>
            <Link to="/test-ia" className="hover:text-sentinel-accent">Test IA</Link>
            <Link to="/telechargement" className="hover:text-sentinel-accent">Téléchargement</Link>
            <Link to="/threatmap" className="hover:text-sentinel-accent">Threat Map</Link>
            <Link to="/about" className="hover:text-sentinel-accent">À propos</Link>
            <Link to="/pricing" className="hover:text-sentinel-accent">Tarifs</Link>
            <Link to="/verification/particulier" className="hover:text-sentinel-accent">Vérif. Particulier</Link>
            <Link to="/verification/professionnel" className="hover:text-sentinel-accent">Vérif. Pro</Link>
          </div>
        </nav>

        {/* Contenu principal animé */}
        <motion.main
          className="relative z-10 p-5 md:p-10"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
        >
          <Routes>
            <Route path="/" element={<Journal />} />
            <Route path="/pegasus-scan" element={<PegasusScan />} />
            <Route path="/test-ia" element={<TestIA />} />
            <Route path="/telechargement" element={<Telechargement />} />
            <Route path="/threatmap" element={<ThreatMap />} />
            <Route path="/about" element={<About />} />
            <Route path="/pricing" element={<Pricing />} />
            <Route path="/verification/particulier" element={<VerificationParticulier />} />
            <Route path="/verification/professionnel" element={<VerificationProfessionnel />} />
          </Routes>
        </motion.main>

        {/* Pied de page */}
        <footer className="relative z-10 text-center py-3 text-xs text-gray-500 border-t border-sentinel-glow/10">
          <p>© 2025 Sentinel Quantum Vanguard AI Pro — All systems monitored ⚡</p>
        </footer>
      </Router>
    </div>
  );
}