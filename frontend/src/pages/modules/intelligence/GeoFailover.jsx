import ModuleTemplate from "../../../components/ModuleTemplate";

export default function GeoFailover() {
  const data = {
    icon: "🧭",
    title: "GeoFailover Coordinator",
    subtitle: "Redondance géographique — Continuité inter-régions",
    stats: [
      { value: "8", label: "Régions actives", color: "blue" },
      { value: "< 1s", label: "Temps basculement", color: "green" },
      { value: "100%", label: "Synchronisation", color: "purple" },
      { value: "3", label: "Basculements (24h)", color: "cyan" }
    ],
    subModules: [
      { name: "Geo-Replication Hub", status: "active", metrics: { Régions: 8, Répliqué: "100%" }},
      { name: "Automatic Failover Switch", status: "active", metrics: { Tests: 156, Succès: "100%" }},
      { name: "Traffic Rerouter AI", status: "active", metrics: { Routages: "2.3M", Optimal: "Oui" }},
      { name: "Disaster Recovery Vault", status: "active", metrics: { Backups: 247, Intègres: "100%" }}
    ],
    keyFeatures: [
      { title: "Continuité géographique", description: "Basculement automatique inter-régions sans perte de données.", color: "blue" },
      { title: "Synchronisation temps réel", description: "Réplication instantanée sur tous les nœuds géographiques.", color: "green" },
      { title: "Plan de secours intégré", description: "Disaster recovery automatique avec tests réguliers.", color: "purple" },
      { title: "Récupération transparente", description: "Reprise invisible pour les utilisateurs finaux.", color: "cyan" }
    ],
    benefits: [
      { icon: "🌍", title: "Résilience mondiale", description: "Garantie totale" },
      { icon: "📉", title: "Risque réduit", description: "Opérationnel minimal" },
      { icon: "🕐", title: "Disponibilité 24/7", description: "Sans interruption" },
      { icon: "🎖️", title: "Conformité OTAN/UE", description: "Standards respectés" }
    ]
  };

  return <ModuleTemplate {...data} />;
}
