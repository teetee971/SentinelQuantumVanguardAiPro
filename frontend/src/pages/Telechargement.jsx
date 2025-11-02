import React from "react";

export default function Telechargement() {
  return (
    <section className="min-h-screen bg-black text-white flex flex-col items-center justify-center p-8">
      <div className="max-w-5xl w-full text-center">
        <h1 className="text-3xl md:text-4xl font-bold text-blue-400 mb-6">
          📘 Fiche officielle Sentinel Quantum Vanguard AI Pro
        </h1>

        <p className="text-gray-300 mb-4">
          Découvrez la présentation complète bilingue (FR / EN) de Sentinel Quantum Vanguard AI Pro.
        </p>

        {/* Aperçu intégré du PDF */}
        <iframe
          src="/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf"
          title="Sentinel Quantum Vanguard AI Pro Executive Brief"
          width="100%"
          height="800"
          className="rounded-2xl border border-blue-700 shadow-lg"
        ></iframe>

        {/* Bouton de téléchargement */}
        <div className="mt-6">
          <a
            href="/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-block px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl shadow-md transition-all"
          >
            ⬇️ Télécharger la fiche officielle (PDF FR / EN)
          </a>
        </div>

        {/* Pied de page léger */}
        <p className="text-sm text-gray-500 mt-8">
          © 2025 Sentinel Quantum Vanguard AI Pro — Tous droits réservés
        </p>
      </div>
    </section>
  );
}
