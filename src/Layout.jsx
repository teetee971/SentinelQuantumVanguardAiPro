import React from 'react';

export default function Layout({ children }) {
  return (
    <div className="min-h-screen bg-black text-white p-6 flex flex-col items-center justify-center">
      <header className="mb-8">
        <h1 className="text-3xl font-bold mb-6 text-lime-400 text-center">
          Sentinel Quantum Vanguard AI Pro
        </h1>
        <p className="text-center text-gray-400 text-sm">
          "La sécurité du futur, dès aujourd'hui"
        </p>
      </header>
      
      <main className="w-full max-w-4xl flex-1 flex flex-col items-center justify-center">
        {children}
      </main>
      
      <footer className="mt-8 text-center text-gray-500 text-xs">
        <p>© 2024 Sentinel Quantum Vanguard AI Pro - Système de sécurité avancé</p>
      </footer>
    </div>
  );
}