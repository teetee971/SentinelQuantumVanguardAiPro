/**
 * RÉPONDEUR IA SIMULÉ
 * 
 * Simule un assistant IA capable de:
 * 1. Répondre à l'appel à la place de l'utilisateur
 * 2. Dialogue neutre et professionnel
 * 3. Analyse comportementale de l'appelant
 * 4. Génération d'un rapport post-appel
 * 
 * IMPORTANT:
 * - Simulation uniquement (aucune IA réelle)
 * - Nécessite consentement utilisateur explicite
 * - Transparence totale sur le fonctionnement
 * - Conforme aux lois sur l'enregistrement
 */

export interface AIAssistantConfig {
  enabled: boolean;
  autoAnswer: boolean;        // Répondre automatiquement?
  recordCall: boolean;         // Enregistrer l'appel?
  neutralTone: boolean;        // Ton neutre ou personnalisé?
  maxDuration: number;         // Durée max en secondes
  language: 'fr' | 'en';
}

export interface CallDialogue {
  timestamp: number;
  speaker: 'AI' | 'CALLER';
  text: string;
  tone: 'neutral' | 'suspicious' | 'commercial' | 'urgent' | 'threatening';
}

export interface BehavioralAnalysis {
  callerType: 'human' | 'robot' | 'mixed';
  confidence: number;           // 0-100%
  patterns: {
    repeatedPhrases: string[];  // Phrases répétées
    pressureTactics: boolean;   // Tactiques de pression?
    urgencyLevel: number;       // 0-10
    requestedInfo: string[];    // Infos demandées
    suspiciousKeywords: string[]; // Mots-clés suspects
  };
  sentiment: 'positive' | 'neutral' | 'negative' | 'aggressive';
  scamIndicators: number;       // 0-100
}

export interface PostCallReport {
  callId: string;
  phoneNumber: string;
  duration: number;             // Secondes
  startTime: number;
  endTime: number;
  dialogue: CallDialogue[];
  behavioralAnalysis: BehavioralAnalysis;
  recommendation: 'SAFE' | 'BLOCK' | 'REPORT' | 'UNCERTAIN';
  summary: string;              // Résumé français
  transcription?: string;       // Transcription complète (si activée)
}

/**
 * Mode Zéro Interaction
 * Gestion automatique sans intervention utilisateur
 */
export interface ZeroInteractionMode {
  enabled: boolean;
  autoBlockThreshold: number;   // Score de risque auto-block
  autoAnswerWithAI: boolean;    // Répondre auto avec IA?
  whitelistOnly: boolean;       // Accepter uniquement contacts?
  notifyUser: boolean;          // Notifier l'utilisateur?
}

/**
 * Mode Institution
 * Journal d'audit, lecture seule, conformité entreprise
 */
export interface InstitutionMode {
  enabled: boolean;
  readOnly: boolean;            // Mode lecture seule?
  auditLog: boolean;            // Journaliser toutes les actions?
  requireJustification: boolean; // Justification pour actions?
  adminOverride: boolean;       // Admin peut override?
}

/**
 * Classe Répondeur IA
 */
export class AICallAssistant {
  
  private config: AIAssistantConfig = {
    enabled: false,
    autoAnswer: false,
    recordCall: false,
    neutralTone: true,
    maxDuration: 60,
    language: 'fr',
  };
  
  /**
   * Simuler une réponse IA à l'appel
   * SIMULATION UNIQUEMENT - Aucune IA réelle
   */
  async simulateAIResponse(phoneNumber: string): Promise<PostCallReport> {
    const callId = this.generateCallId();
    const startTime = Date.now();
    
    // Simuler dialogue
    const dialogue = this.generateSimulatedDialogue();
    
    // Simuler analyse comportementale
    const behavioralAnalysis = this.analyzeSimulatedBehavior(dialogue);
    
    // Durée simulée
    const duration = Math.floor(Math.random() * 30) + 15; // 15-45 secondes
    const endTime = startTime + (duration * 1000);
    
    // Recommandation basée sur l'analyse
    const recommendation = this.determineRecommendation(behavioralAnalysis);
    
    // Générer résumé
    const summary = this.generateSummary(dialogue, behavioralAnalysis);
    
    return {
      callId,
      phoneNumber,
      duration,
      startTime,
      endTime,
      dialogue,
      behavioralAnalysis,
      recommendation,
      summary,
    };
  }
  
  /**
   * Générer dialogue simulé
   */
  private generateSimulatedDialogue(): CallDialogue[] {
    const scenarios = [
      // Scénario démarchage commercial
      [
        { speaker: 'AI' as const, text: 'Allô?', tone: 'neutral' as const },
        { speaker: 'CALLER' as const, text: 'Bonjour, je vous appelle concernant votre contrat d\'électricité...', tone: 'commercial' as const },
        { speaker: 'AI' as const, text: 'Je ne suis pas intéressé. Au revoir.', tone: 'neutral' as const },
        { speaker: 'CALLER' as const, text: 'Mais attendez, vous pourriez économiser jusqu\'à 30%...', tone: 'commercial' as const },
        { speaker: 'AI' as const, text: 'Non merci. Bonne journée.', tone: 'neutral' as const },
      ],
      // Scénario robocall
      [
        { speaker: 'AI' as const, text: 'Allô?', tone: 'neutral' as const },
        { speaker: 'CALLER' as const, text: 'Vous avez été sélectionné pour...', tone: 'suspicious' as const },
        { speaker: 'AI' as const, text: 'Qui êtes-vous?', tone: 'neutral' as const },
        { speaker: 'CALLER' as const, text: '[silence]', tone: 'suspicious' as const },
      ],
      // Scénario tentative arnaque
      [
        { speaker: 'AI' as const, text: 'Allô?', tone: 'neutral' as const },
        { speaker: 'CALLER' as const, text: 'C\'est urgent! Votre compte bancaire a été compromis!', tone: 'urgent' as const },
        { speaker: 'AI' as const, text: 'Quelle banque?', tone: 'neutral' as const },
        { speaker: 'CALLER' as const, text: 'Nous avons besoin de vos informations immédiatement!', tone: 'urgent' as const },
        { speaker: 'AI' as const, text: 'Je vais raccrocher et appeler ma banque directement.', tone: 'neutral' as const },
      ],
    ];
    
    // Sélectionner scénario aléatoire
    const scenario = scenarios[Math.floor(Math.random() * scenarios.length)];
    
    // Ajouter timestamp
    return scenario.map((line, index) => ({
      timestamp: Date.now() + (index * 3000),
      speaker: line.speaker,
      text: line.text,
      tone: line.tone,
    }));
  }
  
  /**
   * Analyser comportement simulé
   */
  private analyzeSimulatedBehavior(dialogue: CallDialogue[]): BehavioralAnalysis {
    const callerLines = dialogue.filter(d => d.speaker === 'CALLER');
    
    // Détecter phrases répétées
    const repeatedPhrases: string[] = [];
    const phrases = callerLines.map(d => d.text.toLowerCase());
    phrases.forEach((phrase, i) => {
      if (phrases.indexOf(phrase) !== i && !repeatedPhrases.includes(phrase)) {
        repeatedPhrases.push(phrase);
      }
    });
    
    // Détecter tactiques de pression
    const pressureKeywords = ['urgent', 'immédiatement', 'maintenant', 'dernier jour', 'offre limitée'];
    const pressureTactics = callerLines.some(d =>
      pressureKeywords.some(kw => d.text.toLowerCase().includes(kw))
    );
    
    // Niveau d'urgence
    const urgencyKeywords = ['urgent', 'rapidement', 'vite', 'immédiatement'];
    const urgencyLevel = Math.min(10,
      callerLines.reduce((sum, d) =>
        sum + urgencyKeywords.filter(kw => d.text.toLowerCase().includes(kw)).length * 3,
        0
      )
    );
    
    // Infos demandées
    const infoKeywords = ['numéro', 'carte', 'code', 'mot de passe', 'compte', 'RIB', 'IBAN'];
    const requestedInfo = infoKeywords.filter(kw =>
      callerLines.some(d => d.text.toLowerCase().includes(kw))
    );
    
    // Mots-clés suspects
    const scamKeywords = ['gagné', 'tirage au sort', 'sélectionné', 'cadeau', 'gratuit', 'remboursement'];
    const suspiciousKeywords = scamKeywords.filter(kw =>
      callerLines.some(d => d.text.toLowerCase().includes(kw))
    );
    
    // Sentiment
    const negativeTones = dialogue.filter(d =>
      d.tone === 'urgent' || d.tone === 'threatening'
    ).length;
    let sentiment: BehavioralAnalysis['sentiment'] = 'neutral';
    if (negativeTones >= 2) sentiment = 'aggressive';
    else if (negativeTones === 1) sentiment = 'negative';
    
    // Score indicateurs arnaque
    let scamIndicators = 0;
    scamIndicators += pressureTactics ? 30 : 0;
    scamIndicators += urgencyLevel * 5;
    scamIndicators += requestedInfo.length * 15;
    scamIndicators += suspiciousKeywords.length * 10;
    scamIndicators = Math.min(100, scamIndicators);
    
    // Type d'appelant
    const hasRobotPattern = callerLines.some(d => d.text.includes('[silence]'));
    const callerType = hasRobotPattern ? 'robot' : 'human';
    
    return {
      callerType,
      confidence: 75,
      patterns: {
        repeatedPhrases,
        pressureTactics,
        urgencyLevel,
        requestedInfo,
        suspiciousKeywords,
      },
      sentiment,
      scamIndicators,
    };
  }
  
  /**
   * Déterminer recommandation
   */
  private determineRecommendation(
    analysis: BehavioralAnalysis
  ): PostCallReport['recommendation'] {
    if (analysis.scamIndicators >= 70) return 'REPORT';
    if (analysis.scamIndicators >= 50) return 'BLOCK';
    if (analysis.scamIndicators >= 30) return 'UNCERTAIN';
    return 'SAFE';
  }
  
  /**
   * Générer résumé en français
   */
  private generateSummary(
    dialogue: CallDialogue[],
    analysis: BehavioralAnalysis
  ): string {
    const parts: string[] = [];
    
    // Type d'appelant
    if (analysis.callerType === 'robot') {
      parts.push('Appel automatisé (robot) détecté.');
    } else {
      parts.push('Appelant humain détecté.');
    }
    
    // Tactiques
    if (analysis.patterns.pressureTactics) {
      parts.push('Utilise des tactiques de pression.');
    }
    
    if (analysis.patterns.urgencyLevel > 5) {
      parts.push(`Niveau d'urgence élevé (${analysis.patterns.urgencyLevel}/10).`);
    }
    
    // Infos demandées
    if (analysis.patterns.requestedInfo.length > 0) {
      parts.push(
        `Demande d'informations sensibles: ${analysis.patterns.requestedInfo.join(', ')}.`
      );
    }
    
    // Mots-clés suspects
    if (analysis.patterns.suspiciousKeywords.length > 0) {
      parts.push(
        `Mots-clés suspects détectés: ${analysis.patterns.suspiciousKeywords.join(', ')}.`
      );
    }
    
    // Score arnaque
    if (analysis.scamIndicators >= 70) {
      parts.push('⚠️ FORTE PROBABILITÉ D\'ARNAQUE - Bloquer et signaler recommandé.');
    } else if (analysis.scamIndicators >= 50) {
      parts.push('⚠️ Comportement suspect - Bloquer recommandé.');
    } else if (analysis.scamIndicators >= 30) {
      parts.push('Vigilance recommandée.');
    } else {
      parts.push('Aucun indicateur majeur d\'arnaque détecté.');
    }
    
    return parts.join(' ');
  }
  
  /**
   * Générer ID d'appel unique
   */
  private generateCallId(): string {
    return `call_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
  }
  
  /**
   * Configurer l'assistant
   */
  configure(config: Partial<AIAssistantConfig>): void {
    this.config = { ...this.config, ...config };
  }
  
  /**
   * Obtenir configuration actuelle
   */
  getConfig(): AIAssistantConfig {
    return { ...this.config };
  }
}

/**
 * Gestionnaire Mode Zéro Interaction
 */
export class ZeroInteractionManager {
  private mode: ZeroInteractionMode = {
    enabled: false,
    autoBlockThreshold: 70,
    autoAnswerWithAI: false,
    whitelistOnly: false,
    notifyUser: true,
  };
  
  configure(mode: Partial<ZeroInteractionMode>): void {
    this.mode = { ...this.mode, ...mode };
  }
  
  getMode(): ZeroInteractionMode {
    return { ...this.mode };
  }
  
  shouldAutoBlock(riskScore: number): boolean {
    if (!this.mode.enabled) return false;
    return riskScore >= this.mode.autoBlockThreshold;
  }
  
  shouldAutoAnswerWithAI(riskScore: number): boolean {
    if (!this.mode.enabled) return false;
    if (!this.mode.autoAnswerWithAI) return false;
    return riskScore >= 40 && riskScore < this.mode.autoBlockThreshold;
  }
}

/**
 * Gestionnaire Mode Institution
 */
export class InstitutionModeManager {
  private mode: InstitutionMode = {
    enabled: false,
    readOnly: true,
    auditLog: true,
    requireJustification: true,
    adminOverride: false,
  };
  
  private auditLog: Array<{
    timestamp: number;
    action: string;
    user: string;
    details: string;
    justification?: string;
  }> = [];
  
  configure(mode: Partial<InstitutionMode>): void {
    this.mode = { ...this.mode, ...mode };
  }
  
  getMode(): InstitutionMode {
    return { ...this.mode };
  }
  
  logAction(action: string, user: string, details: string, justification?: string): void {
    if (!this.mode.auditLog) return;
    
    this.auditLog.push({
      timestamp: Date.now(),
      action,
      user,
      details,
      justification,
    });
  }
  
  getAuditLog(): typeof this.auditLog {
    return [...this.auditLog];
  }
  
  canPerformAction(user: string, isAdmin: boolean): boolean {
    if (!this.mode.enabled) return true;
    if (this.mode.readOnly && !isAdmin) return false;
    if (this.mode.readOnly && isAdmin && this.mode.adminOverride) return true;
    return !this.mode.readOnly;
  }
}

// Export singletons
export const aiCallAssistant = new AICallAssistant();
export const zeroInteractionManager = new ZeroInteractionManager();
export const institutionModeManager = new InstitutionModeManager();
