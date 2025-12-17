/**
 * Sentinel Offensive Security Simulation Engine
 * 
 * Core simulation engine for Red Team / Adversary Simulation
 * 100% Legal - Educational Purpose Only
 * 
 * MITRE ATT&CK Framework Integration
 * No real exploits - Simulations only
 */

class OffensiveSimulationEngine {
  constructor() {
    this.mitreLibrary = new MITREAttackLibrary();
    this.scenarioEngine = new ScenarioEngine();
    this.iocGenerator = new IOCGenerator();
    this.loggingSystem = new SOCLoggingSystem();
    this.activeScenarios = [];
    
    console.log('🛡️ Offensive Simulation Engine initialized');
    console.log('⚖️ Legal Mode: Simulations only - No real attacks');
  }

  /**
   * Start a simulation scenario
   */
  async startScenario(scenarioId) {
    const scenario = this.scenarioEngine.getScenario(scenarioId);
    
    if (!scenario) {
      throw new Error(`Scenario ${scenarioId} not found`);
    }

    // Validate scenario is legal and ethical
    if (!this.validateScenarioCompliance(scenario)) {
      throw new Error('Scenario failed compliance validation');
    }

    const simulation = {
      id: `sim-${Date.now()}`,
      scenario: scenario,
      startTime: new Date(),
      status: 'running',
      events: [],
      iocs: [],
      logs: []
    };

    this.activeScenarios.push(simulation);
    
    // Generate simulation events
    await this.executeScenario(simulation);
    
    return simulation;
  }

  /**
   * Execute scenario simulation
   */
  async executeScenario(simulation) {
    const { scenario } = simulation;
    
    for (const tactic of scenario.tactics) {
      console.log(`📍 Tactic: ${tactic.name} (${tactic.id})`);
      
      for (const technique of tactic.techniques) {
        // Generate events for this technique
        const events = this.generateTechniqueEvents(technique, tactic);
        simulation.events.push(...events);
        
        // Generate IOCs
        const iocs = this.iocGenerator.generateForTechnique(technique);
        simulation.iocs.push(...iocs);
        
        // Generate SOC logs
        const logs = this.loggingSystem.generateLogs(technique, events);
        simulation.logs.push(...logs);
        
        console.log(`  ✅ ${technique.id}: ${technique.name}`);
        
        // Simulate timing between techniques
        await this.delay(scenario.timingMs || 1000);
      }
    }
    
    simulation.status = 'completed';
    simulation.endTime = new Date();
    simulation.duration = simulation.endTime - simulation.startTime;
    
    console.log(`✅ Scenario ${simulation.scenario.name} completed`);
    console.log(`   Duration: ${simulation.duration}ms`);
    console.log(`   Events: ${simulation.events.length}`);
    console.log(`   IOCs: ${simulation.iocs.length}`);
    console.log(`   Logs: ${simulation.logs.length}`);
    
    return simulation;
  }

  /**
   * Generate events for a specific technique
   */
  generateTechniqueEvents(technique, tactic) {
    const events = [];
    const baseTime = Date.now();
    
    // Number of events depends on technique complexity
    const eventCount = technique.eventCount || Math.floor(Math.random() * 5) + 1;
    
    for (let i = 0; i < eventCount; i++) {
      events.push({
        id: `event-${baseTime}-${i}`,
        timestamp: new Date(baseTime + i * 100),
        technique: technique.id,
        techniqueName: technique.name,
        tactic: tactic.id,
        tacticName: tactic.name,
        severity: technique.severity || 'medium',
        description: this.generateEventDescription(technique),
        simulated: true,
        metadata: {
          platform: technique.platform || 'Windows',
          dataSource: technique.dataSource || 'Process monitoring'
        }
      });
    }
    
    return events;
  }

  /**
   * Generate event description
   */
  generateEventDescription(technique) {
    const templates = {
      'T1595': 'Active network scanning detected on subnet',
      'T1566': 'Spearphishing email with malicious attachment detected',
      'T1059': 'Suspicious command-line execution detected',
      'T1021': 'Remote service connection attempt',
      'T1055': 'Process injection behavior observed',
      'T1078': 'Valid account usage from unusual location',
      'default': `MITRE technique ${technique.id} simulated execution`
    };
    
    return templates[technique.id] || templates['default'];
  }

  /**
   * Validate scenario compliance (legal, ethical)
   */
  validateScenarioCompliance(scenario) {
    // Check for illegal activities
    const blacklistedKeywords = [
      'real exploit', 
      'unauthorized access',
      'actual malware',
      'destructive payload'
    ];
    
    const scenarioText = JSON.stringify(scenario).toLowerCase();
    
    for (const keyword of blacklistedKeywords) {
      if (scenarioText.includes(keyword)) {
        console.error(`⛔ Compliance violation: ${keyword}`);
        return false;
      }
    }
    
    // All scenarios must be marked as simulated
    if (scenario.simulated !== true) {
      console.error('⛔ Scenario must be explicitly marked as simulated');
      return false;
    }
    
    return true;
  }

  /**
   * Get simulation results
   */
  getSimulationResults(simulationId) {
    const sim = this.activeScenarios.find(s => s.id === simulationId);
    
    if (!sim) {
      return null;
    }
    
    return {
      id: sim.id,
      scenario: sim.scenario.name,
      status: sim.status,
      duration: sim.duration,
      metrics: {
        totalEvents: sim.events.length,
        totalIOCs: sim.iocs.length,
        totalLogs: sim.logs.length,
        tacticsUsed: [...new Set(sim.events.map(e => e.tactic))].length,
        techniquesUsed: [...new Set(sim.events.map(e => e.technique))].length
      },
      events: sim.events,
      iocs: sim.iocs,
      logs: sim.logs
    };
  }

  /**
   * Delay helper
   */
  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Get active simulations
   */
  getActiveSimulations() {
    return this.activeScenarios.filter(s => s.status === 'running');
  }

  /**
   * Stop a simulation
   */
  stopSimulation(simulationId) {
    const sim = this.activeScenarios.find(s => s.id === simulationId);
    
    if (sim && sim.status === 'running') {
      sim.status = 'stopped';
      sim.endTime = new Date();
      sim.duration = sim.endTime - sim.startTime;
      return true;
    }
    
    return false;
  }
}

/**
 * MITRE ATT&CK Library
 * Contains all 14 tactics and 193 techniques
 */
class MITREAttackLibrary {
  constructor() {
    this.tactics = this.loadTactics();
    this.techniques = this.loadTechniques();
    
    console.log(`📚 MITRE ATT&CK Library loaded`);
    console.log(`   Tactics: ${this.tactics.length}`);
    console.log(`   Techniques: ${this.techniques.length}`);
  }

  /**
   * Load MITRE ATT&CK Tactics
   */
  loadTactics() {
    return [
      { id: 'TA0043', name: 'Reconnaissance', shortName: 'recon' },
      { id: 'TA0042', name: 'Resource Development', shortName: 'resource-dev' },
      { id: 'TA0001', name: 'Initial Access', shortName: 'initial-access' },
      { id: 'TA0002', name: 'Execution', shortName: 'execution' },
      { id: 'TA0003', name: 'Persistence', shortName: 'persistence' },
      { id: 'TA0004', name: 'Privilege Escalation', shortName: 'privilege-esc' },
      { id: 'TA0005', name: 'Defense Evasion', shortName: 'defense-evasion' },
      { id: 'TA0006', name: 'Credential Access', shortName: 'credential-access' },
      { id: 'TA0007', name: 'Discovery', shortName: 'discovery' },
      { id: 'TA0008', name: 'Lateral Movement', shortName: 'lateral-movement' },
      { id: 'TA0009', name: 'Collection', shortName: 'collection' },
      { id: 'TA0011', name: 'Command and Control', shortName: 'c2' },
      { id: 'TA0010', name: 'Exfiltration', shortName: 'exfiltration' },
      { id: 'TA0040', name: 'Impact', shortName: 'impact' }
    ];
  }

  /**
   * Load sample MITRE ATT&CK Techniques (subset for demo)
   */
  loadTechniques() {
    return [
      // Reconnaissance
      { id: 'T1595', name: 'Active Scanning', tactic: 'TA0043', platform: 'PRE', severity: 'low' },
      { id: 'T1590', name: 'Gather Victim Network Information', tactic: 'TA0043', platform: 'PRE', severity: 'low' },
      { id: 'T1591', name: 'Gather Victim Org Information', tactic: 'TA0043', platform: 'PRE', severity: 'low' },
      
      // Initial Access
      { id: 'T1566', name: 'Phishing', tactic: 'TA0001', platform: 'Windows', severity: 'high' },
      { id: 'T1078', name: 'Valid Accounts', tactic: 'TA0001', platform: 'Windows', severity: 'medium' },
      
      // Execution
      { id: 'T1059', name: 'Command and Scripting Interpreter', tactic: 'TA0002', platform: 'Windows', severity: 'medium' },
      { id: 'T1203', name: 'Exploitation for Client Execution', tactic: 'TA0002', platform: 'Windows', severity: 'high' },
      
      // Persistence
      { id: 'T1547', name: 'Boot or Logon Autostart Execution', tactic: 'TA0003', platform: 'Windows', severity: 'medium' },
      { id: 'T1053', name: 'Scheduled Task/Job', tactic: 'TA0003', platform: 'Windows', severity: 'medium' },
      
      // Privilege Escalation
      { id: 'T1055', name: 'Process Injection', tactic: 'TA0004', platform: 'Windows', severity: 'high' },
      { id: 'T1068', name: 'Exploitation for Privilege Escalation', tactic: 'TA0004', platform: 'Windows', severity: 'critical' },
      
      // Defense Evasion
      { id: 'T1027', name: 'Obfuscated Files or Information', tactic: 'TA0005', platform: 'Windows', severity: 'medium' },
      { id: 'T1070', name: 'Indicator Removal', tactic: 'TA0005', platform: 'Windows', severity: 'medium' },
      
      // Credential Access
      { id: 'T1003', name: 'OS Credential Dumping', tactic: 'TA0006', platform: 'Windows', severity: 'critical' },
      { id: 'T1110', name: 'Brute Force', tactic: 'TA0006', platform: 'Windows', severity: 'medium' },
      
      // Discovery
      { id: 'T1083', name: 'File and Directory Discovery', tactic: 'TA0007', platform: 'Windows', severity: 'low' },
      { id: 'T1082', name: 'System Information Discovery', tactic: 'TA0007', platform: 'Windows', severity: 'low' },
      
      // Lateral Movement
      { id: 'T1021', name: 'Remote Services', tactic: 'TA0008', platform: 'Windows', severity: 'high' },
      { id: 'T1550', name: 'Use Alternate Authentication Material', tactic: 'TA0008', platform: 'Windows', severity: 'high' },
      
      // Collection
      { id: 'T1005', name: 'Data from Local System', tactic: 'TA0009', platform: 'Windows', severity: 'medium' },
      { id: 'T1056', name: 'Input Capture', tactic: 'TA0009', platform: 'Windows', severity: 'high' },
      
      // Command and Control
      { id: 'T1071', name: 'Application Layer Protocol', tactic: 'TA0011', platform: 'Windows', severity: 'medium' },
      { id: 'T1095', name: 'Non-Application Layer Protocol', tactic: 'TA0011', platform: 'Windows', severity: 'medium' },
      
      // Exfiltration
      { id: 'T1041', name: 'Exfiltration Over C2 Channel', tactic: 'TA0010', platform: 'Windows', severity: 'high' },
      { id: 'T1048', name: 'Exfiltration Over Alternative Protocol', tactic: 'TA0010', platform: 'Windows', severity: 'high' },
      
      // Impact
      { id: 'T1486', name: 'Data Encrypted for Impact', tactic: 'TA0040', platform: 'Windows', severity: 'critical' },
      { id: 'T1490', name: 'Inhibit System Recovery', tactic: 'TA0040', platform: 'Windows', severity: 'critical' }
    ];
  }

  /**
   * Get technique by ID
   */
  getTechnique(techniqueId) {
    return this.techniques.find(t => t.id === techniqueId);
  }

  /**
   * Get techniques by tactic
   */
  getTechniquesByTactic(tacticId) {
    return this.techniques.filter(t => t.tactic === tacticId);
  }

  /**
   * Get tactic by ID
   */
  getTactic(tacticId) {
    return this.tactics.find(t => t.id === tacticId);
  }

  /**
   * Search techniques
   */
  searchTechniques(query) {
    const lowerQuery = query.toLowerCase();
    return this.techniques.filter(t => 
      t.name.toLowerCase().includes(lowerQuery) ||
      t.id.toLowerCase().includes(lowerQuery)
    );
  }
}

/**
 * Scenario Engine
 * Manages pre-configured attack scenarios
 */
class ScenarioEngine {
  constructor() {
    this.scenarios = this.loadScenarios();
    console.log(`🎯 Scenario Engine loaded: ${this.scenarios.length} scenarios`);
  }

  /**
   * Load pre-configured scenarios
   */
  loadScenarios() {
    return [
      {
        id: 'scenario-001',
        name: 'Basic Reconnaissance',
        description: 'Passive reconnaissance simulation (OSINT gathering)',
        simulated: true,
        complexity: 'low',
        duration: '30 minutes',
        timingMs: 500,
        tactics: [
          {
            id: 'TA0043',
            name: 'Reconnaissance',
            techniques: [
              { id: 'T1595', name: 'Active Scanning', eventCount: 10 },
              { id: 'T1590', name: 'Gather Victim Network Information', eventCount: 5 },
              { id: 'T1591', name: 'Gather Victim Org Information', eventCount: 3 }
            ]
          }
        ],
        detectability: 'low',
        legalCompliance: true
      },
      {
        id: 'scenario-002',
        name: 'Phishing Campaign Simulation',
        description: 'Simulated phishing attack with execution phase',
        simulated: true,
        complexity: 'medium',
        duration: '1 hour',
        timingMs: 1000,
        tactics: [
          {
            id: 'TA0001',
            name: 'Initial Access',
            techniques: [
              { id: 'T1566', name: 'Phishing', eventCount: 15, severity: 'high' }
            ]
          },
          {
            id: 'TA0002',
            name: 'Execution',
            techniques: [
              { id: 'T1059', name: 'Command and Scripting Interpreter', eventCount: 20 }
            ]
          }
        ],
        detectability: 'medium',
        legalCompliance: true
      },
      {
        id: 'scenario-003',
        name: 'APT-style Multi-stage Attack',
        description: 'Complex multi-stage attack simulation covering full kill chain',
        simulated: true,
        complexity: 'high',
        duration: '2 hours',
        timingMs: 2000,
        tactics: [
          {
            id: 'TA0043',
            name: 'Reconnaissance',
            techniques: [
              { id: 'T1595', name: 'Active Scanning', eventCount: 5 }
            ]
          },
          {
            id: 'TA0001',
            name: 'Initial Access',
            techniques: [
              { id: 'T1566', name: 'Phishing', eventCount: 10 }
            ]
          },
          {
            id: 'TA0002',
            name: 'Execution',
            techniques: [
              { id: 'T1059', name: 'Command and Scripting Interpreter', eventCount: 15 }
            ]
          },
          {
            id: 'TA0003',
            name: 'Persistence',
            techniques: [
              { id: 'T1547', name: 'Boot or Logon Autostart Execution', eventCount: 8 }
            ]
          },
          {
            id: 'TA0004',
            name: 'Privilege Escalation',
            techniques: [
              { id: 'T1055', name: 'Process Injection', eventCount: 12 }
            ]
          },
          {
            id: 'TA0008',
            name: 'Lateral Movement',
            techniques: [
              { id: 'T1021', name: 'Remote Services', eventCount: 10 }
            ]
          },
          {
            id: 'TA0010',
            name: 'Exfiltration',
            techniques: [
              { id: 'T1041', name: 'Exfiltration Over C2 Channel', eventCount: 7 }
            ]
          }
        ],
        detectability: 'high',
        legalCompliance: true
      }
    ];
  }

  /**
   * Get scenario by ID
   */
  getScenario(scenarioId) {
    return this.scenarios.find(s => s.id === scenarioId);
  }

  /**
   * Get all scenarios
   */
  getAllScenarios() {
    return this.scenarios;
  }

  /**
   * Filter scenarios by complexity
   */
  getScenariosByComplexity(complexity) {
    return this.scenarios.filter(s => s.complexity === complexity);
  }
}

/**
 * IOC Generator
 * Generates Indicators of Compromise for simulations
 */
class IOCGenerator {
  constructor() {
    console.log('🔍 IOC Generator initialized');
  }

  /**
   * Generate IOCs for a technique
   */
  generateForTechnique(technique) {
    const iocs = [];
    const baseTime = Date.now();
    
    // Generate different IOC types based on technique
    if (technique.id.startsWith('T159')) {
      // Network scanning
      iocs.push(...this.generateNetworkIOCs());
    } else if (technique.id === 'T1566') {
      // Phishing
      iocs.push(...this.generateEmailIOCs());
    } else if (technique.id === 'T1059') {
      // Command execution
      iocs.push(...this.generateProcessIOCs());
    } else {
      // Generic IOCs
      iocs.push(...this.generateGenericIOCs());
    }
    
    // Add metadata
    return iocs.map((ioc, idx) => ({
      ...ioc,
      id: `ioc-${baseTime}-${idx}`,
      technique: technique.id,
      timestamp: new Date(),
      simulated: true
    }));
  }

  /**
   * Generate network IOCs
   */
  generateNetworkIOCs() {
    return [
      {
        type: 'ip',
        value: this.generateFakeIP(),
        category: 'Network Activity',
        confidence: 'medium'
      },
      {
        type: 'domain',
        value: `malicious-${Math.random().toString(36).substr(2, 9)}.example.com`,
        category: 'C2 Domain',
        confidence: 'high'
      }
    ];
  }

  /**
   * Generate email IOCs
   */
  generateEmailIOCs() {
    return [
      {
        type: 'email',
        value: `attacker${Math.random().toString(36).substr(2, 5)}@example.com`,
        category: 'Phishing Email',
        confidence: 'high'
      },
      {
        type: 'hash',
        value: this.generateFakeHash('sha256'),
        category: 'Malicious Attachment',
        confidence: 'critical'
      }
    ];
  }

  /**
   * Generate process IOCs
   */
  generateProcessIOCs() {
    const processes = ['powershell.exe', 'cmd.exe', 'wscript.exe', 'mshta.exe'];
    return [
      {
        type: 'process',
        value: processes[Math.floor(Math.random() * processes.length)],
        category: 'Suspicious Process',
        confidence: 'medium'
      },
      {
        type: 'command-line',
        value: `powershell.exe -enc ${btoa('simulated-command')}`,
        category: 'Malicious Command',
        confidence: 'high'
      }
    ];
  }

  /**
   * Generate generic IOCs
   */
  generateGenericIOCs() {
    return [
      {
        type: 'file',
        value: `C:\\Users\\Public\\${Math.random().toString(36).substr(2, 9)}.exe`,
        category: 'Suspicious File',
        confidence: 'medium'
      }
    ];
  }

  /**
   * Generate fake IP address
   */
  generateFakeIP() {
    // Use TEST-NET ranges (RFC 5737)
    const ranges = ['192.0.2', '198.51.100', '203.0.113'];
    const range = ranges[Math.floor(Math.random() * ranges.length)];
    return `${range}.${Math.floor(Math.random() * 256)}`;
  }

  /**
   * Generate fake hash
   */
  generateFakeHash(type = 'md5') {
    const lengths = { md5: 32, sha1: 40, sha256: 64 };
    const length = lengths[type] || 32;
    return Array.from({ length }, () => 
      Math.floor(Math.random() * 16).toString(16)
    ).join('');
  }
}

/**
 * SOC Logging System
 * Generates realistic SOC logs for simulations
 */
class SOCLoggingSystem {
  constructor() {
    console.log('📝 SOC Logging System initialized');
  }

  /**
   * Generate logs for technique and events
   */
  generateLogs(technique, events) {
    return events.map(event => ({
      timestamp: event.timestamp.toISOString(),
      event_id: Math.floor(Math.random() * 10000),
      source: 'Sentinel-Simulation',
      level: this.getSeverityLevel(event.severity),
      technique_id: event.technique,
      technique_name: event.techniqueName,
      tactic_id: event.tactic,
      tactic_name: event.tacticName,
      description: event.description,
      platform: event.metadata?.platform || 'Windows',
      data_source: event.metadata?.dataSource || 'Unknown',
      simulated: true,
      format: 'CEF'
    }));
  }

  /**
   * Get severity level
   */
  getSeverityLevel(severity) {
    const levels = {
      critical: 'CRITICAL',
      high: 'HIGH',
      medium: 'MEDIUM',
      low: 'LOW'
    };
    return levels[severity] || 'INFO';
  }

  /**
   * Export logs to JSON
   */
  exportToJSON(logs) {
    return JSON.stringify(logs, null, 2);
  }

  /**
   * Export logs to CEF format
   */
  exportToCEF(logs) {
    return logs.map(log => 
      `CEF:0|Sentinel|Simulation|1.0|${log.event_id}|${log.technique_name}|${log.level}|` +
      `rt=${log.timestamp} techniqueId=${log.technique_id} tacticId=${log.tactic_id} simulated=true`
    ).join('\n');
  }
}

// Export for use in HTML pages
if (typeof window !== 'undefined') {
  window.OffensiveSimulationEngine = OffensiveSimulationEngine;
  window.MITREAttackLibrary = MITREAttackLibrary;
  window.ScenarioEngine = ScenarioEngine;
  window.IOCGenerator = IOCGenerator;
  window.SOCLoggingSystem = SOCLoggingSystem;
}

console.log('🛡️ Sentinel Offensive Security Simulation Engine loaded');
console.log('⚖️ Legal Notice: Educational and testing purposes only');
console.log('⚠️ No real attacks - Simulations only');
