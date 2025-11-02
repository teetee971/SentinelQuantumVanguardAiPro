import ModuleTemplate from "../../../components/ModuleTemplate";

export default function PegasusScan() {
  const data = {
    icon: "🧬",
    title: "Pegasus Scan IA",
    subtitle: "Détection spyware — Pegasus, malware et comportements suspects",
    stats: [
      { value: "1,584", label: "Terminaux scannés", color: "blue" },
      { value: "0", label: "Infections actives", color: "green" },
      { value: "23", label: "Menaces isolées", color: "orange" },
      { value: "100%", label: "Protection", color: "purple" }
    ],
    subModules: [
      { name: "Device Integrity Scanner", status: "active", metrics: { Scans: 1584, Intègres: "100%" }},
      { name: "Pegasus Signature Detector", status: "active", metrics: { Signatures: 2341, Détections: 23 }},
      { name: "Memory Trace Analyzer", status: "active", metrics: { Analyses: "10K/h", Anomalies: 0 }},
      { name: "IA Threat Correlator", status: "active", metrics: { Corrélations: 156, Précision: "98%" }}
    ],
    keyFeatures: [
      { title: "Scan IA local et cloud", description: "Analyse complète des terminaux avec détection comportementale avancée.", color: "blue" },
      { title: "Détection comportementale", description: "Identification des patterns typiques de Pegasus et spyware similaires.", color: "orange" },
      { title: "Analyse mémoire", description: "Inspection profonde des processus et fichiers chiffrés en mémoire.", color: "purple" },
      { title: "Isolation automatique", description: "Quarantaine immédiate des modules infectés sans perte de données.", color: "green" }
    ],
    benefits: [
      { icon: "📱", title: "Protection mobile/PC", description: "Tous appareils" },
      { icon: "🎖️", title: "Conformité renseignement", description: "Standards respectés" },
      { icon: "🔍", title: "Analyse non-intrusive", description: "Certifiée" },
      { icon: "🛡️", title: "Confiance restaurée", description: "Numérique sécurisé" }
    ]
  };

  return <ModuleTemplate {...data} />;
}
