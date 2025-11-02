import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { QRCodeCanvas } from "qrcode.react";
import { ShieldCheck, Lock, FileCheck } from "lucide-react";
import Navbar from "../components/Navbar";

export default function Telechargement() {
  const [pdfStatus, setPdfStatus] = useState("checking");
  const [isPdfLoading, setIsPdfLoading] = useState(true);
  const [isCertified, setIsCertified] = useState(false);
  const [timestamp, setTimestamp] = useState("");

  const pdfUrl = "/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf";
  const fullPdfUrl = `https://sentinelquantumvanguardaipro.pages.dev${pdfUrl}`;

  // Vérifier si le PDF existe
  useEffect(() => {
    fetch(pdfUrl, { method: "HEAD" })
      .then(response => {
        if (response.ok) {
          setPdfStatus("available");
        } else {
          setPdfStatus("unavailable");
        }
      })
      .catch(() => setPdfStatus("unavailable"));
  }, []);

  // Horodatage en temps réel UTC
  useEffect(() => {
    const updateTimestamp = () => {
      const now = new Date();
      const day = String(now.getUTCDate()).padStart(2, '0');
      const month = String(now.getUTCMonth() + 1).padStart(2, '0');
      const year = now.getUTCFullYear();
      const hours = String(now.getUTCHours()).padStart(2, '0');
      const minutes = String(now.getUTCMinutes()).padStart(2, '0');
      const seconds = String(now.getUTCSeconds()).padStart(2, '0');
      const formatted = `${day}/${month}/${year} - ${hours}:${minutes}:${seconds} UTC`;
      setTimestamp(formatted);
    };
    updateTimestamp();
    const interval = setInterval(updateTimestamp, 1000);
    return () => clearInterval(interval);
  }, []);

  // Gérer le chargement du PDF avec séquence de certification
  const handlePdfLoad = () => {
    setTimeout(() => {
      setIsPdfLoading(false);
      // Afficher la certification après la vérification
      setTimeout(() => setIsCertified(true), 800);
      // Masquer la certification après 3 secondes
      setTimeout(() => setIsCertified(false), 4300);
    }, 1200);
  };

  return (
    <>
      <Navbar />
      <div className="min-h-screen bg-zinc-950 text-zinc-100 pt-24 pb-12 px-6">
        <div className="max-w-4xl mx-auto">
          {/* Header with animation */}
          <motion.div 
            className="text-center mb-12"
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, ease: "easeOut" }}
          >
            <div className="text-6xl mb-4">📄</div>
            <h1 className="text-4xl md:text-5xl font-bold text-blue-400 mb-4">
              Téléchargement
            </h1>
            <p className="text-zinc-400 text-lg">
              Documentation officielle Sentinel Quantum Vanguard AI Pro
            </p>
          </motion.div>

          {/* Main Document Card with animation */}
          <motion.div 
            className="bg-zinc-900 border border-zinc-800 rounded-lg p-8 mb-8 hover:border-blue-500/50 transition-all"
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.2, ease: "easeOut" }}
          >
            <div className="flex items-start space-x-4">
              <div className="text-5xl">📑</div>
              <div className="flex-1">
                <h2 className="text-2xl font-bold text-white mb-2">
                  Executive Brief - Version Officielle
                </h2>
                <p className="text-zinc-400 mb-4">
                  Présentation exécutive complète du système Sentinel Quantum Vanguard AI Pro.
                  Document bilingue (Français/English) contenant l'architecture, les modules, 
                  les agents IA, et la feuille de route du projet.
                </p>
                
                <div className="flex flex-wrap gap-2 mb-4">
                  <span className="bg-blue-900/30 text-blue-300 px-3 py-1 rounded-full text-sm">
                    📄 PDF
                  </span>
                  <span className="bg-green-900/30 text-green-300 px-3 py-1 rounded-full text-sm">
                    🌍 Bilingue FR/EN
                  </span>
                  <span className="bg-purple-900/30 text-purple-300 px-3 py-1 rounded-full text-sm">
                    🔒 Document Officiel
                  </span>
                </div>

                {/* Button and QR Code Section with animations */}
                <div className="flex flex-col md:flex-row items-start md:items-center gap-6">
                  {pdfStatus === "available" ? (
                    <>
                      <motion.a
                        href={pdfUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-block bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-500 hover:to-blue-400 text-white font-semibold px-6 py-3 rounded-lg transition-all shadow-lg shadow-blue-600/30 hover:shadow-blue-500/40"
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.7, delay: 0.4 }}
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                      >
                        ⬇️ Télécharger le PDF
                      </motion.a>
                      
                      {/* QR Code officiel Sentinel avec effet holographique */}
                      <motion.div 
                        className="relative flex flex-col items-center bg-blue-950/40 border-2 border-blue-700 rounded-2xl p-4 shadow-lg animate-pulse-sentinel"
                        initial={{ opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ duration: 0.8, delay: 0.6, ease: "easeOut" }}
                      >
                        {/* Halo holographique animé */}
                        <div className="absolute inset-0 rounded-2xl bg-blue-500/20 blur-xl pointer-events-none"></div>
                        
                        {/* QR Code */}
                        <div className="relative z-10">
                          <QRCodeCanvas
                            value={fullPdfUrl}
                            size={140}
                            bgColor="#000000"
                            fgColor="#00BFFF"
                            level="H"
                            includeMargin={true}
                            imageSettings={{
                              src: "https://sentinelquantumvanguardaipro.pages.dev/assets/logo-sentinel-color.png",
                              x: undefined,
                              y: undefined,
                              height: 32,
                              width: 32,
                              excavate: true,
                            }}
                          />
                        </div>
                        
                        <p className="text-xs text-blue-300 mt-3 font-semibold text-center relative z-10">
                          📱 Scanner pour mobile
                        </p>
                      </motion.div>
                    </>
                  ) : pdfStatus === "checking" ? (
                    <div className="bg-zinc-800 text-zinc-400 px-6 py-3 rounded-lg inline-block">
                      <span className="animate-pulse">⏳ Vérification...</span>
                    </div>
                  ) : (
                    <div className="bg-yellow-900/30 border border-yellow-700 text-yellow-300 px-6 py-3 rounded-lg">
                      ⚠️ Document en préparation - Disponible prochainement
                    </div>
                  )}
                </div>
              </div>
            </div>
          </motion.div>

          {/* PDF Preview Section with AI Verification Loading */}
          {pdfStatus === "available" && (
            <motion.div
              className="relative mb-8 rounded-2xl overflow-hidden"
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.9 }}
            >
              {/* AI Verification Loading Overlay */}
              <AnimatePresence mode="wait">
                {isPdfLoading && (
                  <motion.div
                    key="loading"
                    className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/95 backdrop-blur-md rounded-2xl border-2 border-blue-500/30"
                    initial={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.6 }}
                  >
                    {/* Animated Shield Icon */}
                    <motion.div
                      animate={{
                        scale: [1, 1.2, 1],
                        rotate: [0, 5, -5, 0],
                      }}
                      transition={{
                        duration: 2,
                        repeat: Infinity,
                        ease: "easeInOut"
                      }}
                    >
                      <ShieldCheck className="w-16 h-16 text-blue-400 mb-4" strokeWidth={1.5} />
                    </motion.div>

                    {/* Animated Text */}
                    <motion.div className="text-center">
                      <motion.h3
                        className="text-xl font-bold text-blue-400 mb-2"
                        animate={{ opacity: [0.5, 1, 0.5] }}
                        transition={{ duration: 2, repeat: Infinity }}
                      >
                        🔐 Vérification de l'intégrité IA
                      </motion.h3>
                      <p className="text-zinc-400 text-sm">
                        Analyse sécurisée en cours...
                      </p>

                      {/* Progress indicators */}
                      <div className="flex items-center justify-center space-x-4 mt-6">
                        <motion.div
                          animate={{ scale: [1, 1.3, 1], opacity: [0.5, 1, 0.5] }}
                          transition={{ duration: 1.5, repeat: Infinity, delay: 0 }}
                        >
                          <Lock className="w-5 h-5 text-blue-500" />
                        </motion.div>
                        <motion.div
                          animate={{ scale: [1, 1.3, 1], opacity: [0.5, 1, 0.5] }}
                          transition={{ duration: 1.5, repeat: Infinity, delay: 0.3 }}
                        >
                          <FileCheck className="w-5 h-5 text-blue-500" />
                        </motion.div>
                        <motion.div
                          animate={{ scale: [1, 1.3, 1], opacity: [0.5, 1, 0.5] }}
                          transition={{ duration: 1.5, repeat: Infinity, delay: 0.6 }}
                        >
                          <ShieldCheck className="w-5 h-5 text-blue-500" />
                        </motion.div>
                      </div>

                      {/* Loading bar */}
                      <div className="mt-6 w-64 h-1 bg-zinc-800 rounded-full overflow-hidden">
                        <motion.div
                          className="h-full bg-gradient-to-r from-blue-600 via-blue-400 to-blue-600"
                          animate={{ x: ["-100%", "100%"] }}
                          transition={{ duration: 1.5, repeat: Infinity, ease: "linear" }}
                          style={{ width: "50%" }}
                        />
                      </div>
                    </motion.div>
                  </motion.div>
                )}

                {/* Certification Confirmation Overlay */}
                {isCertified && (
                  <motion.div
                    key="certified"
                    className="absolute inset-0 z-30 flex items-center justify-center pointer-events-none"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.6 }}
                  >
                    <motion.div
                      className="px-8 py-4 rounded-2xl bg-gradient-to-r from-blue-900/70 via-blue-700/60 to-blue-900/70 border-2 border-blue-500/80 shadow-[0_0_30px_rgba(0,191,255,0.6)] backdrop-blur-sm"
                      initial={{ scale: 0.85, y: 20 }}
                      animate={{ 
                        scale: [0.95, 1.05, 1],
                        y: 0
                      }}
                      transition={{ 
                        duration: 1.5, 
                        ease: 'easeOut'
                      }}
                    >
                      <motion.div
                        className="flex items-center space-x-3"
                        animate={{ 
                          opacity: [0.8, 1, 0.8]
                        }}
                        transition={{ 
                          duration: 2, 
                          repeat: Infinity,
                          ease: "easeInOut"
                        }}
                      >
                        <ShieldCheck className="w-7 h-7 text-green-400" strokeWidth={2} />
                        <p className="text-base md:text-lg font-bold tracking-wide sentinel-holo">
                          Document certifié par Sentinel Quantum AI
                        </p>
                        <motion.div
                          animate={{ rotate: 360 }}
                          transition={{ duration: 3, repeat: Infinity, ease: "linear" }}
                        >
                          <FileCheck className="w-6 h-6 text-blue-400" />
                        </motion.div>
                      </motion.div>
                    </motion.div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* PDF Iframe */}
              <motion.iframe
                src={pdfUrl}
                title="Sentinel Quantum Vanguard AI Pro Executive Brief"
                width="100%"
                height="800"
                onLoad={handlePdfLoad}
                className="rounded-2xl border-2 border-blue-700/50 shadow-2xl shadow-blue-500/20"
                initial={{ opacity: 0 }}
                animate={{ opacity: isPdfLoading ? 0.3 : 1 }}
                transition={{ duration: 0.8 }}
              />
            </motion.div>
          )}

          {/* Additional Resources with staggered animations */}
          <motion.div 
            className="grid md:grid-cols-2 gap-6"
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 1.2 }}
          >
            {/* Documentation en ligne */}
            <motion.div 
              className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 hover:border-blue-500/50 transition-all"
              whileHover={{ y: -5, borderColor: "rgba(59, 130, 246, 0.5)" }}
              transition={{ duration: 0.3 }}
            >
              <div className="text-3xl mb-3">📚</div>
              <h3 className="text-xl font-bold text-white mb-2">
                Documentation en ligne
              </h3>
              <p className="text-zinc-400 text-sm mb-4">
                Accédez à la documentation technique complète directement en ligne.
              </p>
              <a
                href="https://github.com/teetee971/SentinelQuantumVanguardAiPro"
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-400 hover:text-blue-300 transition flex items-center space-x-1"
              >
                <span>Voir sur GitHub</span>
                <span>→</span>
              </a>
            </motion.div>

            {/* Status Page */}
            <motion.div 
              className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 hover:border-green-500/50 transition-all"
              whileHover={{ y: -5, borderColor: "rgba(34, 197, 94, 0.5)" }}
              transition={{ duration: 0.3 }}
            >
              <div className="text-3xl mb-3">📊</div>
              <h3 className="text-xl font-bold text-white mb-2">
                Status Dashboard
              </h3>
              <p className="text-zinc-400 text-sm mb-4">
                Consultez le tableau de bord en temps réel du système.
              </p>
              <a
                href="/status.html"
                target="_blank"
                rel="noopener noreferrer"
                className="text-green-400 hover:text-green-300 transition flex items-center space-x-1"
              >
                <span>Voir le status</span>
                <span>→</span>
              </a>
            </motion.div>
          </motion.div>

          {/* Info Box with animation */}
          <motion.div 
            className="mt-8 bg-blue-900/20 border border-blue-700/50 rounded-lg p-6"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 1 }}
          >
            <div className="flex items-start space-x-3">
              <div className="text-2xl">ℹ️</div>
              <div>
                <h4 className="font-bold text-white mb-2">À propos des documents</h4>
                <p className="text-zinc-300 text-sm">
                  Tous les documents officiels de Sentinel Quantum Vanguard AI Pro sont régulièrement mis à jour 
                  pour refléter les dernières évolutions du système. Pour toute question ou demande de documentation 
                  spécifique, consultez notre repository GitHub.
                </p>
              </div>
            </div>
          </motion.div>
        </div>
      </div>

      {/* AI Signature Footer with Real-time Timestamp */}
      <motion.div
        className="fixed bottom-0 left-0 right-0 py-3 bg-gradient-to-t from-black via-zinc-950/95 to-transparent backdrop-blur-sm border-t border-blue-900/20 z-50"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 1.2, delay: 1.5, ease: "easeOut" }}
      >
        <div className="text-center">
          <motion.div
            className="text-xs md:text-sm font-mono tracking-wider sentinel-signature inline-block px-6 py-2"
            animate={{ opacity: [0.7, 1, 0.7] }}
            transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
          >
            <span className="inline-flex items-center space-x-2">
              <ShieldCheck className="w-4 h-4 text-green-400" />
              <span>Sentinel Quantum AI Verified</span>
              <span className="text-blue-400">—</span>
              <span className="text-blue-300">horodaté {timestamp}</span>
            </span>
          </motion.div>
        </div>
      </motion.div>
    </>
  );
}
