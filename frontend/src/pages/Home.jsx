import CyberCard from "../components/CyberCard";

export default function Home() {
  return (
    <div className="grid md:grid-cols-3 gap-6 mt-10">
      <CyberCard title="Scan de Menaces" description="Analyse IA instantanée de votre sécurité." />
      <CyberCard title="Défense DDoS" description="Système d’atténuation des attaques réseau." />
      <CyberCard title="Surveillance Quantum" description="Agents IA actifs 24/7." />
    </div>
  );
}