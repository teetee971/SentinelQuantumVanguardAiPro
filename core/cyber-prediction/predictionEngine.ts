/**
 * Cyber Prediction Engine - Main Engine
 * 
 * Legal Notice: Offensive Security Simulation – Aucun accès non autorisé – 
 * Usage audit, formation et évaluation uniquement.
 * 
 * MÉTHODOLOGIE :
 * - Analyse historique des incidents (fréquence, gravité, récence)
 * - Modèles statistiques simples et transparents
 * - Prédiction basée sur tendances observables
 * - AUCUNE garantie - outil d'aide à la décision uniquement
 * 
 * LIMITATIONS :
 * - Pas de prédiction d'événements futurs avec certitude
 * - Basé sur patterns historiques uniquement
 * - Nécessite données de qualité en entrée
 */

import {
  Incident,
  PredictionResult,
  SectorPrediction,
  RegionPrediction,
} from "./types";
import {
  simpleTrendScore,
  calculateTrendForSubset,
  generateRiskNotes,
} from "./predictionModels";

/**
 * Known sectors for analysis
 */
const KNOWN_SECTORS = [
  "Finance",
  "Government",
  "Healthcare",
  "Energy",
  "Technology",
  "Manufacturing",
  "Retail",
  "Education",
  "Telecommunications",
  "Transportation",
];

/**
 * Known regions for analysis
 */
const KNOWN_REGIONS = ["EU", "NA", "APAC", "AF", "SA", "ME"];

/**
 * Cyber Prediction Engine
 * 
 * Provides trend-based risk predictions for cyber threats across sectors and regions.
 * 
 * USAGE :
 * ```typescript
 * const engine = new CyberPredictionEngine();
 * engine.loadIncidents(historicalData);
 * const prediction = engine.predict(30); // 30-day horizon
 * ```
 */
export class CyberPredictionEngine {
  private incidents: Incident[] = [];
  
  /**
   * Load incident data
   * @param incidents - Historical incidents (from JSON or API)
   */
  loadIncidents(incidents: Incident[]): void {
    this.incidents = incidents;
  }
  
  /**
   * Predict cyber risk for a given horizon
   * 
   * @param horizonDays - Prediction horizon (7, 30, or 90 days recommended)
   * @returns Prediction results with scores, trends, and notes
   */
  predict(horizonDays: number): PredictionResult {
    const bySector = this.predictBySector(horizonDays);
    const byRegion = this.predictByRegion(horizonDays);
    const notes = generateRiskNotes(bySector, byRegion);
    
    return {
      horizonDays,
      bySector,
      byRegion,
      notes,
      generatedAt: new Date().toISOString(),
    };
  }
  
  /**
   * Predict risk by sector
   */
  private predictBySector(horizonDays: number): SectorPrediction[] {
    return KNOWN_SECTORS.map((sector) => {
      const filterFn = (inc: Incident) => inc.sector === sector;
      const riskScore = simpleTrendScore(this.incidents, horizonDays, filterFn);
      const trend = calculateTrendForSubset(this.incidents, horizonDays, filterFn);
      const incidentCount = this.incidents.filter(filterFn).length;
      
      return {
        sector,
        riskScore: Math.round(riskScore),
        trend,
        incidentCount,
      };
    }).sort((a, b) => b.riskScore - a.riskScore);
  }
  
  /**
   * Predict risk by region
   */
  private predictByRegion(horizonDays: number): RegionPrediction[] {
    return KNOWN_REGIONS.map((region) => {
      const filterFn = (inc: Incident) => inc.region === region;
      const riskScore = simpleTrendScore(this.incidents, horizonDays, filterFn);
      const trend = calculateTrendForSubset(this.incidents, horizonDays, filterFn);
      const incidentCount = this.incidents.filter(filterFn).length;
      
      return {
        region,
        riskScore: Math.round(riskScore),
        trend,
        incidentCount,
      };
    }).sort((a, b) => b.riskScore - a.riskScore);
  }
  
  /**
   * Get summary statistics
   */
  getSummaryStats(): {
    totalIncidents: number;
    bySeverity: Record<string, number>;
    byType: Record<string, number>;
    dateRange: { earliest: string; latest: string };
  } {
    const bySeverity: Record<string, number> = {};
    const byType: Record<string, number> = {};
    
    this.incidents.forEach((inc) => {
      bySeverity[inc.severity] = (bySeverity[inc.severity] || 0) + 1;
      byType[inc.type] = (byType[inc.type] || 0) + 1;
    });
    
    const dates = this.incidents.map((inc) => inc.date).sort();
    
    return {
      totalIncidents: this.incidents.length,
      bySeverity,
      byType,
      dateRange: {
        earliest: dates[0] || "",
        latest: dates[dates.length - 1] || "",
      },
    };
  }
}

/**
 * Convenience function for quick predictions
 * 
 * @param incidents - Historical incident data
 * @param horizonDays - Prediction horizon
 * @returns Prediction result
 */
export function predictCyberRisk(incidents: Incident[], horizonDays: number): PredictionResult {
  const engine = new CyberPredictionEngine();
  engine.loadIncidents(incidents);
  return engine.predict(horizonDays);
}
