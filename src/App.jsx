// src/App.jsx
import React from 'react'

export default function App() {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-black text-cyan-400 text-center">
      <h1 className="text-4xl font-bold mb-4">
        Sentinel Quantum Vanguard AI Pro
      </h1>
      <p className="text-xl text-cyan-300 mb-6">
        “La sécurité du futur, dès aujourd’hui.”
      </p>

      <nav className="flex gap-6 text-cyan-200 text-lg mb-8">
        <a href="#">Modules</a>
        <a href="#">Actualités</a>
        <a href="#">Blog</a>
        <a href="#">Télécharger</a>
      </nav>

      <section className="max-w-xl text-gray-300 leading-relaxed">
        <h2 className="text-2xl font-semibold text-cyan-400 mb-2">
          Modules actifs
        </h2>
        <p>
          IA prédictive, Quantum Shield, Scanner OSINT, pare-feu intelligent,
          et détection d’anomalies en temps réel.
        </p>
      </section>
    </div>
  )
}