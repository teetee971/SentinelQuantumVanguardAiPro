import { useState } from "react";
import { Link } from "react-router-dom";
import { Shield, Menu, X } from "lucide-react";

export default function ResponsiveNav() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  const toggleMenu = () => setIsMenuOpen(!isMenuOpen);

  const navLinks = [
    { to: "/", label: "Journal" },
    { to: "/pegasus-scan", label: "Pegasus Scan" },
    { to: "/test-ia", label: "Test IA" },
    { to: "/telechargement", label: "Téléchargement" },
    { to: "/threatmap", label: "Threat Map" },
    { to: "/about", label: "À propos" },
    { to: "/pricing", label: "Tarifs" },
    { to: "/verification/particulier", label: "Vérif. Particulier" },
    { to: "/verification/professionnel", label: "Vérif. Pro" },
  ];

  return (
    <nav className="relative z-10 bg-black/60 backdrop-blur border-b border-sentinel-glow/20">
      <div className="container mx-auto px-4 py-3">
        <div className="flex items-center justify-between">
          {/* Logo */}
          <h1 className="font-bold text-sentinel-accent text-sm md:text-base flex items-center gap-2">
            <Shield size={18} aria-hidden="true" /> 
            <span className="hidden sm:inline">Sentinel Quantum Vanguard AI Pro</span>
            <span className="sm:hidden">Sentinel QV</span>
          </h1>

          {/* Desktop Navigation */}
          <div className="hidden lg:flex items-center gap-3 text-xs xl:text-sm">
            {navLinks.map((link) => (
              <Link 
                key={link.to} 
                to={link.to} 
                className="hover:text-sentinel-accent transition-colors px-2 py-2"
              >
                {link.label}
              </Link>
            ))}
            <Link
              to="/pricing?utm_source=navbar&utm_medium=button&utm_campaign=trial-14d"
              className="btn ml-2 px-4 py-2 rounded bg-sentinel-accent text-black font-semibold hover:bg-sentinel-accent/90 transition-colors"
            >
              Essai 14 j
            </Link>
          </div>

          {/* Mobile Menu Button */}
          <button
            onClick={toggleMenu}
            className="lg:hidden btn p-2 rounded hover:bg-white/10 transition-colors"
            aria-label={isMenuOpen ? "Fermer le menu" : "Ouvrir le menu"}
            aria-expanded={isMenuOpen}
          >
            {isMenuOpen ? <X size={24} /> : <Menu size={24} />}
          </button>
        </div>

        {/* Mobile Navigation Menu */}
        {isMenuOpen && (
          <div className="lg:hidden mt-4 pb-4 border-t border-sentinel-glow/20 pt-4">
            <div className="flex flex-col gap-2">
              {navLinks.map((link) => (
                <Link
                  key={link.to}
                  to={link.to}
                  className="btn hover:bg-white/10 px-4 py-3 rounded text-sm transition-colors text-left"
                  onClick={() => setIsMenuOpen(false)}
                >
                  {link.label}
                </Link>
              ))}
              <Link
                to="/pricing?utm_source=navbar&utm_medium=button&utm_campaign=trial-14d"
                className="btn mt-2 px-4 py-3 rounded bg-sentinel-accent text-black font-semibold hover:bg-sentinel-accent/90 transition-colors text-center"
                onClick={() => setIsMenuOpen(false)}
              >
                Essai 14 j
              </Link>
            </div>
          </div>
        )}
      </div>
    </nav>
  );
}
