import React from 'react'
import Navbar from './components/Navbar'

export default function App() {
  return (
    <div className="relative flex flex-col items-center justify-center min-h-screen overflow-hidden text-cyan-300">
      <div className="halo"></div>
      <div className="halo" style={{ top: '30%', left: '60%' }}></div>

      <div className="glass p-10 text-center max-w-2xl z-10">
        <h1 className="text-4xl md:text-5xl font-bold mb-4 text-cyan-400 drop-shadow-lg">
          Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-lg md:text-xl text-cyan-200 mb-6 italic">
          “La sécurité du futur, dès aujourd’hui.”
        </p>

        <Navbar />

        <section className="mt-8 text-gray-300">
          <h2 className="text-2xl font-semibold text-cyan-400 mb-3">
            Modules actifs
          </h2>
          <p>
            IA prédictive, Quantum Shield, Scanner OSINT, pare-feu intelligent,
            détection d’anomalies en temps réel et supervision autonome.
          </p>
        </section>
      </div>
    </div>
  )
}