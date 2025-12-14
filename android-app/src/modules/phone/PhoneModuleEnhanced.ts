/**
 * PHASE B+ Sprint 1 - ThreatScore & Explainability
 * 
 * Extends PhoneModule with intelligent scoring and explainable decisions.
 * 
 * IMPORTANT:
 * - All processing is LOCAL only
 * - No cloud ML or external APIs
 * - Transparent decision-making
 * - User-understandable explanations
 * 
 * PHASE B+ Sprint 2 Extension:
 * - ARCEP factor integration (France telemarketing ranges)
 */

import { calculateArcepFactor, getArcepExplanation, isArcepDemarchage } from './arcepRanges';

/**
 * Threat Score with detailed breakdown
 * Helps users understand WHY a number is suspicious
 */
export interface ThreatScore {
  overall: number;           // Score global 0-100 (0=safe, 100=dangerous)
  breakdown: {
    frequency: number;       // Fréquence d'appels suspects (0-20 pts)
    timing: number;          // Horaires inhabituels (0-15 pts)
    duration: number;        // Durée typique < 10s = suspect (0-25 pts)
    pattern: number;         // Motifs d'appels répétitifs (0-20 pts)
    source: number;          // Origine géographique suspecte (0-20 pts)
    arcep: number;           // Numéro ARCEP démarchage (0-20 pts) - France only
  };
  riskLevel: 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  explanation: string;       // Explication lisible pour l'utilisateur
  recommendations: string[]; // Actions suggérées
}

/**
 * Call Memory - Local reputation database
 * Stores historical data for learning without cloud ML
 */
export interface CallMemory {
  number: string;
  firstSeen: number;         // Timestamp première occurrence
  lastSeen: number;          // Timestamp dernière occurrence
  totalCalls: number;        // Nombre total d'appels
  userActions: {
    blocked: number;         // Fois bloqué par utilisateur
    answered: number;        // Fois répondu
    ignored: number;         // Fois ignoré
    reported: number;        // Fois signalé comme spam
  };
  userNotes?: string;        // Notes personnelles utilisateur
  communityScore?: number;   // Score communauté (opt-in, local network only)
  tags: string[];            // ex: 'démarchage', 'famille', 'travail', 'spam'
  averageDuration: number;   // Durée moyenne des appels
  callTimes: number[];       // Heures des appels (pour détection pattern)
}

/**
 * Call Decision Explanation - Explainable AI
 * Every decision comes with transparent reasoning
 */
export interface CallDecisionExplanation {
  decision: 'ALLOW' | 'BLOCK' | 'WARN';
  confidence: number;        // 0-100%
  factors: DecisionFactor[];
  reasoning: string;         // Explication complète en français
  alternativeAction?: string; // Action alternative suggérée
}

/**
 * Individual decision factor
 */
export interface DecisionFactor {
  name: string;              // Nom du facteur (ex: "Durée appel")
  weight: number;            // Poids dans la décision (-50 à +50 pts)
  value: string;             // Valeur observée (ex: "< 5 secondes")
  impact: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  explanation: string;       // Pourquoi ce facteur est important
}

/**
 * Protection Profile - User preference for protection level
 */
export enum ProtectionProfile {
  MINIMAL = 'minimal',       // Alertes uniquement, pas de blocage auto
  BALANCED = 'balanced',     // Bloquer spam évident uniquement
  AGGRESSIVE = 'aggressive', // Bloquer tous numéros inconnus suspects
  PARANOID = 'paranoid',     // Whitelist uniquement - bloquer tout le reste
  CUSTOM = 'custom'          // Configuration manuelle fine
}

/**
 * Timeline Entry for suspicious activity tracking
 */
export interface TimelineEntry {
  id: string;
  timestamp: number;
  event: string;             // Description événement
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  details: string;           // Détails supplémentaires
  automated: boolean;        // Action automatique ou manuelle
  number?: string;           // Numéro concerné (si applicable)
  action?: string;           // Action prise (bloqué, autorisé, etc.)
}

/**
 * Phone Module Extensions for Phase B+ Sprint 1
 */
export class PhoneModuleEnhanced {
  
  private callMemoryStore: Map<string, CallMemory> = new Map();
  private activityTimeline: TimelineEntry[] = [];
  private protectionProfile: ProtectionProfile = ProtectionProfile.BALANCED;
  private timelineIdCounter = 0;
  
  // ========================================
  // THREAT SCORING SYSTEM
  // ========================================
  
  /**
   * Calculate comprehensive threat score for a number
   * Uses local heuristics and historical data
   */
  calculateThreatScore(
    number: string,
    callHistory?: CallMemory
  ): ThreatScore {
    const breakdown = {
      frequency: 0,
      timing: 0,
      duration: 0,
      pattern: 0,
      source: 0,
      arcep: 0,
    };
    
    // Analyze frequency (0-20 pts)
    if (callHistory) {
      const callsPerDay = callHistory.totalCalls / 
        ((Date.now() - callHistory.firstSeen) / (1000 * 60 * 60 * 24));
      
      if (callsPerDay > 5) breakdown.frequency = 20;       // > 5 appels/jour = très suspect
      else if (callsPerDay > 3) breakdown.frequency = 15;  // 3-5 appels/jour = suspect
      else if (callsPerDay > 1) breakdown.frequency = 10;  // 1-3 appels/jour = modéré
      else if (callsPerDay > 0.5) breakdown.frequency = 5; // 0.5-1 appel/jour = léger
    }
    
    // Analyze timing patterns (0-15 pts)
    if (callHistory && callHistory.callTimes.length > 0) {
      const nightCalls = callHistory.callTimes.filter(h => h < 8 || h > 22).length;
      const nightRatio = nightCalls / callHistory.callTimes.length;
      
      if (nightRatio > 0.5) breakdown.timing = 15;      // >50% appels nocturnes
      else if (nightRatio > 0.3) breakdown.timing = 10; // 30-50% nocturnes
      else if (nightRatio > 0.1) breakdown.timing = 5;  // 10-30% nocturnes
    }
    
    // Analyze call duration (0-25 pts)
    if (callHistory) {
      if (callHistory.averageDuration < 3) breakdown.duration = 25;       // <3s = robocall
      else if (callHistory.averageDuration < 10) breakdown.duration = 20; // 3-10s = très suspect
      else if (callHistory.averageDuration < 30) breakdown.duration = 10; // 10-30s = suspect
      else if (callHistory.averageDuration < 60) breakdown.duration = 5;  // 30-60s = modéré
    }
    
    // Analyze user actions pattern (0-20 pts)
    if (callHistory && callHistory.totalCalls > 0) {
      const blockRatio = callHistory.userActions.blocked / callHistory.totalCalls;
      const ignoreRatio = callHistory.userActions.ignored / callHistory.totalCalls;
      const reportRatio = callHistory.userActions.reported / callHistory.totalCalls;
      
      if (reportRatio > 0.5) breakdown.pattern = 20;      // >50% signalé
      else if (blockRatio > 0.7) breakdown.pattern = 18;  // >70% bloqué
      else if (ignoreRatio > 0.8) breakdown.pattern = 15; // >80% ignoré
      else if (blockRatio > 0.5) breakdown.pattern = 12;  // >50% bloqué
    }
    
    // Analyze source/origin (0-20 pts)
    const sourceRisk = this.analyzeNumberSource(number);
    breakdown.source = sourceRisk;
    
    // Analyze ARCEP factor (0-20 pts) - France telemarketing ranges
    breakdown.arcep = calculateArcepFactor(number);
    
    // Calculate overall score (now includes ARCEP factor)
    const overall = Math.min(100,
      breakdown.frequency +
      breakdown.timing +
      breakdown.duration +
      breakdown.pattern +
      breakdown.source +
      breakdown.arcep
    );
    
    // Determine risk level
    let riskLevel: ThreatScore['riskLevel'];
    if (overall >= 80) riskLevel = 'CRITICAL';
    else if (overall >= 60) riskLevel = 'HIGH';
    else if (overall >= 40) riskLevel = 'MEDIUM';
    else if (overall >= 20) riskLevel = 'LOW';
    else riskLevel = 'SAFE';
    
    // Generate explanation (including ARCEP if applicable)
    const explanation = this.generateThreatExplanation(breakdown, overall, riskLevel, number);
    
    // Generate recommendations
    const recommendations = this.generateRecommendations(riskLevel, callHistory);
    
    return {
      overall,
      breakdown,
      riskLevel,
      explanation,
      recommendations,
    };
  }
  
  /**
   * Analyze number source for suspicious patterns
   */
  private analyzeNumberSource(number: string): number {
    let risk = 0;
    
    // Check for invalid/spoofed patterns
    if (!number || number.length < 3) risk += 15;
    if (number.startsWith('0000')) risk += 20;
    if (number.match(/^(\d)\1{9,}$/)) risk += 20; // Repeated digits
    
    // Check for known spam patterns
    if (number.match(/^(\+33|0)[89]/)) risk += 5; // Premium numbers FR
    if (number.length > 15) risk += 10; // Suspiciously long
    
    // Check geographic indicators (basic)
    // International calls from certain regions may be higher risk
    if (number.startsWith('+212') || number.startsWith('+216')) {
      risk += 5; // North Africa call centers (common for spam)
    }
    
    return Math.min(20, risk);
  }
  
  /**
   * Generate human-readable threat explanation
   */
  private generateThreatExplanation(
    breakdown: ThreatScore['breakdown'],
    overall: number,
    riskLevel: string,
    number: string
  ): string {
    const parts: string[] = [];
    
    parts.push(`Niveau de risque : ${riskLevel} (${overall}/100)`);
    parts.push('\nAnalyse détaillée :');
    
    if (breakdown.frequency > 10) {
      parts.push(`\n• Fréquence élevée d'appels (${breakdown.frequency} pts)`);
    }
    
    if (breakdown.timing > 5) {
      parts.push(`\n• Appels à horaires inhabituels (${breakdown.timing} pts)`);
    }
    
    if (breakdown.duration > 10) {
      parts.push(`\n• Durée d'appel très courte (${breakdown.duration} pts)`);
    }
    
    if (breakdown.pattern > 10) {
      parts.push(`\n• Comportement utilisateur négatif (${breakdown.pattern} pts)`);
    }
    
    if (breakdown.source > 10) {
      parts.push(`\n• Origine suspecte détectée (${breakdown.source} pts)`);
    }
    
    if (breakdown.arcep > 0) {
      const arcepExplanation = getArcepExplanation(number);
      if (arcepExplanation) {
        parts.push(`\n• ${arcepExplanation} (${breakdown.arcep} pts)`);
      }
    }
    
    if (overall < 20) {
      parts.push('\n\nCe numéro semble légitime.');
    }
    
    return parts.join('');
  }
  
  /**
   * Generate actionable recommendations based on risk
   */
  private generateRecommendations(
    riskLevel: ThreatScore['riskLevel'],
    history?: CallMemory
  ): string[] {
    const recommendations: string[] = [];
    
    switch (riskLevel) {
      case 'CRITICAL':
        recommendations.push('Bloquer automatiquement ce numéro');
        recommendations.push('Signaler comme spam si pas déjà fait');
        recommendations.push('Ne jamais rappeler ce numéro');
        break;
        
      case 'HIGH':
        recommendations.push('Bloquer ce numéro');
        recommendations.push('Ne pas répondre aux appels futurs');
        if (!history?.userActions.reported) {
          recommendations.push('Envisager de signaler comme spam');
        }
        break;
        
      case 'MEDIUM':
        recommendations.push('Ignorer cet appel');
        recommendations.push('Vérifier si numéro connu avant de répondre');
        recommendations.push('Surveiller les appels futurs');
        break;
        
      case 'LOW':
        recommendations.push('Répondre avec prudence');
        recommendations.push('Vérifier l\'identité de l\'appelant');
        break;
        
      case 'SAFE':
        recommendations.push('Numéro semble sûr');
        if (history && history.userActions.answered > 0) {
          recommendations.push('Vous avez déjà répondu à ce numéro');
        }
        break;
    }
    
    return recommendations;
  }
  
  // ========================================
  // EXPLAINABLE DECISIONS
  // ========================================
  
  /**
   * Make call decision with full explanation
   */
  makeCallDecision(
    number: string,
    callHistory?: CallMemory
  ): CallDecisionExplanation {
    const factors: DecisionFactor[] = [];
    let totalWeight = 0;
    
    // Factor 1: Call history
    if (callHistory) {
      const blockRatio = callHistory.userActions.blocked / callHistory.totalCalls;
      
      if (blockRatio > 0.7) {
        const factor: DecisionFactor = {
          name: 'Historique de blocage',
          weight: -30,
          value: `${Math.round(blockRatio * 100)}% bloqué`,
          impact: 'NEGATIVE',
          explanation: 'Vous avez fréquemment bloqué ce numéro'
        };
        factors.push(factor);
        totalWeight += factor.weight;
      } else if (callHistory.userActions.answered > 5) {
        const factor: DecisionFactor = {
          name: 'Historique de réponses',
          weight: +25,
          value: `${callHistory.userActions.answered} réponses`,
          impact: 'POSITIVE',
          explanation: 'Vous avez répondu plusieurs fois à ce numéro'
        };
        factors.push(factor);
        totalWeight += factor.weight;
      }
    }
    
    // Factor 2: Duration pattern
    if (callHistory && callHistory.averageDuration < 10) {
      const factor: DecisionFactor = {
        name: 'Durée moyenne',
        weight: -25,
        value: `${Math.round(callHistory.averageDuration)}s`,
        impact: 'NEGATIVE',
        explanation: 'Appels très courts typiques de robocalls'
      };
      factors.push(factor);
      totalWeight += factor.weight;
    } else if (callHistory && callHistory.averageDuration > 60) {
      const factor: DecisionFactor = {
        name: 'Durée moyenne',
        weight: +15,
        value: `${Math.round(callHistory.averageDuration)}s`,
        impact: 'POSITIVE',
        explanation: 'Conversations normales détectées'
      };
      factors.push(factor);
      totalWeight += factor.weight;
    }
    
    // Factor 3: Frequency
    if (callHistory) {
      const callsPerDay = callHistory.totalCalls / 
        ((Date.now() - callHistory.firstSeen) / (1000 * 60 * 60 * 24));
      
      if (callsPerDay > 3) {
        const factor: DecisionFactor = {
          name: 'Fréquence d\'appels',
          weight: -20,
          value: `${Math.round(callsPerDay * 10) / 10} appels/jour`,
          impact: 'NEGATIVE',
          explanation: 'Fréquence inhabituelle pour numéro légitime'
        };
        factors.push(factor);
        totalWeight += factor.weight;
      }
    }
    
    // Factor 4: Source analysis
    const sourceRisk = this.analyzeNumberSource(number);
    if (sourceRisk > 10) {
      const factor: DecisionFactor = {
        name: 'Origine du numéro',
        weight: -sourceRisk,
        value: 'Pattern suspect détecté',
        impact: 'NEGATIVE',
        explanation: 'Format de numéro ou origine géographique suspect'
      };
      factors.push(factor);
      totalWeight += factor.weight;
    }
    
    // Factor 5: ARCEP telemarketing range (France)
    if (isArcepDemarchage(number)) {
      const arcepExplanation = getArcepExplanation(number);
      const factor: DecisionFactor = {
        name: 'Plage ARCEP démarchage',
        weight: -15,
        value: 'Numéro réservé démarchage',
        impact: 'NEGATIVE',
        explanation: arcepExplanation || 'Numéro dans plage ARCEP démarchage commercial'
      };
      factors.push(factor);
      totalWeight += factor.weight;
    }
    
    // Factor 6: User reports
    if (callHistory && callHistory.userActions.reported > 0) {
      const factor: DecisionFactor = {
        name: 'Signalements spam',
        weight: -35,
        value: `${callHistory.userActions.reported} signalement(s)`,
        impact: 'NEGATIVE',
        explanation: 'Vous avez signalé ce numéro comme spam'
      };
      factors.push(factor);
      totalWeight += factor.weight;
    }
    
    // Apply protection profile
    const profileAdjustment = this.getProfileAdjustment();
    totalWeight += profileAdjustment;
    
    // Make decision
    let decision: CallDecisionExplanation['decision'];
    let confidence: number;
    
    if (totalWeight <= -50) {
      decision = 'BLOCK';
      confidence = Math.min(95, 70 + Math.abs(totalWeight) / 2);
    } else if (totalWeight <= -20) {
      decision = 'WARN';
      confidence = Math.min(85, 60 + Math.abs(totalWeight));
    } else {
      decision = 'ALLOW';
      confidence = Math.min(90, 50 + totalWeight);
    }
    
    // Generate reasoning
    const reasoning = this.generateDecisionReasoning(decision, confidence, factors);
    
    // Alternative action
    let alternativeAction: string | undefined;
    if (decision === 'BLOCK') {
      alternativeAction = 'Autoriser avec surveillance renforcée';
    } else if (decision === 'WARN') {
      alternativeAction = decision === 'WARN' ? 'Bloquer préventivement' : 'Répondre avec prudence';
    }
    
    return {
      decision,
      confidence,
      factors,
      reasoning,
      alternativeAction,
    };
  }
  
  /**
   * Get adjustment based on protection profile
   */
  private getProfileAdjustment(): number {
    switch (this.protectionProfile) {
      case ProtectionProfile.MINIMAL:
        return +20; // More permissive
      case ProtectionProfile.BALANCED:
        return 0;   // Neutral
      case ProtectionProfile.AGGRESSIVE:
        return -15; // More restrictive
      case ProtectionProfile.PARANOID:
        return -30; // Very restrictive
      case ProtectionProfile.CUSTOM:
        return 0;   // User-defined
      default:
        return 0;
    }
  }
  
  /**
   * Generate human-readable reasoning for decision
   */
  private generateDecisionReasoning(
    decision: string,
    confidence: number,
    factors: DecisionFactor[]
  ): string {
    const parts: string[] = [];
    
    parts.push(`Décision : ${decision === 'BLOCK' ? 'BLOQUER' : decision === 'WARN' ? 'AVERTIR' : 'AUTORISER'}`);
    parts.push(`Confiance : ${Math.round(confidence)}%`);
    parts.push('\n\nFacteurs analysés :');
    
    factors.forEach(f => {
      const icon = f.impact === 'NEGATIVE' ? '✗' : f.impact === 'POSITIVE' ? '✓' : '○';
      parts.push(`\n${icon} ${f.name} : ${f.value} (${f.weight > 0 ? '+' : ''}${f.weight} pts)`);
      parts.push(`\n   ${f.explanation}`);
    });
    
    // Add profile info
    parts.push(`\n\nProfil de protection : ${this.protectionProfile.toUpperCase()}`);
    
    return parts.join('');
  }
  
  // ========================================
  // CALL MEMORY MANAGEMENT
  // ========================================
  
  /**
   * Update call memory with new call data
   */
  updateCallMemory(
    number: string,
    duration: number,
    action: 'answered' | 'blocked' | 'ignored' | 'reported'
  ): void {
    let memory = this.callMemoryStore.get(number);
    
    if (!memory) {
      memory = {
        number,
        firstSeen: Date.now(),
        lastSeen: Date.now(),
        totalCalls: 1,
        userActions: {
          blocked: 0,
          answered: 0,
          ignored: 0,
          reported: 0,
        },
        tags: [],
        averageDuration: duration,
        callTimes: [new Date().getHours()],
      };
    } else {
      memory.lastSeen = Date.now();
      memory.totalCalls++;
      
      // Update average duration
      memory.averageDuration = 
        (memory.averageDuration * (memory.totalCalls - 1) + duration) / memory.totalCalls;
      
      // Track call time
      memory.callTimes.push(new Date().getHours());
      
      // Keep only last 100 call times
      if (memory.callTimes.length > 100) {
        memory.callTimes = memory.callTimes.slice(-100);
      }
    }
    
    // Update action count
    memory.userActions[action]++;
    
    // Auto-tag based on behavior
    if (memory.userActions.reported > 0 && !memory.tags.includes('spam')) {
      memory.tags.push('spam');
    }
    if (memory.userActions.blocked > 3 && !memory.tags.includes('démarchage')) {
      memory.tags.push('démarchage');
    }
    
    this.callMemoryStore.set(number, memory);
  }
  
  /**
   * Get call memory for a number
   */
  getCallMemory(number: string): CallMemory | undefined {
    return this.callMemoryStore.get(number);
  }
  
  /**
   * Add user note to call memory
   */
  addCallNote(number: string, note: string): void {
    const memory = this.callMemoryStore.get(number);
    if (memory) {
      memory.userNotes = note;
      this.callMemoryStore.set(number, memory);
    }
  }
  
  /**
   * Add tag to call memory
   */
  addCallTag(number: string, tag: string): void {
    const memory = this.callMemoryStore.get(number);
    if (memory && !memory.tags.includes(tag)) {
      memory.tags.push(tag);
      this.callMemoryStore.set(number, memory);
    }
  }
  
  // ========================================
  // ACTIVITY TIMELINE
  // ========================================
  
  /**
   * Add entry to activity timeline
   */
  addTimelineEntry(
    event: string,
    severity: TimelineEntry['severity'],
    details: string,
    automated: boolean,
    number?: string,
    action?: string
  ): void {
    const entry: TimelineEntry = {
      id: `timeline-${this.timelineIdCounter++}`,
      timestamp: Date.now(),
      event,
      severity,
      details,
      automated,
      number,
      action,
    };
    
    this.activityTimeline.unshift(entry); // Add to beginning
    
    // Keep only last 1000 entries
    if (this.activityTimeline.length > 1000) {
      this.activityTimeline = this.activityTimeline.slice(0, 1000);
    }
  }
  
  /**
   * Get timeline entries
   */
  getTimeline(limit?: number): TimelineEntry[] {
    if (limit) {
      return this.activityTimeline.slice(0, limit);
    }
    return this.activityTimeline;
  }
  
  /**
   * Get timeline entries by severity
   */
  getTimelineBySeverity(severity: TimelineEntry['severity']): TimelineEntry[] {
    return this.activityTimeline.filter(e => e.severity === severity);
  }
  
  /**
   * Clear old timeline entries
   */
  clearOldTimeline(days: number): void {
    const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
    this.activityTimeline = this.activityTimeline.filter(e => e.timestamp > cutoff);
  }
  
  // ========================================
  // PROTECTION PROFILE MANAGEMENT
  // ========================================
  
  /**
   * Set protection profile
   */
  setProtectionProfile(profile: ProtectionProfile): void {
    this.protectionProfile = profile;
    
    this.addTimelineEntry(
      'Protection profile changed',
      'LOW',
      `Profile set to ${profile}`,
      false
    );
  }
  
  /**
   * Get current protection profile
   */
  getProtectionProfile(): ProtectionProfile {
    return this.protectionProfile;
  }
  
  // ========================================
  // STATISTICS & INSIGHTS
  // ========================================
  
  /**
   * Get statistics on call activity
   */
  getCallStatistics(): {
    totalNumbers: number;
    totalCalls: number;
    spamCalls: number;
    blockedCalls: number;
    answeredCalls: number;
    averageThreatScore: number;
  } {
    let totalCalls = 0;
    let spamCalls = 0;
    let blockedCalls = 0;
    let answeredCalls = 0;
    let totalThreatScore = 0;
    
    this.callMemoryStore.forEach((memory) => {
      totalCalls += memory.totalCalls;
      blockedCalls += memory.userActions.blocked;
      answeredCalls += memory.userActions.answered;
      
      if (memory.tags.includes('spam') || memory.userActions.reported > 0) {
        spamCalls += memory.totalCalls;
      }
      
      const threat = this.calculateThreatScore(memory.number, memory);
      totalThreatScore += threat.overall;
    });
    
    return {
      totalNumbers: this.callMemoryStore.size,
      totalCalls,
      spamCalls,
      blockedCalls,
      answeredCalls,
      averageThreatScore: this.callMemoryStore.size > 0 
        ? totalThreatScore / this.callMemoryStore.size 
        : 0,
    };
  }
}

// Export singleton instance
export const phoneModuleEnhanced = new PhoneModuleEnhanced();
