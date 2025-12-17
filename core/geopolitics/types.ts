/**
 * Geopolitics Engine - Type Definitions
 * 
 * Legal Notice: Offensive Security Simulation – Aucun accès non autorisé – 
 * Usage audit, formation et évaluation uniquement.
 */

export type GeopoliticalEvent = {
  date: string;           // ISO date
  region: string;         // EU, NA, APAC, AF, SA, ME
  title: string;
  description?: string;
  impact: "low" | "medium" | "high";
  correlation: string;    // Cyber correlation text
  sources?: string[];     // OSINT sources
};

export type RegionAnalysis = {
  region: string;
  eventCount: number;
  avgImpact: number;      // 0-100
  trend: "escalating" | "stable" | "de-escalating";
  mainThemes: string[];
  cyberCorrelations: string[];
};

export type GeopoliticalAnalysisResult = {
  timeline: GeopoliticalEvent[];
  byRegion: RegionAnalysis[];
  summary: string;
  generatedAt: string;
};
