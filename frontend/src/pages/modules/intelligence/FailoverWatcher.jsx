import ModuleTemplate from "../../../components/ModuleTemplate";

export default function FailoverWatcher() {
  const data = {
    icon: "🌐",
    title: "Global Failover Watcher",
    subtitle: "Surveillance mondiale — Disponibilité 24/7 et reprise auto",
    stats: [
      { value: "100%", label: "Disponibilité", color: "green" },
      { value: "0", label: "Pannes actives", color: "blue" },
      { value: "8", label: "Régions surveillées", color: "purple" },
      { value: "< 500ms", label: "Temps détection", color: "cyan" }
    ],
    subModules: [
      { name: "Uptime Sensor AI", status: "active", metrics: { Checks: "100/s", Disponibilité: "100%" }},
      { name: "Failover Trigger Node", status: "active", metrics: { Prêts: 8, Tests: "156/j" }},
      { name: "Availability Reporter", status: "active", metrics: { Rapports: 247, SLA: "99.99%" }},
      { name: "Auto-Recovery Agent", status: "active", metrics: { Récupérations: 3, Succès: "100%" }}
    ],
    keyFeatures: [
      { title: "Supervision 24/7", description: "Monitoring continu de tous les points de présence mondiale.", color: "green" },
      { title: "Activation automatique", description: "Basculement instantané en cas de détection d'indisponibilité.", color: "blue" },
      { title: "Statistiques par région", description: "Métriques détaillées de disponibilité géographique en temps réel.", color: "purple" },
      { title: "Auto-relance", description: "Redémarrage automatique des instances défaillantes avec vérification.", color: "cyan" }
    ],
    benefits: [
      { icon: "🌍", title: "Accessibilité continue", description: "Toujours disponible" },
      { icon: "📉", title: "Risque minimisé", description: "Panne globale évitée" },
      { icon: "⚡", title: "Reprise instantanée", description: "Sans délai" },
      { icon: "🎖️", title: "Fiabilité prouvée", description: "Infrastructures critiques" }
    ]
  };

  return <ModuleTemplate {...data} />;
}
