import ModuleTemplate from "../../../components/ModuleTemplate";

export default function LieSense() {
  const data = {
    icon: "🔎",
    title: "LieSense Connector",
    subtitle: "Analyse vocale — Détection anomalies et manipulations",
    stats: [
      { value: "247", label: "Analyses vocales", color: "amber" },
      { value: "92%", label: "Précision", color: "green" },
      { value: "23", label: "Anomalies détectées", color: "red" },
      { value: "156", label: "Patterns linguistiques", color: "blue" }
    ],
    subModules: [
      { name: "Voice Analyzer Node", status: "active", metrics: { Analyses: 247, Précision: "92%" }},
      { name: "Linguistic Pattern AI", status: "active", metrics: { Patterns: 156, Appris: "Auto" }},
      { name: "Emotional Deviation Detector", status: "active", metrics: { Détections: 23, Fiabilité: "89%" }},
      { name: "Cognitive Bias Engine", status: "active", metrics: { Biais: 45, Identifiés: "100%" }}
    ],
    keyFeatures: [
      { title: "Analyse sincérité vocale", description: "Détection des micro-variations dans la voix indiquant le stress ou mensonge.", color: "amber" },
      { title: "Stress et incohérence", description: "Identification des contradictions verbales et signaux de manipulation.", color: "red" },
      { title: "Intégration téléphonique", description: "Connecteur avec systèmes de téléphonie pour analyse en temps réel.", color: "blue" },
      { title: "Détection manipulation", description: "Reconnaissance des tentatives d'influence et de chantage vocal.", color: "purple" }
    ],
    benefits: [
      { icon: "🔍", title: "Vérification fiable", description: "Enquêtes, RH, sécurité" },
      { icon: "💼", title: "Anti-fraude", description: "Entretien, télévente" },
      { icon: "⚠️", title: "Détection extorsion", description: "Chantage vocal" },
      { icon: "✅", title: "Transparence renforcée", description: "Interactions sûres" }
    ]
  };

  return <ModuleTemplate {...data} />;
}
