/**
 * PHASE B+ Sprint 2 - Persistent Memory & Behavioral Baseline
 * 
 * Extends PhoneModuleEnhanced with persistent local storage and behavioral learning.
 * 
 * IMPORTANT:
 * - All data stored LOCALLY only (AsyncStorage/SQLite)
 * - NO cloud synchronization
 * - NO external APIs
 * - Behavioral baseline learning without ML
 * - Google Play compliant
 */

import { CallMemory } from './PhoneModuleEnhanced';

/**
 * Behavioral Baseline - Learn normal user behavior
 * Detects deviations from established patterns
 */
export interface BehaviorBaseline {
  established: boolean;          // Baseline établie après période apprentissage
  learningStarted: number;       // Timestamp début apprentissage
  learningPeriod: number;        // Période d'apprentissage (ms) - défaut 7 jours
  lastUpdated: number;           // Dernière mise à jour
  
  // Phone metrics
  phoneMetrics: {
    avgCallsPerDay: number;      // Moyenne appels/jour
    avgCallDuration: number;     // Durée moyenne appels
    commonCallHours: number[];   // Heures habituelles (0-23)
    avgSpamPerDay: number;       // Moyenne spam détecté/jour
    avgBlockedPerDay: number;    // Moyenne bloqués/jour
    peakHours: number[];         // Top 3 heures les plus actives
  };
  
  // User behavior patterns
  behaviorPatterns: {
    answerRate: number;          // % appels répondus
    blockRate: number;           // % appels bloqués
    ignoreRate: number;          // % appels ignorés
    reportRate: number;          // % appels signalés
  };
  
  // Temporal patterns
  temporalPatterns: {
    weekdayActivity: number[];   // Activité par jour semaine (0=dim)
    weekendActivity: number[];   // Activité weekend séparée
    nightCalls: number;          // % appels nocturnes (22h-8h)
    businessHours: number;       // % appels heures bureau (9h-18h)
  };
  
  // Deviation tracking
  deviations: Deviation[];
}

/**
 * Deviation from baseline
 */
export interface Deviation {
  metric: string;                // Nom métrique
  expected: number;              // Valeur attendue (baseline)
  actual: number;                // Valeur actuelle
  variance: number;              // % écart
  significant: boolean;          // Écart significatif (>30%)
  timestamp: number;             // Quand détecté
  explanation: string;           // Explication lisible
}

/**
 * Malicious Pattern Detection
 */
export interface MaliciousPattern {
  type: 'SPAM_WAVE' | 'SPOOFING_CAMPAIGN' | 'ROBOCALL_BURST' | 
        'UNUSUAL_FREQUENCY' | 'COORDINATED_CALLS';
  detectedAt: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  evidence: string[];            // Preuves détectées
  affectedNumbers: string[];     // Numéros concernés
  recommendation: string;        // Action recommandée
  autoBlocked: boolean;          // Bloqué automatiquement
}

/**
 * Persistent Storage Interface
 * Abstraction for AsyncStorage or SQLite
 */
export interface PersistentStorage {
  saveCallMemory(number: string, memory: CallMemory): Promise<void>;
  loadCallMemory(number: string): Promise<CallMemory | null>;
  loadAllCallMemory(): Promise<Map<string, CallMemory>>;
  deleteCallMemory(number: string): Promise<void>;
  
  saveBaseline(baseline: BehaviorBaseline): Promise<void>;
  loadBaseline(): Promise<BehaviorBaseline | null>;
  
  saveMaliciousPatterns(patterns: MaliciousPattern[]): Promise<void>;
  loadMaliciousPatterns(): Promise<MaliciousPattern[]>;
}

/**
 * Score Change Explanation
 * Explains why threat score changed over time
 */
export interface ScoreChangeExplanation {
  previousScore: number;
  currentScore: number;
  change: number;               // Variation points
  changePercent: number;        // Variation %
  reason: string;               // Raison principale
  factors: {
    name: string;
    oldValue: number;
    newValue: number;
    impact: number;
    explanation: string;
  }[];
  trend: 'IMPROVING' | 'WORSENING' | 'STABLE';
}

/**
 * Phone Module with Persistent Memory & Baseline
 */
export class PhoneModulePersistent {
  
  private storage: PersistentStorage;
  private callMemoryCache: Map<string, CallMemory> = new Map();
  private baseline: BehaviorBaseline | null = null;
  private maliciousPatterns: MaliciousPattern[] = [];
  
  // Learning constants
  private readonly DEFAULT_LEARNING_PERIOD = 7 * 24 * 60 * 60 * 1000; // 7 jours
  private readonly SIGNIFICANT_DEVIATION_THRESHOLD = 0.30; // 30%
  private readonly SPAM_WAVE_THRESHOLD = 5; // 5 spam en 1h
  private readonly ROBOCALL_BURST_THRESHOLD = 3; // 3 robocalls en 10min
  
  constructor(storage: PersistentStorage) {
    this.storage = storage;
    this.initialize();
  }
  
  /**
   * Initialize - Load data from persistent storage
   */
  private async initialize(): Promise<void> {
    try {
      // Load all call memory
      this.callMemoryCache = await this.storage.loadAllCallMemory();
      
      // Load baseline
      this.baseline = await this.storage.loadBaseline();
      
      // Load malicious patterns
      this.maliciousPatterns = await this.storage.loadMaliciousPatterns();
      
      // Initialize baseline if not exists
      if (!this.baseline) {
        this.baseline = this.createEmptyBaseline();
      }
      
      // Update baseline if established
      if (this.baseline.established) {
        await this.updateBaseline();
      }
    } catch (error) {
      console.error('PhoneModulePersistent: Initialization failed', error);
    }
  }
  
  // ========================================
  // PERSISTENT CALL MEMORY
  // ========================================
  
  /**
   * Save call memory to persistent storage
   */
  async saveCallMemory(number: string, memory: CallMemory): Promise<void> {
    try {
      // Update cache
      this.callMemoryCache.set(number, memory);
      
      // Persist to storage
      await this.storage.saveCallMemory(number, memory);
      
      // Update baseline with new data
      if (this.baseline) {
        await this.updateBaseline();
      }
    } catch (error) {
      console.error('Failed to save call memory:', error);
    }
  }
  
  /**
   * Load call memory from persistent storage
   */
  async loadCallMemory(number: string): Promise<CallMemory | null> {
    // Check cache first
    if (this.callMemoryCache.has(number)) {
      return this.callMemoryCache.get(number) || null;
    }
    
    // Load from storage
    try {
      const memory = await this.storage.loadCallMemory(number);
      if (memory) {
        this.callMemoryCache.set(number, memory);
      }
      return memory;
    } catch (error) {
      console.error('Failed to load call memory:', error);
      return null;
    }
  }
  
  /**
   * Get all call memory (from cache)
   */
  getAllCallMemory(): Map<string, CallMemory> {
    return this.callMemoryCache;
  }
  
  /**
   * Delete call memory
   */
  async deleteCallMemory(number: string): Promise<void> {
    try {
      this.callMemoryCache.delete(number);
      await this.storage.deleteCallMemory(number);
    } catch (error) {
      console.error('Failed to delete call memory:', error);
    }
  }
  
  /**
   * Clear old call memory (older than X days)
   */
  async clearOldCallMemory(days: number): Promise<void> {
    const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
    const toDelete: string[] = [];
    
    this.callMemoryCache.forEach((memory, number) => {
      if (memory.lastSeen < cutoff) {
        toDelete.push(number);
      }
    });
    
    for (const number of toDelete) {
      await this.deleteCallMemory(number);
    }
  }
  
  // ========================================
  // BEHAVIORAL BASELINE
  // ========================================
  
  /**
   * Create empty baseline
   */
  private createEmptyBaseline(): BehaviorBaseline {
    return {
      established: false,
      learningStarted: Date.now(),
      learningPeriod: this.DEFAULT_LEARNING_PERIOD,
      lastUpdated: Date.now(),
      
      phoneMetrics: {
        avgCallsPerDay: 0,
        avgCallDuration: 0,
        commonCallHours: [],
        avgSpamPerDay: 0,
        avgBlockedPerDay: 0,
        peakHours: [],
      },
      
      behaviorPatterns: {
        answerRate: 0,
        blockRate: 0,
        ignoreRate: 0,
        reportRate: 0,
      },
      
      temporalPatterns: {
        weekdayActivity: [0, 0, 0, 0, 0],
        weekendActivity: [0, 0],
        nightCalls: 0,
        businessHours: 0,
      },
      
      deviations: [],
    };
  }
  
  /**
   * Update baseline with current data
   */
  async updateBaseline(): Promise<void> {
    if (!this.baseline) return;
    
    const now = Date.now();
    const learningComplete = now - this.baseline.learningStarted >= this.baseline.learningPeriod;
    
    // Calculate metrics from call memory
    const metrics = this.calculateBaselineMetrics();
    
    // Update baseline
    this.baseline.phoneMetrics = metrics.phoneMetrics;
    this.baseline.behaviorPatterns = metrics.behaviorPatterns;
    this.baseline.temporalPatterns = metrics.temporalPatterns;
    this.baseline.lastUpdated = now;
    
    // Mark as established after learning period
    if (learningComplete && !this.baseline.established) {
      this.baseline.established = true;
      console.log('PhoneModulePersistent: Baseline established');
    }
    
    // Detect deviations if baseline established
    if (this.baseline.established) {
      this.baseline.deviations = this.detectDeviations();
    }
    
    // Persist baseline
    await this.storage.saveBaseline(this.baseline);
  }
  
  /**
   * Calculate baseline metrics from call memory
   */
  private calculateBaselineMetrics(): {
    phoneMetrics: BehaviorBaseline['phoneMetrics'];
    behaviorPatterns: BehaviorBaseline['behaviorPatterns'];
    temporalPatterns: BehaviorBaseline['temporalPatterns'];
  } {
    let totalCalls = 0;
    let totalDuration = 0;
    let totalSpam = 0;
    let totalBlocked = 0;
    let totalAnswered = 0;
    let totalIgnored = 0;
    let totalReported = 0;
    
    const hourCounts = new Array(24).fill(0);
    const weekdayCounts = new Array(5).fill(0);
    const weekendCounts = new Array(2).fill(0);
    let nightCallCount = 0;
    let businessHourCount = 0;
    
    // Aggregate data from all call memory
    this.callMemoryCache.forEach((memory) => {
      totalCalls += memory.totalCalls;
      totalDuration += memory.averageDuration * memory.totalCalls;
      totalBlocked += memory.userActions.blocked;
      totalAnswered += memory.userActions.answered;
      totalIgnored += memory.userActions.ignored;
      totalReported += memory.userActions.reported;
      
      if (memory.tags.includes('spam') || memory.userActions.reported > 0) {
        totalSpam += memory.totalCalls;
      }
      
      // Temporal analysis
      memory.callTimes.forEach(hour => {
        hourCounts[hour]++;
        
        if (hour < 8 || hour >= 22) nightCallCount++;
        if (hour >= 9 && hour < 18) businessHourCount++;
      });
    });
    
    // Calculate averages
    const daysOfData = this.baseline 
      ? (Date.now() - this.baseline.learningStarted) / (24 * 60 * 60 * 1000)
      : 1;
    
    const avgCallsPerDay = totalCalls / Math.max(1, daysOfData);
    const avgCallDuration = totalCalls > 0 ? totalDuration / totalCalls : 0;
    const avgSpamPerDay = totalSpam / Math.max(1, daysOfData);
    const avgBlockedPerDay = totalBlocked / Math.max(1, daysOfData);
    
    // Find common hours (hours with >10% of calls)
    const commonCallHours = hourCounts
      .map((count, hour) => ({ hour, count }))
      .filter(h => h.count > totalCalls * 0.1)
      .map(h => h.hour);
    
    // Find peak hours (top 3)
    const peakHours = hourCounts
      .map((count, hour) => ({ hour, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 3)
      .map(h => h.hour);
    
    // Behavior patterns
    const answerRate = totalCalls > 0 ? totalAnswered / totalCalls : 0;
    const blockRate = totalCalls > 0 ? totalBlocked / totalCalls : 0;
    const ignoreRate = totalCalls > 0 ? totalIgnored / totalCalls : 0;
    const reportRate = totalCalls > 0 ? totalReported / totalCalls : 0;
    
    // Temporal patterns
    const nightCalls = totalCalls > 0 ? nightCallCount / totalCalls : 0;
    const businessHours = totalCalls > 0 ? businessHourCount / totalCalls : 0;
    
    return {
      phoneMetrics: {
        avgCallsPerDay,
        avgCallDuration,
        commonCallHours,
        avgSpamPerDay,
        avgBlockedPerDay,
        peakHours,
      },
      behaviorPatterns: {
        answerRate,
        blockRate,
        ignoreRate,
        reportRate,
      },
      temporalPatterns: {
        weekdayActivity: weekdayCounts,
        weekendActivity: weekendCounts,
        nightCalls,
        businessHours,
      },
    };
  }
  
  /**
   * Detect deviations from baseline
   */
  private detectDeviations(): Deviation[] {
    if (!this.baseline || !this.baseline.established) return [];
    
    const deviations: Deviation[] = [];
    const currentMetrics = this.calculateBaselineMetrics();
    
    // Check calls per day deviation
    const callsDeviation = this.calculateDeviation(
      'avgCallsPerDay',
      this.baseline.phoneMetrics.avgCallsPerDay,
      currentMetrics.phoneMetrics.avgCallsPerDay
    );
    if (callsDeviation) deviations.push(callsDeviation);
    
    // Check spam per day deviation
    const spamDeviation = this.calculateDeviation(
      'avgSpamPerDay',
      this.baseline.phoneMetrics.avgSpamPerDay,
      currentMetrics.phoneMetrics.avgSpamPerDay
    );
    if (spamDeviation) deviations.push(spamDeviation);
    
    // Check block rate deviation
    const blockDeviation = this.calculateDeviation(
      'blockRate',
      this.baseline.behaviorPatterns.blockRate,
      currentMetrics.behaviorPatterns.blockRate
    );
    if (blockDeviation) deviations.push(blockDeviation);
    
    // Check night calls deviation
    const nightDeviation = this.calculateDeviation(
      'nightCalls',
      this.baseline.temporalPatterns.nightCalls,
      currentMetrics.temporalPatterns.nightCalls
    );
    if (nightDeviation) deviations.push(nightDeviation);
    
    return deviations;
  }
  
  /**
   * Calculate single deviation
   */
  private calculateDeviation(
    metric: string,
    expected: number,
    actual: number
  ): Deviation | null {
    if (expected === 0) return null;
    
    const variance = Math.abs((actual - expected) / expected);
    const significant = variance >= this.SIGNIFICANT_DEVIATION_THRESHOLD;
    
    if (!significant) return null;
    
    const explanations: Record<string, string> = {
      avgCallsPerDay: `Activité téléphonique ${actual > expected ? 'inhabituelle' : 'réduite'}: ${actual.toFixed(1)} vs ${expected.toFixed(1)} appels/jour`,
      avgSpamPerDay: `Vague de spam détectée: ${actual.toFixed(1)} vs ${expected.toFixed(1)} spam/jour normalement`,
      blockRate: `Taux de blocage ${actual > expected ? 'augmenté' : 'diminué'}: ${(actual * 100).toFixed(0)}% vs ${(expected * 100).toFixed(0)}% habituellement`,
      nightCalls: `Appels nocturnes inhabituels: ${(actual * 100).toFixed(0)}% vs ${(expected * 100).toFixed(0)}% normalement`,
    };
    
    return {
      metric,
      expected,
      actual,
      variance,
      significant,
      timestamp: Date.now(),
      explanation: explanations[metric] || `Écart détecté pour ${metric}`,
    };
  }
  
  /**
   * Get current baseline
   */
  getBaseline(): BehaviorBaseline | null {
    return this.baseline;
  }
  
  /**
   * Reset baseline (start learning again)
   */
  async resetBaseline(): Promise<void> {
    this.baseline = this.createEmptyBaseline();
    await this.storage.saveBaseline(this.baseline);
  }
  
  // ========================================
  // MALICIOUS PATTERN DETECTION
  // ========================================
  
  /**
   * Detect malicious patterns in recent activity
   */
  async detectMaliciousPatterns(): Promise<MaliciousPattern[]> {
    const patterns: MaliciousPattern[] = [];
    const now = Date.now();
    const oneHourAgo = now - 60 * 60 * 1000;
    const tenMinutesAgo = now - 10 * 60 * 1000;
    
    // Count recent spam and robocalls
    let recentSpam = 0;
    let recentRobocalls = 0;
    const recentNumbers: string[] = [];
    
    this.callMemoryCache.forEach((memory, number) => {
      if (memory.lastSeen >= oneHourAgo) {
        recentNumbers.push(number);
        
        if (memory.tags.includes('spam')) {
          recentSpam++;
        }
        
        if (memory.averageDuration < 5) {
          recentRobocalls++;
        }
      }
    });
    
    // Detect spam wave (5+ spam in 1 hour)
    if (recentSpam >= this.SPAM_WAVE_THRESHOLD) {
      patterns.push({
        type: 'SPAM_WAVE',
        detectedAt: now,
        severity: recentSpam >= 10 ? 'CRITICAL' : 'HIGH',
        description: `Vague de spam détectée: ${recentSpam} appels spam dans la dernière heure`,
        evidence: [
          `${recentSpam} appels spam identifiés`,
          `Détecté dans les 60 dernières minutes`,
          `Baseline normale: ${this.baseline?.phoneMetrics.avgSpamPerDay.toFixed(1) || 0} spam/jour`,
        ],
        affectedNumbers: recentNumbers.filter(n => {
          const mem = this.callMemoryCache.get(n);
          return mem?.tags.includes('spam');
        }),
        recommendation: 'Activer blocage automatique spam',
        autoBlocked: false,
      });
    }
    
    // Detect robocall burst (3+ robocalls in 10 minutes)
    if (recentRobocalls >= this.ROBOCALL_BURST_THRESHOLD) {
      patterns.push({
        type: 'ROBOCALL_BURST',
        detectedAt: now,
        severity: 'MEDIUM',
        description: `Rafale de robocalls: ${recentRobocalls} détectés`,
        evidence: [
          `${recentRobocalls} appels < 5s détectés`,
          `Pattern typique robocall`,
        ],
        affectedNumbers: recentNumbers.filter(n => {
          const mem = this.callMemoryCache.get(n);
          return mem && mem.averageDuration < 5;
        }),
        recommendation: 'Bloquer les numéros suspects',
        autoBlocked: false,
      });
    }
    
    // Detect unusual frequency (compared to baseline)
    if (this.baseline?.established) {
      const deviation = this.baseline.deviations.find(d => d.metric === 'avgCallsPerDay');
      if (deviation && deviation.significant && deviation.actual > deviation.expected * 2) {
        patterns.push({
          type: 'UNUSUAL_FREQUENCY',
          detectedAt: now,
          severity: 'MEDIUM',
          description: 'Fréquence d\'appels inhabituelle détectée',
          evidence: [
            `${deviation.actual.toFixed(1)} appels/jour vs ${deviation.expected.toFixed(1)} normalement`,
            `Augmentation de ${(deviation.variance * 100).toFixed(0)}%`,
          ],
          affectedNumbers: [],
          recommendation: 'Surveiller l\'activité téléphonique',
          autoBlocked: false,
        });
      }
    }
    
    // Save detected patterns
    this.maliciousPatterns = patterns;
    await this.storage.saveMaliciousPatterns(patterns);
    
    return patterns;
  }
  
  /**
   * Get malicious patterns
   */
  getMaliciousPatterns(): MaliciousPattern[] {
    return this.maliciousPatterns;
  }
  
  /**
   * Clear old malicious patterns
   */
  async clearOldPatterns(days: number): Promise<void> {
    const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
    this.maliciousPatterns = this.maliciousPatterns.filter(p => p.detectedAt >= cutoff);
    await this.storage.saveMaliciousPatterns(this.maliciousPatterns);
  }
  
  // ========================================
  // SCORE CHANGE EXPLANATION
  // ========================================
  
  /**
   * Explain why a number's threat score changed
   */
  explainScoreChange(
    number: string,
    previousScore: number,
    currentScore: number,
    memory: CallMemory
  ): ScoreChangeExplanation {
    const change = currentScore - previousScore;
    const changePercent = previousScore > 0 
      ? (change / previousScore) * 100 
      : 0;
    
    const factors: ScoreChangeExplanation['factors'] = [];
    
    // Analyze what changed
    // This is a simplified version - in production, you'd track previous values
    
    if (memory.userActions.blocked > 0) {
      factors.push({
        name: 'Blocages utilisateur',
        oldValue: 0, // Simplified
        newValue: memory.userActions.blocked,
        impact: -15,
        explanation: 'Vous avez bloqué ce numéro, augmentant le score de menace',
      });
    }
    
    if (memory.userActions.reported > 0) {
      factors.push({
        name: 'Signalements spam',
        oldValue: 0,
        newValue: memory.userActions.reported,
        impact: -20,
        explanation: 'Signalement comme spam influence fortement le score',
      });
    }
    
    if (memory.totalCalls > 10) {
      factors.push({
        name: 'Fréquence élevée',
        oldValue: 5,
        newValue: memory.totalCalls,
        impact: -10,
        explanation: 'Nombre d\'appels inhabituel pour un contact légitime',
      });
    }
    
    // Determine trend
    let trend: ScoreChangeExplanation['trend'];
    if (Math.abs(change) < 5) trend = 'STABLE';
    else if (change > 0) trend = 'WORSENING';
    else trend = 'IMPROVING';
    
    // Generate reason
    let reason: string;
    if (trend === 'WORSENING') {
      reason = 'Comportement suspect détecté (blocages, fréquence élevée)';
    } else if (trend === 'IMPROVING') {
      reason = 'Interactions positives enregistrées';
    } else {
      reason = 'Comportement stable, pas de changement significatif';
    }
    
    return {
      previousScore,
      currentScore,
      change,
      changePercent,
      reason,
      factors,
      trend,
    };
  }
}

/**
 * Mock Persistent Storage Implementation
 * In production, replace with AsyncStorage or SQLite
 */
export class MockPersistentStorage implements PersistentStorage {
  private callMemoryStore: Map<string, CallMemory> = new Map();
  private baseline: BehaviorBaseline | null = null;
  private patterns: MaliciousPattern[] = [];
  
  async saveCallMemory(number: string, memory: CallMemory): Promise<void> {
    this.callMemoryStore.set(number, memory);
  }
  
  async loadCallMemory(number: string): Promise<CallMemory | null> {
    return this.callMemoryStore.get(number) || null;
  }
  
  async loadAllCallMemory(): Promise<Map<string, CallMemory>> {
    return new Map(this.callMemoryStore);
  }
  
  async deleteCallMemory(number: string): Promise<void> {
    this.callMemoryStore.delete(number);
  }
  
  async saveBaseline(baseline: BehaviorBaseline): Promise<void> {
    this.baseline = baseline;
  }
  
  async loadBaseline(): Promise<BehaviorBaseline | null> {
    return this.baseline;
  }
  
  async saveMaliciousPatterns(patterns: MaliciousPattern[]): Promise<void> {
    this.patterns = patterns;
  }
  
  async loadMaliciousPatterns(): Promise<MaliciousPattern[]> {
    return this.patterns;
  }
}

// Export singleton instance with mock storage
// In production, replace MockPersistentStorage with AsyncStorageImpl or SQLiteImpl
export const phoneModulePersistent = new PhoneModulePersistent(
  new MockPersistentStorage()
);
