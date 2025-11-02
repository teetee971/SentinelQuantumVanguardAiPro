import ModuleTemplate from "../../../components/ModuleTemplate";

export default function FireGuard() {
  const data = {
    icon: "🔥",
    title: "FireGuard",
    subtitle: "Protection cloud — Surveillance Firebase, AdonisJS et Railway",
    stats: [
      { value: "2,847", label: "Services surveillés", color: "orange" },
      { value: "45", label: "Anomalies bloquées", color: "red" },
      { value: "100%", label: "Intégrité garantie", color: "green" },
      { value: "0", label: "Fuites détectées", color: "blue" }
    ],
    subModules: [
      { name: "CloudWatch Sentinel", status: "active", metrics: { Moniteurs: 247, Alertes: 12 }},
      { name: "Function Integrity Checker", status: "active", metrics: { Vérifications: 892, Erreurs: 0 }},
      { name: "Database Guardian", status: "active", metrics: { Requêtes: "1.2M", Suspectes: 3 }},
      { name: "Cloud Shield AI", status: "active", metrics: { Protections: 156, Blocs: 45 }}
    ],
    recentActivity: [
      { title: "Accès suspect bloqué", description: "Tentative d'accès non autorisé à Firebase", time: "Il y a 15m" },
      { title: "Intégrité vérifiée", description: "Toutes les fonctions backend validées", time: "Il y a 1h" },
      { title: "Alerte résolu", description: "Requête anormale isolée et analysée", time: "Il y a 3h" }
    ],
    keyFeatures: [
      {
        title: "Surveillance microservices",
        description: "Monitoring continu de tous les services cloud avec détection d'anomalies comportementales en temps réel.",
        color: "orange"
      },
      {
        title: "Détection comportements anormaux",
        description: "Analyse IA des patterns d'utilisation pour identifier les activités suspectes et tentatives d'intrusion.",
        color: "red"
      },
      {
        title: "Alerte instantanée IA",
        description: "Notification immédiate avec classification automatique de la sévérité et actions recommandées.",
        color: "yellow"
      },
      {
        title: "Blocage automatique",
        description: "Isolation instantanée des accès non autorisés sans impact sur les services légitimes.",
        color: "purple"
      }
    ],
    benefits: [
      { icon: "☁️", title: "Protection backend", description: "Constante 24/7" },
      { icon: "🛡️", title: "Anti-exploitation", description: "Cloud sécurisé" },
      { icon: "💾", title: "Intégrité BDD", description: "Garantie totale" },
      { icon: "🔐", title: "Zéro compromission", description: "Risques minimisés" }
    ]
  };

  return <ModuleTemplate {...data} />;
}
