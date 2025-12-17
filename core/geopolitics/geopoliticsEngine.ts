/**
 * Geopolitics Engine - Main Engine
 * 
 * Legal Notice: Offensive Security Simulation – Aucun accès non autorisé – 
 * Usage audit, formation et évaluation uniquement.
 * 
 * OBJECTIF :
 * Analyser le contexte géopolitique et ses corrélations cyber
 * 
 * SOURCES :
 * - Événements OSINT publics uniquement
 * - Pas d'accès à données classifiées
 * - Pas d'interception
 * 
 * MÉTHODE :
 * - Corrélation temporelle événements géopolitiques → activité cyber
 * - Analyse patterns historiques
 * - Contexte régional
 */

import {
  GeopoliticalEvent,
  RegionAnalysis,
  GeopoliticalAnalysisResult,
} from "./types";

/**
 * Impact scoring
 */
const IMPACT_SCORES = {
  low: 33,
  medium: 66,
  high: 100,
};

/**
 * Known cyber-geopolitical correlations
 */
const CORRELATION_PATTERNS = {
  sanctions: "Augmentation activité hacktiviste et groupes APT affiliés",
  conflict: "Recrudescence cyberattaques DDoS et désinformation",
  tension: "Intensification espionnage cyber et reconnaissance",
  regulation: "Adaptation techniques pour contourner nouvelles contraintes",
  cooperation: "Amélioration posture défensive via partage renseignement",
};

/**
 * Geopolitics Engine
 * 
 * Analyse contexte géopolitique et impact cyber
 */
export class GeopoliticsEngine {
  private events: GeopoliticalEvent[] = [];
  
  /**
   * Load geopolitical events
   * @param events - Events from OSINT sources
   */
  loadEvents(events: GeopoliticalEvent[]): void {
    this.events = events.sort((a, b) => 
      new Date(b.date).getTime() - new Date(a.date).getTime()
    );
  }
  
  /**
   * Analyze geopolitical cyber context
   * 
   * @returns Analysis result with timeline and regional breakdown
   */
  analyzeGeopoliticalCyberContext(): GeopoliticalAnalysisResult {
    const timeline = this.events;
    const byRegion = this.analyzeByRegion();
    const summary = this.generateSummary(byRegion);
    
    return {
      timeline,
      byRegion,
      summary,
      generatedAt: new Date().toISOString(),
    };
  }
  
  /**
   * Analyze events by region
   */
  private analyzeByRegion(): RegionAnalysis[] {
    const regions = [...new Set(this.events.map((e) => e.region))];
    
    return regions.map((region) => {
      const regionEvents = this.events.filter((e) => e.region === region);
      const eventCount = regionEvents.length;
      
      // Calculate average impact
      const totalImpact = regionEvents.reduce(
        (sum, e) => sum + IMPACT_SCORES[e.impact],
        0
      );
      const avgImpact = eventCount > 0 ? Math.round(totalImpact / eventCount) : 0;
      
      // Determine trend (simple heuristic: recent vs older events)
      const recent = regionEvents.slice(0, Math.ceil(eventCount / 2));
      const older = regionEvents.slice(Math.ceil(eventCount / 2));
      
      const recentAvg = recent.length > 0
        ? recent.reduce((sum, e) => sum + IMPACT_SCORES[e.impact], 0) / recent.length
        : 0;
      const olderAvg = older.length > 0
        ? older.reduce((sum, e) => sum + IMPACT_SCORES[e.impact], 0) / older.length
        : 0;
      
      let trend: "escalating" | "stable" | "de-escalating" = "stable";
      if (recentAvg > olderAvg * 1.2) trend = "escalating";
      else if (recentAvg < olderAvg * 0.8) trend = "de-escalating";
      
      // Extract main themes
      const mainThemes = this.extractThemes(regionEvents);
      
      // Collect cyber correlations
      const cyberCorrelations = regionEvents.map((e) => e.correlation);
      
      return {
        region,
        eventCount,
        avgImpact,
        trend,
        mainThemes,
        cyberCorrelations: [...new Set(cyberCorrelations)],
      };
    }).sort((a, b) => b.avgImpact - a.avgImpact);
  }
  
  /**
   * Extract themes from events
   */
  private extractThemes(events: GeopoliticalEvent[]): string[] {
    const themes: string[] = [];
    const titles = events.map((e) => e.title.toLowerCase());
    
    // Simple keyword extraction
    const keywords = [
      "sanctions",
      "conflit",
      "tension",
      "régulation",
      "coopération",
      "cyber",
      "espionnage",
      "attaque",
    ];
    
    keywords.forEach((keyword) => {
      if (titles.some((title) => title.includes(keyword))) {
        themes.push(keyword.charAt(0).toUpperCase() + keyword.slice(1));
      }
    });
    
    return themes.slice(0, 3); // Top 3 themes
  }
  
  /**
   * Generate summary
   */
  private generateSummary(byRegion: RegionAnalysis[]): string {
    const highImpactRegions = byRegion.filter((r) => r.avgImpact >= 70);
    const escalating = byRegion.filter((r) => r.trend === "escalating");
    
    let summary = `Analyse de ${this.events.length} événements géopolitiques. `;
    
    if (highImpactRegions.length > 0) {
      summary += `Régions à impact élevé: ${highImpactRegions.map((r) => r.region).join(", ")}. `;
    }
    
    if (escalating.length > 0) {
      summary += `Tensions en escalade: ${escalating.map((r) => r.region).join(", ")}. `;
    }
    
    summary += "Corrélations cyber basées sur patterns historiques OSINT.";
    
    return summary;
  }
}

/**
 * Convenience function for quick analysis
 * 
 * @param events - Geopolitical events
 * @returns Analysis result
 */
export function analyzeGeopoliticalCyberContext(
  events: GeopoliticalEvent[]
): GeopoliticalAnalysisResult {
  const engine = new GeopoliticsEngine();
  engine.loadEvents(events);
  return engine.analyzeGeopoliticalCyberContext();
}
