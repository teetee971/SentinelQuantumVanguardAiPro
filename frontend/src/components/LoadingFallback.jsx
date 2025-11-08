export default function LoadingFallback() {
  return (
    <div className="min-h-screen bg-black flex items-center justify-center">
      <div className="text-cyan-400 animate-pulse">Chargement...</div>
    </div>
  );
}
