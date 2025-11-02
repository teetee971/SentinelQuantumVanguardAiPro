import { Link } from "react-router-dom";

export default function Navbar() {
  return (
    <nav className="fixed top-0 left-0 w-full bg-black/80 backdrop-blur-md text-white border-b border-blue-900 z-50">
      <div className="max-w-6xl mx-auto flex items-center justify-between p-4">
        {/* Logo */}
        <Link to="/" className="text-xl font-bold text-blue-400 hover:text-blue-300 transition">
          Sentinel Quantum Vanguard AI Pro
        </Link>

        {/* Menu Links */}
        <div className="flex space-x-6 text-sm md:text-base">
          <Link to="/" className="hover:text-blue-400 transition">
            Accueil
          </Link>
          <Link to="/diagnostic" className="hover:text-blue-400 transition">
            Diagnostic
          </Link>
          <Link to="/admin/vpn-console" className="hover:text-blue-400 transition">
            Console VPN
          </Link>
          <Link 
            to="/telechargement" 
            className="hover:text-blue-400 font-semibold transition flex items-center gap-1"
          >
            📄 Téléchargement
          </Link>
        </div>
      </div>
    </nav>
  );
}
