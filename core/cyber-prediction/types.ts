/**
 * Cyber Prediction Engine - Type Definitions
 *
 * Defensive use notice: aucune action non autorisée.
 * Usage d'analyse de risque, audit, formation et évaluation uniquement.
 */

export type Severity = "low" | "medium" | "high" | "critical";

export type Incident = {
  date: string;           // ISO (YYYY-MM-DD)
  sector: string;         // ex: "Government", "Finance"
  region: string;         // ex: "EU", "NA", "AF"
  type: string;           // ex: "ransomware", "phishing"
  severity: Severity;
};

export type TrendDirection = "down" | "flat" | "up";

export type SectorPrediction = {
  sector: string;
  riskScore: number;      // 0-100
  trend: TrendDirection;
  incidentCount: number;
};

export type RegionPrediction = {
  region: string;
  riskScore: number;      // 0-100
  trend: TrendDirection;
  incidentCount: number;
};

export type PredictionResult = {
  horizonDays: number;
  bySector: SectorPrediction[];
  byRegion: RegionPrediction[];
  notes: string[];
  generatedAt: string;    // ISO timestamp
};

export type CVETrend = {
  cve: string;
  score: number;          // CVSS score
  date: string;           // ISO
  sector: string;
  affectedSystems: string[];
};

export type TTPStat = {
  techniqueId: string;    // MITRE ATT&CK ID (ex: T1566)
  name: string;
  count: number;
  period: string;         // ex: "2024-Q4"
  sectors: string[];
};
