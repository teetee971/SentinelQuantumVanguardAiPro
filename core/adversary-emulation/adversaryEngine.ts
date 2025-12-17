/**
 * Adversary Emulation Engine
 * 
 * LEGAL NOTICE:
 * "Offensive Security Simulation – Aucun accès non autorisé – Usage audit, formation et évaluation uniquement."
 * 
 * This module emulates real-world adversary behaviors based on known APT groups.
 * NO REAL ATTACKS - LOGICAL SIMULATION ONLY
 * 
 * @version 2.0.0
 * @compliance ANSSI, NIST, MITRE ATT&CK, INSTITUTIONAL
 */

/**
 * Adversary Profile Interface
 */
export interface AdversaryProfile {
  id: string;
  publicName: string;
  type: 'APT' | 'Cybercrime' | 'Hacktivism' | 'InsiderThreat' | 'StateSponsored';
  motivations: string[];
  supposedCountry?: string; // Simulated attribution
  mitreTechniques: string[]; // MITRE ATT&CK technique IDs
  attackChains: AttackChain[];
  typicalObjectives: string[];
  behavioralSignatures: BehavioralSignature[];
  sophisticationLevel: 'low' | 'medium' | 'high' | 'advanced';
  ttpFingerprint: string; // Unique TTP signature
}

/**
 * Attack Chain
 */
export interface AttackChain {
  phase: string;
  techniques: string[];
  duration: number; // minutes
  stealthLevel: 'low' | 'medium' | 'high';
}

/**
 * Behavioral Signature
 */
export interface BehavioralSignature {
  category: 'timing' | 'tools' | 'infrastructure' | 'targeting';
  pattern: string;
  confidence: number; // 0-100
}

/**
 * Campaign Simulation
 */
export interface CampaignSimulation {
  id: string;
  adversaryId: string;
  adversaryName: string;
  startTime: Date;
  endTime?: Date;
  phases: CampaignPhase[];
  status: 'planning' | 'active' | 'completed' | 'detected' | 'contained';
  detectionTime?: Date; // When SOC detected the campaign
  metrics: CampaignMetrics;
}

/**
 * Campaign Phase
 */
export interface CampaignPhase {
  phaseName: string;
  startTime: Date;
  endTime?: Date;
  techniques: string[];
  events: AdversaryEvent[];
  detected: boolean;
  detectionTime?: Date;
}

/**
 * Adversary Event
 */
export interface AdversaryEvent {
  id: string;
  timestamp: Date;
  adversaryId: string;
  phase: string;
  techniqueId: string;
  action: string;
  impact: 'reconnaissance' | 'initial-access' | 'execution' | 'persistence' | 
          'privilege-escalation' | 'defense-evasion' | 'credential-access' |
          'discovery' | 'lateral-movement' | 'collection' | 'exfiltration' | 'impact';
  stealthScore: number; // 0-100 (higher = stealthier)
  severity: 'low' | 'medium' | 'high' | 'critical';
  iocGenerated: string[];
  simulated: true; // ALWAYS true
}

/**
 * Campaign Metrics
 */
export interface CampaignMetrics {
  totalEvents: number;
  detectedEvents: number;
  detectionRate: number; // percentage
  meanTimeToDetect: number; // milliseconds
  stealthScore: number; // 0-100
  successRate: number; // percentage (offensive success)
  socVisibility: number; // percentage (SOC coverage)
  mitreConverage: number; // percentage of MITRE matrix
}

/**
 * Adversary Emulation Engine
 * 
 * Simulates realistic adversary campaigns without any real attacks
 */
export class AdversaryEmulationEngine {
  private adversaries: Map<string, AdversaryProfile> = new Map();
  private activeCampaigns: Map<string, CampaignSimulation> = new Map();
  private eventLog: AdversaryEvent[] = [];

  constructor() {
    console.log('🎭 Adversary Emulation Engine initialized');
    console.log('⚠️ LEGAL MODE: Logical simulation only - No real attacks');
    console.log('🛡️ INSTITUTIONAL COMPLIANCE: ANSSI / NIST / MITRE');
  }

  /**
   * Load adversary profile
   */
  loadAdversary(profile: AdversaryProfile): void {
    // Validate compliance
    if (!this.validateAdversaryCompliance(profile)) {
      throw new Error(`Adversary profile failed compliance validation: ${profile.id}`);
    }

    this.adversaries.set(profile.id, profile);
    console.log(`✅ Adversary loaded: ${profile.publicName} (${profile.type})`);
  }

  /**
   * Validate adversary profile compliance
   */
  private validateAdversaryCompliance(profile: AdversaryProfile): boolean {
    // Check 1: No real exploitation keywords
    const blacklist = ['exploit', 'weaponize', 'compromise', 'breach'];
    const profileStr = JSON.stringify(profile).toLowerCase();
    
    for (const keyword of blacklist) {
      if (profileStr.includes(keyword)) {
        console.error(`❌ COMPLIANCE: Dangerous keyword detected: ${keyword}`);
        return false;
      }
    }

    // Check 2: Must have MITRE techniques
    if (!profile.mitreTechniques || profile.mitreTechniques.length === 0) {
      console.error('❌ COMPLIANCE: No MITRE techniques specified');
      return false;
    }

    // Check 3: Attack chains must be logical only
    for (const chain of profile.attackChains) {
      if (!chain.phase || !chain.techniques) {
        console.error('❌ COMPLIANCE: Invalid attack chain structure');
        return false;
      }
    }

    return true;
  }

  /**
   * Simulate adversary campaign
   */
  async simulateCampaign(adversaryId: string, targetProfile: string = 'generic'): Promise<CampaignSimulation> {
    const adversary = this.adversaries.get(adversaryId);
    
    if (!adversary) {
      throw new Error(`Adversary not found: ${adversaryId}`);
    }

    console.log(`🚀 Starting campaign simulation: ${adversary.publicName}`);
    console.log(`   Type: ${adversary.type}`);
    console.log(`   Sophistication: ${adversary.sophisticationLevel}`);

    const campaign: CampaignSimulation = {
      id: `campaign-${Date.now()}`,
      adversaryId: adversary.id,
      adversaryName: adversary.publicName,
      startTime: new Date(),
      phases: [],
      status: 'active',
      metrics: {
        totalEvents: 0,
        detectedEvents: 0,
        detectionRate: 0,
        meanTimeToDetect: 0,
        stealthScore: 0,
        successRate: 0,
        socVisibility: 0,
        mitreConverage: 0
      }
    };

    // Execute attack chains
    for (const chain of adversary.attackChains) {
      const phase = await this.executeAttackChain(campaign, adversary, chain);
      campaign.phases.push(phase);

      // Check if detected
      if (phase.detected) {
        campaign.status = 'detected';
        campaign.detectionTime = phase.detectionTime;
        console.log(`   🚨 Campaign detected at phase: ${phase.phaseName}`);
        break;
      }
    }

    campaign.endTime = new Date();
    
    if (campaign.status === 'active') {
      campaign.status = 'completed';
    }

    // Calculate final metrics
    campaign.metrics = this.calculateCampaignMetrics(campaign, adversary);

    this.activeCampaigns.set(campaign.id, campaign);

    console.log(`✅ Campaign simulation completed: ${adversary.publicName}`);
    console.log(`   Duration: ${campaign.endTime.getTime() - campaign.startTime.getTime()}ms`);
    console.log(`   Events: ${campaign.metrics.totalEvents}`);
    console.log(`   Detection Rate: ${campaign.metrics.detectionRate.toFixed(1)}%`);
    console.log(`   Success Rate: ${campaign.metrics.successRate.toFixed(1)}%`);

    return campaign;
  }

  /**
   * Execute attack chain phase
   */
  private async executeAttackChain(
    campaign: CampaignSimulation,
    adversary: AdversaryProfile,
    chain: AttackChain
  ): Promise<CampaignPhase> {
    const phase: CampaignPhase = {
      phaseName: chain.phase,
      startTime: new Date(),
      techniques: chain.techniques,
      events: [],
      detected: false
    };

    console.log(`  📍 Phase: ${chain.phase}`);

    for (const techniqueId of chain.techniques) {
      // Generate events for this technique
      const events = this.generateAdversaryEvents(
        adversary,
        chain.phase,
        techniqueId,
        chain.stealthLevel
      );

      phase.events.push(...events);
      this.eventLog.push(...events);

      // Simulate detection probability
      const detectionProbability = this.calculateDetectionProbability(
        chain.stealthLevel,
        adversary.sophisticationLevel,
        events.length
      );

      if (Math.random() < detectionProbability) {
        phase.detected = true;
        phase.detectionTime = new Date();
        break;
      }

      // Simulate realistic timing
      await this.delay(chain.duration * 100); // Scaled down for simulation
    }

    phase.endTime = new Date();
    console.log(`    ✓ ${chain.phase}: ${phase.events.length} events, detected: ${phase.detected}`);

    return phase;
  }

  /**
   * Generate adversary events
   */
  private generateAdversaryEvents(
    adversary: AdversaryProfile,
    phase: string,
    techniqueId: string,
    stealthLevel: string
  ): AdversaryEvent[] {
    const events: AdversaryEvent[] = [];
    const eventCount = this.getEventCount(stealthLevel);
    const baseTime = Date.now();

    for (let i = 0; i < eventCount; i++) {
      const event: AdversaryEvent = {
        id: `adv-evt-${baseTime}-${i}`,
        timestamp: new Date(baseTime + i * 1000),
        adversaryId: adversary.id,
        phase: phase,
        techniqueId: techniqueId,
        action: this.generateActionDescription(phase, techniqueId),
        impact: this.mapPhaseToImpact(phase),
        stealthScore: this.calculateStealthScore(stealthLevel, adversary.sophisticationLevel),
        severity: this.determineSeverity(phase),
        iocGenerated: this.generateIOCs(techniqueId),
        simulated: true // ALWAYS true
      };

      events.push(event);
    }

    return events;
  }

  /**
   * Get event count based on stealth level
   */
  private getEventCount(stealthLevel: string): number {
    const counts: Record<string, number> = {
      'low': 8,    // More noisy
      'medium': 5,
      'high': 3    // More stealthy
    };
    return counts[stealthLevel] || 5;
  }

  /**
   * Calculate stealth score
   */
  private calculateStealthScore(stealthLevel: string, sophistication: string): number {
    const baseScores: Record<string, number> = {
      'low': 30,
      'medium': 60,
      'high': 85
    };

    const sophisticationBonus: Record<string, number> = {
      'low': 0,
      'medium': 5,
      'high': 10,
      'advanced': 15
    };

    return Math.min(
      (baseScores[stealthLevel] || 50) + (sophisticationBonus[sophistication] || 0),
      100
    );
  }

  /**
   * Calculate detection probability
   */
  private calculateDetectionProbability(
    stealthLevel: string,
    sophistication: string,
    eventCount: number
  ): number {
    // Base detection rates
    const baseRates: Record<string, number> = {
      'low': 0.7,    // 70% chance of detection
      'medium': 0.4,  // 40% chance
      'high': 0.15    // 15% chance
    };

    let probability = baseRates[stealthLevel] || 0.5;

    // Adjust for sophistication
    const sophisticationModifier: Record<string, number> = {
      'low': 0.1,
      'medium': 0,
      'high': -0.1,
      'advanced': -0.2
    };
    probability += sophisticationModifier[sophistication] || 0;

    // More events = higher detection chance
    probability += (eventCount / 100);

    return Math.max(0, Math.min(1, probability));
  }

  /**
   * Generate action description
   */
  private generateActionDescription(phase: string, techniqueId: string): string {
    const templates: Record<string, string> = {
      'Reconnaissance': `MITRE ${techniqueId}: Network reconnaissance activity detected`,
      'Initial Access': `MITRE ${techniqueId}: Initial access attempt observed`,
      'Execution': `MITRE ${techniqueId}: Code execution detected`,
      'Persistence': `MITRE ${techniqueId}: Persistence mechanism established`,
      'Privilege Escalation': `MITRE ${techniqueId}: Privilege escalation detected`,
      'Defense Evasion': `MITRE ${techniqueId}: Defense evasion technique observed`,
      'Credential Access': `MITRE ${techniqueId}: Credential access attempt`,
      'Discovery': `MITRE ${techniqueId}: System discovery activity`,
      'Lateral Movement': `MITRE ${techniqueId}: Lateral movement detected`,
      'Collection': `MITRE ${techniqueId}: Data collection activity`,
      'Exfiltration': `MITRE ${techniqueId}: Data exfiltration attempt`,
      'Impact': `MITRE ${techniqueId}: Impact operation detected`
    };

    return templates[phase] || `MITRE ${techniqueId}: Adversary activity simulated`;
  }

  /**
   * Map phase to impact category
   */
  private mapPhaseToImpact(phase: string): AdversaryEvent['impact'] {
    const mapping: Record<string, AdversaryEvent['impact']> = {
      'Reconnaissance': 'reconnaissance',
      'Initial Access': 'initial-access',
      'Execution': 'execution',
      'Persistence': 'persistence',
      'Privilege Escalation': 'privilege-escalation',
      'Defense Evasion': 'defense-evasion',
      'Credential Access': 'credential-access',
      'Discovery': 'discovery',
      'Lateral Movement': 'lateral-movement',
      'Collection': 'collection',
      'Exfiltration': 'exfiltration',
      'Impact': 'impact'
    };

    return mapping[phase] || 'reconnaissance';
  }

  /**
   * Determine severity
   */
  private determineSeverity(phase: string): AdversaryEvent['severity'] {
    const severities: Record<string, AdversaryEvent['severity']> = {
      'Reconnaissance': 'low',
      'Initial Access': 'medium',
      'Execution': 'high',
      'Persistence': 'high',
      'Privilege Escalation': 'critical',
      'Defense Evasion': 'medium',
      'Credential Access': 'critical',
      'Discovery': 'low',
      'Lateral Movement': 'high',
      'Collection': 'medium',
      'Exfiltration': 'critical',
      'Impact': 'critical'
    };

    return severities[phase] || 'medium';
  }

  /**
   * Generate IOCs
   */
  private generateIOCs(techniqueId: string): string[] {
    const iocTypes = ['ip', 'domain', 'hash', 'process', 'registry'];
    const selectedTypes = iocTypes.slice(0, Math.floor(Math.random() * 3) + 1);

    return selectedTypes.map(type => {
      switch (type) {
        case 'ip':
          return `IOC-IP: 203.0.113.${Math.floor(Math.random() * 256)}`;
        case 'domain':
          return `IOC-DOMAIN: ${Math.random().toString(36).substring(7)}.malicious.example`;
        case 'hash':
          return `IOC-HASH-SHA256: ${Array(64).fill(0).map(() => Math.floor(Math.random() * 16).toString(16)).join('')}`;
        case 'process':
          return `IOC-PROCESS: suspicious_${Math.random().toString(36).substring(7)}.exe`;
        case 'registry':
          return `IOC-REGISTRY: HKLM\\Software\\${Math.random().toString(36).substring(7)}`;
        default:
          return `IOC-UNKNOWN`;
      }
    });
  }

  /**
   * Calculate campaign metrics
   */
  private calculateCampaignMetrics(
    campaign: CampaignSimulation,
    adversary: AdversaryProfile
  ): CampaignMetrics {
    const allEvents = campaign.phases.flatMap(p => p.events);
    const detectedPhases = campaign.phases.filter(p => p.detected);
    const detectedEvents = detectedPhases.flatMap(p => p.events);

    // Mean Time To Detect (simulated)
    let mttd = 0;
    if (detectedPhases.length > 0) {
      const detectionTimes = detectedPhases.map(p => 
        p.detectionTime!.getTime() - p.startTime.getTime()
      );
      mttd = detectionTimes.reduce((a, b) => a + b, 0) / detectionTimes.length;
    }

    // Stealth score (average)
    const avgStealth = allEvents.length > 0
      ? allEvents.reduce((sum, e) => sum + e.stealthScore, 0) / allEvents.length
      : 0;

    // Success rate (did adversary complete campaign before detection?)
    const successRate = campaign.status === 'completed' ? 100 :
      (campaign.phases.length / adversary.attackChains.length) * 100;

    // SOC Visibility (inverse of stealth)
    const socVisibility = 100 - avgStealth;

    // MITRE coverage
    const uniqueTechniques = new Set(allEvents.map(e => e.techniqueId));
    const mitreConverage = (uniqueTechniques.size / 193) * 100; // 193 total MITRE techniques

    return {
      totalEvents: allEvents.length,
      detectedEvents: detectedEvents.length,
      detectionRate: (detectedEvents.length / allEvents.length) * 100,
      meanTimeToDetect: mttd,
      stealthScore: avgStealth,
      successRate,
      socVisibility,
      mitreConverage
    };
  }

  /**
   * Get campaign results
   */
  getCampaignResults(campaignId: string): CampaignSimulation | undefined {
    return this.activeCampaigns.get(campaignId);
  }

  /**
   * Get all adversaries
   */
  getAllAdversaries(): AdversaryProfile[] {
    return Array.from(this.adversaries.values());
  }

  /**
   * Get event log
   */
  getEventLog(): AdversaryEvent[] {
    return [...this.eventLog];
  }

  /**
   * Export campaign to JSON
   */
  exportCampaignJSON(campaignId: string): string {
    const campaign = this.activeCampaigns.get(campaignId);
    if (!campaign) {
      throw new Error(`Campaign not found: ${campaignId}`);
    }
    return JSON.stringify(campaign, null, 2);
  }

  /**
   * Delay helper
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

// Export singleton
export const adversaryEngine = new AdversaryEmulationEngine();

// Browser compatibility
if (typeof window !== 'undefined') {
  (window as any).AdversaryEmulationEngine = AdversaryEmulationEngine;
  (window as any).adversaryEngine = adversaryEngine;
}

console.log('🎭 Adversary Emulation Engine loaded');
console.log('⚖️ LEGAL NOTICE: Offensive Security Simulation – Aucun accès non autorisé');
console.log('🏛️ INSTITUTIONAL MODE: ANSSI / NIST / MITRE compliant');
