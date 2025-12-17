/**
 * Decision Support Engine - Type Definitions
 * 
 * Legal Notice: Offensive Security Simulation – Aucun accès non autorisé – 
 * Usage audit, formation et évaluation uniquement.
 */

export type Priority = "Critical" | "High" | "Medium" | "Low";
export type Category = "Detection" | "Response" | "Prevention" | "Recovery" | "Governance";
export type Effort = "Low" | "Medium" | "High";
export type Impact = "Low" | "Medium" | "High";

export type Recommendation = {
  id: string;
  priority: Priority;
  category: Category;
  title: string;
  rationale: string;
  actions: string[];
  effort: Effort;
  impact: Impact;
  timeline: string;
  frameworks?: string[]; // ANSSI, NIST, ISO 27001
};

export type DecisionContext = {
  topRisks: Array<{ sector: string; region: string; score: number }>;
  constraints?: {
    budget?: "low" | "medium" | "high";
    sovereignty?: boolean; // Préférence solutions souveraines
    compliance?: string[]; // ANSSI, GDPR, etc.
  };
  currentPosture?: {
    maturityLevel?: number; // 1-5
    coverageGaps?: string[];
  };
};

export type DecisionSupportResult = {
  recommendations: Recommendation[];
  summary: string;
  priorityBreakdown: Record<Priority, number>;
  generatedAt: string;
};
