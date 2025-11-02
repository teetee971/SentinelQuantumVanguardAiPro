import ModuleTemplate from "../../../components/ModuleTemplate";

export default function PegasusHunter() {
  const data = {
    icon: "🧠",
    title: "PegasusHunter",
    subtitle: "Chasseur comportemental — Pegasus, FinFisher et équivalents",
    stats: [
      { value: "892", label: "Patterns suivis", color: "red" },
      { value: "0", label: "Infections actives", color: "green" },
      { value: "156", label: "Corrélations", color: "purple" },
      { value: "12", label: "Suppressions (24h)", color: "orange" }
    ],
    subModules: [
      { name: "Behavior Pattern Analyzer", status: "active", metrics: { Patterns: 892, Précision: "97%" }},
      { name: "Infection Correlator", status: "active", metrics: { Corrélations: 156, Détections: 12 }},
      { name: "Rootkit Detector", status: "active", metrics: { Scans: "1K/h", Trouvés: 0 }},
      { name: "Communication Tracker", status: "active", metrics: { Flux: "2.3M", Suspects: 3 }}
    ],
    keyFeatures: [
      { title: "Surveillance réseau IA", description: "Monitoring continu du trafic avec détection d'empreintes comportementales.", color: "red" },
      { title: "Empreintes comportementales", description: "Identification des patterns de communication typiques des spyware.", color: "orange" },
      { title: "Journal d'incidents IA", description: "Documentation complète pour expertise judiciaire et forensique.", color: "purple" },
      { title: "Suppression automatisée", description: "Élimination des menaces sans redémarrage ni interruption.", color: "green" }
    ],
    benefits: [
      { icon: "👻", title: "Détection invisible", description: "Arrière-plan discret" },
      { icon: "🏢", title: "Protection entreprise", description: "Proactive continue" },
      { icon: "🔧", title: "Neutralisation sans reboot", description: "IA autonome" },
      { icon: "⚖️", title: "Audit judiciaire", description: "Exportable certifié" }
    ]
  };

  return <ModuleTemplate {...data} />;
}
