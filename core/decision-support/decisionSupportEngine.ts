/**
 * Decision Support Engine - Main Engine
 * 
 * Legal Notice: Offensive Security Simulation – Aucun accès non autorisé – 
 * Usage audit, formation et évaluation uniquement.
 * 
 * OBJECTIF :
 * Générer des recommandations stratégiques de cybersécurité basées sur :
 * - Analyse des risques identifiés
 * - Bonnes pratiques ANSSI / NIST / ISO 27001
 * - Contraintes organisationnelles (budget, souveraineté)
 * 
 * LANGAGE :
 * - Terminologie RSSI / CERT / ANSSI
 * - Actions défensives concrètes
 * - Pas de jargon marketing
 */

import {
  Recommendation,
  DecisionContext,
  DecisionSupportResult,
  Priority,
  Category,
} from "./types";

/**
 * Recommendation templates based on best practices
 */
const RECOMMENDATION_TEMPLATES = {
  highRiskSector: {
    finance: {
      id: "REC-FIN-001",
      priority: "Critical" as Priority,
      category: "Detection" as Category,
      title: "Renforcer la surveillance des transactions et accès privilégiés",
      rationale: "Secteur financier sous pression - augmentation des attaques ciblées",
      actions: [
        "Déployer solution SIEM avec cas d'usage spécifiques secteur financier",
        "Surveillance temps réel des accès administrateurs et comptes de service",
        "Corrélation événements métier (transactions) et sécurité",
      ],
      effort: "Medium" as const,
      impact: "High" as const,
      timeline: "60 jours",
      frameworks: ["ANSSI", "NIST CSF", "PCI-DSS"],
    },
    government: {
      id: "REC-GOV-001",
      priority: "Critical" as Priority,
      category: "Prevention" as Category,
      title: "Durcissement infrastructure et segmentation réseau",
      rationale: "Ciblage accru des institutions - APT et espionnage",
      actions: [
        "Segmentation réseau stricte (zones sensibles isolées)",
        "Déploiement solutions souveraines (conformité ANSSI)",
        "Audit sécurité trimestriel par prestataire qualifié",
      ],
      effort: "High" as const,
      impact: "High" as const,
      timeline: "90 jours",
      frameworks: ["ANSSI", "RGS", "ISO 27001"],
    },
    healthcare: {
      id: "REC-HEA-001",
      priority: "High" as Priority,
      category: "Response" as Category,
      title: "Plan de continuité et sauvegarde données patients",
      rationale: "Risque ransomware élevé - impact critique sur soins",
      actions: [
        "Sauvegarde offline quotidienne (3-2-1 rule)",
        "Exercice de restauration mensuel",
        "Plan de continuité métier (mode dégradé documenté)",
      ],
      effort: "Medium" as const,
      impact: "High" as const,
      timeline: "45 jours",
      frameworks: ["ANSSI", "HDS", "ISO 27001"],
    },
    energy: {
      id: "REC-ENE-001",
      priority: "Critical" as Priority,
      category: "Detection" as Category,
      title: "Surveillance OT/ICS et détection d'anomalies",
      rationale: "Infrastructure critique - risque sabotage et perturbation",
      actions: [
        "Déploiement IDS/IPS spécifique OT (Modbus, DNP3, IEC 104)",
        "Baseline des communications industrielles",
        "Corrélation IT/OT pour détection latéral movement",
      ],
      effort: "High" as const,
      impact: "High" as const,
      timeline: "120 jours",
      frameworks: ["ANSSI", "IEC 62443", "NIST CSF"],
    },
  },
  
  risingTrend: {
    id: "REC-TREND-001",
    priority: "High" as Priority,
    category: "Prevention" as Category,
    title: "Adaptation stratégie défensive face à tendance à la hausse",
    rationale: "Augmentation significative des incidents détectée",
    actions: [
      "Revue et mise à jour threat model",
      "Formation équipes SOC sur nouvelles techniques adverses",
      "Renforcement détection (nouvelles règles SIEM/EDR)",
    ],
    effort: "Medium" as const,
    impact: "Medium" as const,
    timeline: "30 jours",
    frameworks: ["MITRE ATT&CK", "ANSSI"],
  },
  
  baseline: [
    {
      id: "REC-BASE-001",
      priority: "Medium" as Priority,
      category: "Prevention" as Category,
      title: "Formation continue équipes sur phishing et ingénierie sociale",
      rationale: "Vecteur d'attaque initial le plus fréquent (>70% des intrusions)",
      actions: [
        "Campagnes simulation phishing mensuelles",
        "Formation ciblée cadres dirigeants (spearphishing)",
        "Mise à jour filtres anti-phishing et SPF/DMARC",
      ],
      effort: "Low" as const,
      impact: "Medium" as const,
      timeline: "30 jours",
      frameworks: ["ANSSI", "NIST"],
    },
    {
      id: "REC-BASE-002",
      priority: "Medium" as Priority,
      category: "Governance" as Category,
      title: "Audit et gestion des privilèges administrateurs",
      rationale: "Réduction surface d'attaque et conformité (moindre privilège)",
      actions: [
        "Inventaire complet comptes privilégiés",
        "Révocation accès non justifiés",
        "Déploiement PAM (Privileged Access Management)",
      ],
      effort: "Medium" as const,
      impact: "High" as const,
      timeline: "60 jours",
      frameworks: ["ANSSI", "ISO 27001"],
    },
    {
      id: "REC-BASE-003",
      priority: "Low" as Priority,
      category: "Recovery" as Category,
      title: "Tests réguliers du plan de reprise d'activité (PRA)",
      rationale: "Assurer résilience organisationnelle en cas d'incident majeur",
      actions: [
        "Exercice PRA semestriel (simulation crise cyber)",
        "Validation temps de restauration (RTO/RPO)",
        "Mise à jour documentation (runbooks, contacts)",
      ],
      effort: "Low" as const,
      impact: "Medium" as const,
      timeline: "90 jours",
      frameworks: ["ANSSI", "ISO 22301"],
    },
  ],
};

/**
 * Decision Support Engine
 * 
 * Génère des recommandations stratégiques basées sur contexte cyber
 */
export class DecisionSupportEngine {
  /**
   * Generate recommendations based on context
   * 
   * @param context - Decision context (risks, constraints, posture)
   * @returns Recommendations prioritized and actionable
   */
  generateRecommendations(context: DecisionContext): DecisionSupportResult {
    const recommendations: Recommendation[] = [];
    
    // Recommendations based on top risks
    context.topRisks.forEach((risk) => {
      if (risk.score >= 70) {
        const sectorKey = risk.sector.toLowerCase() as keyof typeof RECOMMENDATION_TEMPLATES.highRiskSector;
        const template = RECOMMENDATION_TEMPLATES.highRiskSector[sectorKey];
        
        if (template) {
          recommendations.push({
            ...template,
            rationale: `${template.rationale} (Score: ${risk.score}/100)`,
          });
        }
      }
    });
    
    // Rising trend recommendation
    const risingTrends = context.topRisks.filter((r) => r.score >= 60);
    if (risingTrends.length > 0) {
      recommendations.push(RECOMMENDATION_TEMPLATES.risingTrend);
    }
    
    // Baseline recommendations (always relevant)
    recommendations.push(...RECOMMENDATION_TEMPLATES.baseline);
    
    // Filter by constraints (if any)
    let filteredRecs = recommendations;
    
    if (context.constraints?.sovereignty) {
      // Prioritize sovereign solutions
      filteredRecs = filteredRecs.map((rec) => {
        if (rec.frameworks?.includes("ANSSI")) {
          return { ...rec, priority: this.promotePriority(rec.priority) };
        }
        return rec;
      });
    }
    
    if (context.constraints?.budget === "low") {
      // Prioritize low-effort recommendations
      filteredRecs = filteredRecs.filter((rec) => rec.effort !== "High").slice(0, 5);
    }
    
    // Sort by priority
    const priorityOrder: Record<Priority, number> = {
      Critical: 0,
      High: 1,
      Medium: 2,
      Low: 3,
    };
    
    filteredRecs.sort((a, b) => priorityOrder[a.priority] - priorityOrder[b.priority]);
    
    // Calculate summary
    const priorityBreakdown = this.calculatePriorityBreakdown(filteredRecs);
    const summary = this.generateSummary(filteredRecs, context);
    
    return {
      recommendations: filteredRecs,
      summary,
      priorityBreakdown,
      generatedAt: new Date().toISOString(),
    };
  }
  
  /**
   * Promote priority level (for sovereignty bonus)
   */
  private promotePriority(priority: Priority): Priority {
    if (priority === "Low") return "Medium";
    if (priority === "Medium") return "High";
    if (priority === "High") return "Critical";
    return priority;
  }
  
  /**
   * Calculate priority breakdown
   */
  private calculatePriorityBreakdown(recommendations: Recommendation[]): Record<Priority, number> {
    const breakdown: Record<Priority, number> = {
      Critical: 0,
      High: 0,
      Medium: 0,
      Low: 0,
    };
    
    recommendations.forEach((rec) => {
      breakdown[rec.priority]++;
    });
    
    return breakdown;
  }
  
  /**
   * Generate summary text
   */
  private generateSummary(recommendations: Recommendation[], context: DecisionContext): string {
    const criticalCount = recommendations.filter((r) => r.priority === "Critical").length;
    const highCount = recommendations.filter((r) => r.priority === "High").length;
    
    let summary = `${recommendations.length} recommandations générées. `;
    
    if (criticalCount > 0) {
      summary += `${criticalCount} critiques nécessitant action immédiate. `;
    }
    
    if (highCount > 0) {
      summary += `${highCount} prioritaires pour les 30-60 prochains jours. `;
    }
    
    if (context.constraints?.sovereignty) {
      summary += "Priorité donnée aux solutions souveraines (ANSSI). ";
    }
    
    return summary.trim();
  }
}

/**
 * Convenience function for quick recommendation generation
 * 
 * @param context - Decision context
 * @returns Recommendations
 */
export function generateRecommendations(context: DecisionContext): DecisionSupportResult {
  const engine = new DecisionSupportEngine();
  return engine.generateRecommendations(context);
}
