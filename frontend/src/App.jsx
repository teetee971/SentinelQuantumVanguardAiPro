import { Routes, Route } from "react-router-dom";
// eslint-disable-next-line no-unused-vars
import { motion } from "framer-motion";

import ResponsiveNav from "./components/ResponsiveNav";
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
    <div className="relative min-h-screen overflow-hidden bg-black bg-sentinel-dark text-white font-sans">
      {/* Fond animé IA (opacité réduite pour meilleur contraste) */}
      <div className="absolute inset-0 opacity-10 pointer-events-none animate-pulse bg-gradient-to-br from-sentinel-dark via-black to-sentinel-glow"></div>

      {/* Barre de navigation responsive */}
      <ResponsiveNav />

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
    </div>
  );
}