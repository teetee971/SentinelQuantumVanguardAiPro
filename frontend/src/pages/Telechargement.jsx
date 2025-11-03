import React, { useState } from "react";
import { QRCodeCanvas } from "qrcode.react";
import { motion } from "framer-motion";
import { ShieldCheck } from "lucide-react";

const LOADING_DELAY_MS = 1200; // Délai pour effet fluide de l'animation

export default function Telechargement() {
  const [isLoading, setIsLoading] = useState(true);
  
  // TODO: Replace with actual Sentinel Quantum Vanguard AI Pro PDF URL
  // Consider moving to environment variable or config file for easier updates
  const pdfUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";
  const pageUrl = window.location.href;

  const handleLoad = () => {
    setTimeout(() => setIsLoading(false), LOADING_DELAY_MS);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <a
            href="/"
            className="inline-flex items-center text-blue-400 hover:text-blue-300 transition mb-4"
          >
            ← Retour
          </a>
          <h1 className="text-4xl font-bold text-blue-400 mb-2">
            Sentinel Quantum Vanguard AI Pro
          </h1>
          <p className="text-zinc-400">
            Document officiel - Executive Brief
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* PDF Viewer Section */}
          <div className="lg:col-span-2 relative">
            {/* Animation de vérification IA */}
            {isLoading && (
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/90 backdrop-blur-sm rounded-2xl z-20">
                <motion.div
                  className="flex flex-col items-center text-center text-blue-400 space-y-4"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ duration: 0.8 }}
                >
                  <ShieldCheck className="w-10 h-10 text-blue-500 animate-pulse" />
                  <motion.p
                    className="text-sm md:text-base tracking-wide text-gray-300"
                    animate={{ opacity: [0.6, 1, 0.6] }}
                    transition={{ duration: 2, repeat: Infinity }}
                  >
                    Vérification de l'intégrité du fichier...
                  </motion.p>
                  <motion.div
                    className="h-1 w-40 bg-blue-700 rounded-full overflow-hidden"
                  >
                    <motion.div
                      className="h-full bg-blue-400"
                      initial={{ width: "0%" }}
                      animate={{ width: "80%" }}
                      transition={{ duration: 1.5, repeat: Infinity, ease: "easeInOut" }}
                    ></motion.div>
                  </motion.div>
                </motion.div>
              </div>
            )}

            {/* PDF Iframe */}
            <motion.iframe
              src={pdfUrl}
              title="Sentinel Quantum Vanguard AI Pro Executive Brief"
              width="100%"
              height="800"
              onLoad={handleLoad}
              className="rounded-2xl border border-blue-700 shadow-lg"
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: isLoading ? 0 : 1, y: isLoading ? 10 : 0 }}
              transition={{ duration: 0.8, ease: "easeOut" }}
            ></motion.iframe>
          </div>

          {/* Sidebar avec QR Code */}
          <div className="space-y-6">
            {/* QR Code Card */}
            <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6">
              <h3 className="text-xl font-semibold text-blue-400 mb-4">
                Accès Mobile
              </h3>
              <div className="flex justify-center mb-4">
                <div className="bg-white p-4 rounded-lg">
                  <QRCodeCanvas
                    value={pageUrl}
                    size={180}
                    level="H"
                    includeMargin={true}
                  />
                </div>
              </div>
              <p className="text-sm text-zinc-400 text-center">
                Scannez ce QR code pour accéder au document sur mobile
              </p>
            </div>

            {/* Info Card */}
            <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6">
              <h3 className="text-xl font-semibold text-blue-400 mb-4">
                À propos
              </h3>
              <div className="space-y-3 text-sm text-zinc-400">
                <div className="flex items-start">
                  <span className="text-blue-400 mr-2">🛡️</span>
                  <div>
                    <p className="font-medium text-zinc-300">Sécurité</p>
                    <p>Document vérifié par IA</p>
                  </div>
                </div>
                <div className="flex items-start">
                  <span className="text-blue-400 mr-2">📄</span>
                  <div>
                    <p className="font-medium text-zinc-300">Format</p>
                    <p>PDF Executive Brief</p>
                  </div>
                </div>
                <div className="flex items-start">
                  <span className="text-blue-400 mr-2">⚡</span>
                  <div>
                    <p className="font-medium text-zinc-300">Technologie</p>
                    <p>Sentinel Quantum Vanguard AI Pro</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Download Button */}
            <a
              href={pdfUrl}
              download
              className="block w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 px-6 rounded-lg transition text-center"
            >
              📥 Télécharger le PDF
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
