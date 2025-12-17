/**
 * Cyber Prediction Engine - Prediction Models
 * 
 * Legal Notice: Offensive Security Simulation – Aucun accès non autorisé – 
 * Usage audit, formation et évaluation uniquement.
 * 
 * MÉTHODOLOGIE TRANSPARENTE :
 * - Modèles statistiques simples (fréquence × sévérité × récence)
 * - AUCUNE "IA magique" ou promesse de prédiction parfaite
 * - Heuristiques éprouvées en analyse de risque
 * - Résultats indicatifs, pas prédictifs absolus
 */

import { Incident, Severity, TrendDirection } from "./types";

/**
 * Clamp a number between min and max
 */
function clamp(n: number, min = 0, max = 100): number {
  return Math.max(min, Math.min(max, n));
}

/**
 * Severity weights for scoring
 */
const SEVERITY_WEIGHTS: Record<Severity, number> = {
  low: 1,
  medium: 2.5,
  high: 5,
  critical: 10,
};

/**
 * Calculate time decay factor (recent incidents weighted higher)
 * 
 * @param daysSinceIncident - Days since the incident occurred
 * @param horizonDays - Prediction horizon in days
 * @returns Decay factor (0-1)
 */
function calculateRecencyWeight(daysSinceIncident: number, horizonDays: number): number {
  // Exponential decay: more recent = higher weight
  const decayRate = 0.05;
  return Math.exp(-decayRate * (daysSinceIncident / horizonDays));
}

/**
 * Calculate trend direction based on recent vs older incidents
 * 
 * @param recentScore - Score from recent period
 * @param olderScore - Score from older period
 * @returns Trend direction
 */
function calculateTrend(recentScore: number, olderScore: number): TrendDirection {
  const threshold = 0.15; // 15% change threshold
  const change = (recentScore - olderScore) / (olderScore || 1);
  
  if (change > threshold) return "up";
  if (change < -threshold) return "down";
  return "flat";
}

/**
 * Simple trend-based risk score calculation
 * 
 * MÉTHODOLOGIE :
 * 1. Fréquence : nombre d'incidents dans la période
 * 2. Sévérité : pondération par niveau de gravité (critical=10, low=1)
 * 3. Récence : incidents récents ont plus de poids (décroissance exponentielle)
 * 4. Normalisation : score ramené à échelle 0-100
 * 
 * @param incidents - Historical incidents
 * @param horizonDays - Prediction horizon (7, 30, 90 days)
 * @param filterFn - Optional filter function to subset incidents
 * @returns Risk score (0-100)
 */
export function simpleTrendScore(
  incidents: Incident[],
  horizonDays: number,
  filterFn?: (incident: Incident) => boolean
): number {
  const now = new Date();
  const relevantIncidents = filterFn ? incidents.filter(filterFn) : incidents;
  
  if (relevantIncidents.length === 0) return 0;
  
  let totalScore = 0;
  let totalWeight = 0;
  
  relevantIncidents.forEach((incident) => {
    const incidentDate = new Date(incident.date);
    const daysSince = Math.floor((now.getTime() - incidentDate.getTime()) / (1000 * 60 * 60 * 24));
    
    // Only consider incidents within lookback window (2x horizon)
    if (daysSince > horizonDays * 2) return;
    
    const severityWeight = SEVERITY_WEIGHTS[incident.severity];
    const recencyWeight = calculateRecencyWeight(daysSince, horizonDays);
    const weight = recencyWeight;
    
    totalScore += severityWeight * weight;
    totalWeight += weight;
  });
  
  if (totalWeight === 0) return 0;
  
  // Normalize to 0-100 scale
  const avgWeightedSeverity = totalScore / totalWeight;
  const frequencyFactor = Math.min(relevantIncidents.length / 10, 3);
  const rawScore = avgWeightedSeverity * frequencyFactor * 10;
  
  return clamp(rawScore, 0, 100);
}

/**
 * Calculate trend for a subset of incidents
 * 
 * @param incidents - Historical incidents
 * @param horizonDays - Prediction horizon
 * @param filterFn - Filter function to subset incidents
 * @returns Trend direction
 */
export function calculateTrendForSubset(
  incidents: Incident[],
  horizonDays: number,
  filterFn: (incident: Incident) => boolean
): TrendDirection {
  const now = new Date();
  const relevantIncidents = incidents.filter(filterFn);
  
  // Split into recent (0-horizon) and older (horizon-2*horizon)
  const recentIncidents = relevantIncidents.filter((inc) => {
    const daysSince = Math.floor((now.getTime() - new Date(inc.date).getTime()) / (1000 * 60 * 60 * 24));
    return daysSince <= horizonDays;
  });
  
  const olderIncidents = relevantIncidents.filter((inc) => {
    const daysSince = Math.floor((now.getTime() - new Date(inc.date).getTime()) / (1000 * 60 * 60 * 24));
    return daysSince > horizonDays && daysSince <= horizonDays * 2;
  });
  
  const recentScore = simpleTrendScore(recentIncidents, horizonDays);
  const olderScore = simpleTrendScore(olderIncidents, horizonDays);
  
  return calculateTrend(recentScore, olderScore);
}

/**
 * Generate risk notes based on predictions
 * 
 * @param bySector - Sector predictions
 * @param byRegion - Region predictions
 * @returns Array of notes
 */
export function generateRiskNotes(
  bySector: Array<{ sector: string; riskScore: number; trend: TrendDirection }>,
  byRegion: Array<{ region: string; riskScore: number; trend: TrendDirection }>
): string[] {
  const notes: string[] = [];
  
  // High-risk sectors
  const highRiskSectors = bySector.filter((s) => s.riskScore >= 70);
  if (highRiskSectors.length > 0) {
    notes.push(
      `Secteurs à risque élevé (≥70): ${highRiskSectors.map((s) => s.sector).join(", ")}`
    );
  }
  
  // Rising trends
  const risingTrends = bySector.filter((s) => s.trend === "up");
  if (risingTrends.length > 0) {
    notes.push(
      `Tendance à la hausse détectée pour: ${risingTrends.map((s) => s.sector).join(", ")}`
    );
  }
  
  // High-risk regions
  const highRiskRegions = byRegion.filter((r) => r.riskScore >= 70);
  if (highRiskRegions.length > 0) {
    notes.push(
      `Régions à risque élevé (≥70): ${highRiskRegions.map((r) => r.region).join(", ")}`
    );
  }
  
  // If all low risk
  if (bySector.every((s) => s.riskScore < 40) && byRegion.every((r) => r.riskScore < 40)) {
    notes.push("Niveau de risque global relativement faible sur tous les secteurs et régions");
  }
  
  return notes;
}
