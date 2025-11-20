export default function CyberCard({ title, description }) {
  return (
    <div className="bg-sentinel-panel p-6 rounded-xl shadow-neonSoft border border-sentinel-primary hover:shadow-neon transition-all cursor-pointer">
      <h2 className="text-xl cyber-glow mb-2">{title}</h2>
      <p className="text-gray-300">{description}</p>
    </div>
  );
}