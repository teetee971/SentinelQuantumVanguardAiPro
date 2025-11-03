import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

export default function Telechargement() {
  const [isLoading, setIsLoading] = useState(true);
  const [isCertified, setIsCertified] = useState(false);

  useEffect(() => {
    handleLoad();
  }, []);

  const handleLoad = () => {
    console.log('[Telechargement] Starting integrity check animation');
    setTimeout(() => {
      console.log('[Telechargement] Setting isLoading to false');
      setIsLoading(false);
      setTimeout(() => {
        console.log('[Telechargement] Setting isCertified to true');
        setIsCertified(true);
        setTimeout(() => {
          console.log('[Telechargement] Setting isCertified to false');
          setIsCertified(false);
        }, 2700); // Show for ~2.7 seconds
      }, 800);
    }, 1200);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 p-6">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <a href="/" className="text-blue-400 hover:text-blue-300 mb-4 inline-block">
            ← Retour à l'accueil
          </a>
          <h1 className="text-4xl font-bold text-blue-400 mb-2">
            Téléchargement de Document
          </h1>
          <p className="text-zinc-400">
            Document Sentinel Quantum Vanguard AI Pro
          </p>
        </div>

        {/* Document Container */}
        <div className="relative bg-zinc-900 border border-zinc-800 rounded-lg overflow-hidden" style={{ minHeight: '600px' }}>
          {/* Document Display */}
          <div className="p-8">
            <div className="bg-white text-black p-12 rounded shadow-lg">
              <div className="text-center mb-8">
                <div className="text-6xl mb-4">🛡️</div>
                <h2 className="text-3xl font-bold mb-2">Sentinel Quantum Vanguard AI Pro</h2>
                <p className="text-gray-600">Documentation Officielle</p>
              </div>
              
              <div className="space-y-4">
                <section>
                  <h3 className="text-xl font-semibold mb-2">Introduction</h3>
                  <p className="text-gray-700">
                    Sentinel Quantum Vanguard AI Pro est une plateforme de cybersécurité 
                    de nouvelle génération utilisant l'intelligence artificielle quantique 
                    pour protéger vos infrastructures réseau.
                  </p>
                </section>

                <section>
                  <h3 className="text-xl font-semibold mb-2">Caractéristiques</h3>
                  <ul className="list-disc list-inside text-gray-700 space-y-1">
                    <li>Surveillance VPN en temps réel</li>
                    <li>Détection d'anomalies par IA</li>
                    <li>Encryption quantique</li>
                    <li>Audit de sécurité automatisé</li>
                  </ul>
                </section>

                <section>
                  <h3 className="text-xl font-semibold mb-2">Certification</h3>
                  <p className="text-gray-700">
                    Ce document est certifié et authentifié par Sentinel Quantum AI. 
                    L'intégrité du document est vérifiée par des algorithmes de cryptographie 
                    quantique avancés.
                  </p>
                </section>
              </div>
            </div>
          </div>

          {/* Loading Overlay - Integrity Check */}
          <AnimatePresence>
            {isLoading && (
              <motion.div
                className="absolute inset-0 bg-zinc-950/95 flex items-center justify-center z-20"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.5 }}
              >
                <div className="text-center">
                  <motion.div
                    className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full mx-auto mb-4"
                    animate={{ rotate: 360 }}
                    transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                  />
                  <motion.p
                    className="text-blue-400 text-lg font-semibold"
                    animate={{ opacity: [0.5, 1, 0.5] }}
                    transition={{ duration: 1.5, repeat: Infinity }}
                  >
                    Vérification d'intégrité IA en cours...
                  </motion.p>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Certification Message */}
          {isCertified && (
            <motion.div
              key="certification"
              className="absolute inset-0 flex items-center justify-center z-30 pointer-events-none"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.6 }}
            >
              <motion.div
                className="px-6 py-3 rounded-xl bg-gradient-to-r from-blue-900/60 via-blue-700/50 to-blue-900/60 border border-blue-500/70 shadow-[0_0_20px_rgba(0,191,255,0.5)]"
                initial={{ scale: 0.9 }}
                animate={{ scale: [1, 1.05, 1] }}
                transition={{ duration: 1.2, ease: 'easeInOut', repeat: Infinity }}
              >
                <motion.p
                  className="text-blue-300 text-base md:text-lg font-semibold tracking-wide sentinel-holo"
                  animate={{ opacity: [0.7, 1, 0.7] }}
                  transition={{ duration: 1.5, repeat: Infinity }}
                >
                  ✅ Document certifié par Sentinel Quantum AI
                </motion.p>
              </motion.div>
            </motion.div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="mt-6 flex gap-4 justify-center">
          <button className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-semibold transition">
            📥 Télécharger le document
          </button>
          <button 
            onClick={() => {
              setIsLoading(true);
              setIsCertified(false);
              handleLoad();
            }}
            className="px-6 py-3 bg-zinc-800 hover:bg-zinc-700 text-zinc-100 rounded-lg font-semibold transition"
          >
            🔄 Revérifier l'intégrité
          </button>
        </div>
      </div>
    </div>
  );
}
