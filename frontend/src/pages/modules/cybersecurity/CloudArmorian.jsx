import ModuleTemplate from "../../../components/ModuleTemplate";

export default function CloudArmorian() {
  const data = {
    icon: "🧱",
    title: "CloudArmorian",
    subtitle: "Bouclier IA — Anti-DDoS et anti-injection auto-apprenant",
    stats: [
      { value: "847K", label: "Attaques bloquées", color: "red" },
      { value: "< 50ms", label: "Temps de réponse", color: "green" },
      { value: "98.7%", label: "Précision IA", color: "purple" },
      { value: "12,456", label: "Règles adaptatives", color: "blue" }
    ],
    subModules: [
      { name: "DDoS Absorber Node", status: "active", metrics: { Absorbé: "847K", Capacité: "10Tbps" }},
      { name: "Intrusion Behavior Learner", status: "active", metrics: { Patterns: 2341, Précision: "98.7%" }},
      { name: "Adaptive Filter AI", status: "active", metrics: { Règles: 12456, Auto: "Oui" }},
      { name: "Quantum Shield Layer", status: "active", metrics: { Protection: "Militaire", Niveau: "10" }}
    ],
    recentActivity: [
      { title: "DDoS masive bloqué", description: "Attaque de 5.2Tbps neutralisée en 38ms", time: "Il y a 3h" },
      { title: "Nouveau pattern appris", description: "IA a identifié nouvelle technique d'injection", time: "Il y a 6h" },
      { title: "Filtres mis à jour", description: "247 règles adaptatives ajoutées automatiquement", time: "Il y a 12h" }
    ],
    keyFeatures: [
      {
        title: "Blocage intelligent",
        description: "Analyse en temps réel des patterns d'attaque avec distinction automatique du trafic légitime et malveillant.",
        color: "red"
      },
      {
        title: "Réponse < 50ms",
        description: "Détection et neutralisation ultra-rapide des menaces avant qu'elles n'impactent les services.",
        color: "green"
      },
      {
        title: "Analyse comportementale",
        description: "Apprentissage continu des nouvelles techniques d'attaque pour amélioration constante de la défense.",
        color: "purple"
      },
      {
        title: "Auto-amélioration ML",
        description: "Système d'apprentissage machine qui renforce automatiquement ses capacités après chaque attaque.",
        color: "blue"
      }
    ],
    benefits: [
      { icon: "💪", title: "Résilience totale", description: "Face aux attaques massives" },
      { icon: "⚙️", title: "Fiabilité éprouvée", description: "Environnements critiques" },
      { icon: "🔄", title: "Auto-renforcement", description: "Amélioration continue" },
      { icon: "🎖️", title: "Standard militaire", description: "Protection conforme" }
    ]
  };

  return <ModuleTemplate {...data} />;
}
