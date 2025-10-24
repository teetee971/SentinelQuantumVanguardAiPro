import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Shield, Radar, Download, Brain } from "lucide-react";
import Journal from "./pages/Journal";
import PegasusScan from "./pages/PegasusScan";
import TestIA from "./pages/TestIA";
import Telechargement from "./pages/Telechargement";

export default function App() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-sentinel-dark text-white">
      {/* Fond animé IA */}
      <div className="absolute inset-0 opacity-30 pointer-events-none animate-pulse bg-[url('https://raw.githubusercontent.com/teetee971/assets/main/threatmap-grid.gif')] bg-cover bg-center"></div>

      <Router>
        {/* Barre de navigation */}
        <nav className="relative z-10 bg-black/60 backdrop-blur border-b border-sentinel-accent p-3 flex justify-between items-center">
          <h1 className="font-bold text-sentinel-accent text-sm md:text-base flex items-center gap-2">
            <Shield size={16} /> Sentinel Quantum Vanguard AI Pro
          </h1>
          <div className="space-x-3 text-xs md:text-sm">
            <Link to="/" className="hover:text-sentinel-accent">Journal</Link>
            <Link to="/pegasus-scan" className="hover:text-sentinel-accent">Pegasus Scan</Link>
            <Link to="/test-ia" className="hover:text-sentinel-accent">Test IA</Link>
            <Link to="/telechargement" className="hover:text-sentinel-accent">Téléchargement</Link>
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
          </Routes>
        </motion.main>

        {/* Pied de page */}
        <footer className="relative z-10 text-center py-3 text-xs text-gray-500 border-t border-gray-700 bg-black/60 backdrop-blur">
          <p>© 2025 Sentinel Quantum Vanguard AI Pro — All systems monitored ⚡</p>
        </footer>
      </Router>
    </div>
  );
}
