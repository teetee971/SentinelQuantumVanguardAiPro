import { Link } from "react-router-dom";
import { useState } from "react";

export default function Navbar() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <nav className="fixed top-0 left-0 w-full bg-black/90 backdrop-blur-md text-white border-b border-blue-900/50 z-50 shadow-lg shadow-blue-900/20">
      <div className="max-w-7xl mx-auto flex items-center justify-between p-4">
        {/* Logo */}
        <Link 
          to="/" 
          className="text-xl md:text-2xl font-bold text-blue-400 hover:text-blue-300 transition flex items-center space-x-2"
        >
          <span className="text-2xl">🛡️</span>
          <span className="hidden md:inline">Sentinel Quantum Vanguard AI Pro</span>
          <span className="md:hidden">Sentinel</span>
        </Link>

        {/* Desktop Menu */}
        <div className="hidden md:flex space-x-6 text-sm lg:text-base items-center">
          <Link 
            to="/" 
            className="hover:text-blue-400 transition-colors duration-200"
          >
            Accueil
          </Link>
          <Link 
            to="/diagnostic" 
            className="hover:text-blue-400 transition-colors duration-200"
          >
            🔍 Diagnostic
          </Link>
          <Link 
            to="/admin/vpn-console" 
            className="hover:text-green-400 transition-colors duration-200"
          >
            🌐 Console VPN
          </Link>
          <Link 
            to="/telechargement" 
            className="bg-blue-600 hover:bg-blue-500 px-4 py-2 rounded-lg font-semibold transition-colors duration-200 flex items-center space-x-1 shadow-lg shadow-blue-600/30"
          >
            <span>📄</span>
            <span>Téléchargement</span>
          </Link>
        </div>

        {/* Mobile Menu Button */}
        <button
          onClick={() => setIsMenuOpen(!isMenuOpen)}
          className="md:hidden text-2xl focus:outline-none hover:text-blue-400 transition"
          aria-label="Toggle menu"
        >
          {isMenuOpen ? "✕" : "☰"}
        </button>
      </div>

      {/* Mobile Menu */}
      {isMenuOpen && (
        <div className="md:hidden bg-black/95 backdrop-blur-md border-t border-blue-900/50">
          <div className="flex flex-col space-y-4 p-4">
            <Link 
              to="/" 
              className="hover:text-blue-400 transition-colors duration-200 py-2"
              onClick={() => setIsMenuOpen(false)}
            >
              Accueil
            </Link>
            <Link 
              to="/diagnostic" 
              className="hover:text-blue-400 transition-colors duration-200 py-2"
              onClick={() => setIsMenuOpen(false)}
            >
              🔍 Diagnostic
            </Link>
            <Link 
              to="/admin/vpn-console" 
              className="hover:text-green-400 transition-colors duration-200 py-2"
              onClick={() => setIsMenuOpen(false)}
            >
              🌐 Console VPN
            </Link>
            <Link 
              to="/telechargement" 
              className="bg-blue-600 hover:bg-blue-500 px-4 py-3 rounded-lg font-semibold transition-colors duration-200 text-center"
              onClick={() => setIsMenuOpen(false)}
            >
              📄 Téléchargement
            </Link>
          </div>
        </div>
      )}
    </nav>
  );
}
