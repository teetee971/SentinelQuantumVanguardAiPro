export default function Layout({ children }) {
  return (
    <div className="min-h-screen w-full bg-sentinel-bg text-white relative cyber-grid">
      
      {/* SCAN OVERLAY */}
      <div className="absolute inset-0 cyber-scan pointer-events-none"></div>

      {/* HEADER */}
      <header className="w-full py-5 px-6 bg-sentinel-panel border-b border-sentinel-primary shadow-neonSoft">
        <h1 className="text-2xl font-bold cyber-glow tracking-widest">
          SENTINEL QUANTUM VANGUARD AI PRO
        </h1>
      </header>

      {/* MAIN */}
      <main className="relative z-10 p-6">
        {children}
      </main>

      {/* FOOTER */}
      <footer className="w-full py-4 text-center text-sm opacity-60 bg-[#020409] border-t border-sentinel-primary">
        Sentinel Elite Ops System — Quantum Defense Network v1.0
      </footer>
    </div>
  );
}