import Navbar from "./components/Navbar";
import { Link } from "react-router-dom";

export default function App() {
  return (
    <>
      <Navbar />
      <div className="min-h-screen bg-zinc-950 text-zinc-100 pt-24 pb-12 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <h1 className="text-4xl md:text-5xl font-bold text-blue-400 mb-6">
            Sentinel Quantum Vanguard AI Pro
          </h1>
          <p className="text-zinc-400 mb-8 text-lg">
            Système mondial de cybersécurité et de protection autonome
          </p>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-12">
            <Link
              to="/diagnostic"
              className="bg-zinc-900 border border-zinc-800 hover:border-blue-500 p-8 rounded-lg transition-all group shadow-lg hover:shadow-blue-500/20"
            >
              <div className="text-4xl mb-4">🔍</div>
              <h2 className="text-xl font-semibold mb-2 group-hover:text-blue-400 transition">
                Diagnostic Système
              </h2>
              <p className="text-zinc-500 text-sm">
                Surveillance en temps réel des performances et de l'état du système
              </p>
            </Link>

            <Link
              to="/admin/vpn-console"
              className="bg-zinc-900 border border-zinc-800 hover:border-green-500 p-8 rounded-lg transition-all group shadow-lg hover:shadow-green-500/20"
            >
              <div className="text-4xl mb-4">🛡️</div>
              <h2 className="text-xl font-semibold mb-2 group-hover:text-green-400 transition">
                Console VPN
              </h2>
              <p className="text-zinc-500 text-sm">
                Gestion et contrôle des nœuds VPN Sentinel
              </p>
            </Link>

            <Link
              to="/telechargement"
              className="bg-zinc-900 border border-zinc-800 hover:border-blue-500 p-8 rounded-lg transition-all group shadow-lg hover:shadow-blue-500/20"
            >
              <div className="text-4xl mb-4">📄</div>
              <h2 className="text-xl font-semibold mb-2 group-hover:text-blue-400 transition">
                Téléchargement
              </h2>
              <p className="text-zinc-500 text-sm">
                Documentation officielle et ressources du système
              </p>
            </Link>
          </div>
        </div>
      </div>
    </>
  );
}
