import React from "react";
import { QRCodeCanvas } from "qrcode.react";

export default function Telechargement() {
  const pdfUrl = "/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf";

  return (
    <section className="min-h-screen bg-black text-white flex flex-col items-center justify-center p-8">
      <div className="max-w-5xl w-full text-center">
        <h1 className="text-3xl md:text-4xl font-bold text-blue-400 mb-6">
          📘 Fiche officielle Sentinel Quantum Vanguard AI Pro
        </h1>

        <p className="text-gray-300 mb-4">
          Présentation complète bilingue (FR / EN) de Sentinel Quantum Vanguard AI Pro.
        </p>

        {/* Aperçu intégré du PDF */}
        <iframe
          src={pdfUrl}
          title="Sentinel Quantum Vanguard AI Pro Executive Brief"
          width="100%"
          height="800"
          className="rounded-2xl border border-blue-700 shadow-lg"
        ></iframe>

        {/* Zone bouton + QR */}
        <div className="mt-6 flex flex-col md:flex-row items-center justify-center gap-6">
          {/* Bouton de téléchargement */}
          <a
            href={pdfUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl shadow-md transition-all"
          >
            ⬇️ Télécharger la fiche officielle (PDF FR / EN)
          </a>

          {/* QR Code officiel */}
          <div className="p-4 bg-blue-950/40 border border-blue-700 rounded-2xl shadow-lg">
            <QRCodeCanvas
              value={`https://sentinelquantumvanguardaipro.pages.dev${pdfUrl}`}
              size={140}
              bgColor="#000000"
              fgColor="#00BFFF"
              level="H"
              includeMargin={true}
              imageSettings={{
                src: "/assets/logo-sentinel-color.png",
                x: undefined,
                y: undefined,
                height: 28,
                width: 28,
                excavate: true,
              }}
            />
            <p className="text-xs text-gray-400 mt-2">
              Scannez pour télécharger sur mobile
            </p>
          </div>
        </div>

        {/* Pied de page */}
        <p className="text-sm text-gray-500 mt-8">
          © 2025 Sentinel Quantum Vanguard AI Pro — Tous droits réservés
        </p>
      </div>
    </section>
  );
}
