/**
 * Sentinel Orchestrator - Main AI Modules Coordinator
 * Coordonnateur principal de tous les modules IA Sentinel
 */

// Module 7 - Infrastructure & CI/CD
import { InfraGuard } from './infrastructure/InfraGuard.js';
import { BuildPilot } from './infrastructure/BuildPilot.js';
import { FirebaseDeployExecutor } from './infrastructure/FirebaseDeployExecutor.js';
import { CloudflarePropagateWatcher } from './infrastructure/CloudflarePropagateWatcher.js';
import { ScriptForge } from './infrastructure/ScriptForge.js';
import { ZeroDowntimeSwitcher } from './infrastructure/ZeroDowntimeSwitcher.js';

// Module 8 - Voice & Communication
import { SentinelVoiceCore } from './voice/SentinelVoiceCore.js';
import { DeepFakeVoiceDetection } from './voice/DeepFakeVoiceDetection.js';
import { SentinelChatAssistant } from './voice/SentinelChatAssistant.js';
import { UIEmergencyFallbackAgent } from './voice/UIEmergencyFallbackAgent.js';
import { TouchFeedbackOptimizer } from './voice/TouchFeedbackOptimizer.js';

// Module 9 - Supervision & Monitoring
import { SentinelConsoleAdmin } from './monitoring/SentinelConsoleAdmin.js';
import { LiveConsoleErrorLogger } from './monitoring/LiveConsoleErrorLogger.js';
import { AgentLatencyMonitor } from './monitoring/AgentLatencyMonitor.js';
import { AIRecoveryCommander } from './monitoring/AIRecoveryCommander.js';
import { RegressionDetectorAI } from './monitoring/RegressionDetectorAI.js';

// Module 10 - Self-Healing
import { ServiceWorkerHealer } from './self-healing/ServiceWorkerHealer.js';
import { ManifestRecoveryAgent } from './self-healing/ManifestRecoveryAgent.js';
import { BrokenLinkMapper } from './self-healing/BrokenLinkMapper.js';
import { Ghost404Handler } from './self-healing/Ghost404Handler.js';
import { EmptyStateHealer } from './self-healing/EmptyStateHealer.js';
import { CDNConsistencyAgent } from './self-healing/CDNConsistencyAgent.js';

export class SentinelOrchestrator {
  constructor() {
    this.version = '1.0.0';
    this.status = 'active';
    this.initTime = new Date();
    
    // Initialize all modules
    this.modules = {
      // Module 7 - Infrastructure
      infrastructure: {
        infraGuard: new InfraGuard(),
        buildPilot: new BuildPilot(),
        firebaseDeployExecutor: new FirebaseDeployExecutor(),
        cloudflarePropagateWatcher: new CloudflarePropagateWatcher(),
        scriptForge: new ScriptForge(),
        zeroDowntimeSwitcher: new ZeroDowntimeSwitcher()
      },
      
      // Module 8 - Voice & Communication
      voice: {
        sentinelVoiceCore: new SentinelVoiceCore(),
        deepFakeVoiceDetection: new DeepFakeVoiceDetection(),
        sentinelChatAssistant: new SentinelChatAssistant(),
        uiEmergencyFallbackAgent: new UIEmergencyFallbackAgent(),
        touchFeedbackOptimizer: new TouchFeedbackOptimizer()
      },
      
      // Module 9 - Monitoring
      monitoring: {
        sentinelConsoleAdmin: new SentinelConsoleAdmin(),
        liveConsoleErrorLogger: new LiveConsoleErrorLogger(),
        agentLatencyMonitor: new AgentLatencyMonitor(),
        aiRecoveryCommander: new AIRecoveryCommander(),
        regressionDetectorAI: new RegressionDetectorAI()
      },
      
      // Module 10 - Self-Healing
      selfHealing: {
        serviceWorkerHealer: new ServiceWorkerHealer(),
        manifestRecoveryAgent: new ManifestRecoveryAgent(),
        brokenLinkMapper: new BrokenLinkMapper(),
        ghost404Handler: new Ghost404Handler(),
        emptyStateHealer: new EmptyStateHealer(),
        cdnConsistencyAgent: new CDNConsistencyAgent()
      }
    };
  }

  /**
   * Get status of all modules
   */
  getSystemStatus() {
    const status = {
      version: this.version,
      status: this.status,
      uptime: Date.now() - this.initTime.getTime(),
      modules: {}
    };

    for (const [category, categoryModules] of Object.entries(this.modules)) {
      status.modules[category] = {};
      for (const [moduleName, module] of Object.entries(categoryModules)) {
        status.modules[category][moduleName] = module.getStatus();
      }
    }

    return status;
  }

  /**
   * Get specific module
   */
  getModule(category, moduleName) {
    return this.modules[category]?.[moduleName];
  }

  /**
   * Run health check on all modules
   */
  async healthCheck() {
    const health = {
      timestamp: new Date(),
      healthy: true,
      details: {}
    };

    for (const [category, categoryModules] of Object.entries(this.modules)) {
      health.details[category] = {};
      for (const [moduleName, module] of Object.entries(categoryModules)) {
        try {
          const moduleStatus = module.getStatus();
          health.details[category][moduleName] = {
            healthy: moduleStatus.status === 'active',
            status: moduleStatus.status
          };
        } catch (error) {
          health.healthy = false;
          health.details[category][moduleName] = {
            healthy: false,
            error: error.message
          };
        }
      }
    }

    return health;
  }

  /**
   * Emergency recovery - activate all self-healing modules
   */
  async emergencyRecovery() {
    const results = {
      timestamp: new Date(),
      recovered: []
    };

    // Run all self-healing modules
    for (const [moduleName, module] of Object.entries(this.modules.selfHealing)) {
      try {
        let result;
        if (moduleName === 'serviceWorkerHealer') {
          result = await module.healServiceWorker();
        } else if (moduleName === 'manifestRecoveryAgent') {
          result = await module.recoverManifest();
        } else if (moduleName === 'brokenLinkMapper') {
          result = await module.scanAndFix();
        } else if (moduleName === 'ghost404Handler') {
          result = { status: 'active' };
        } else if (moduleName === 'emptyStateHealer') {
          result = { status: 'active' };
        } else if (moduleName === 'cdnConsistencyAgent') {
          result = await module.checkConsistency();
        }
        
        results.recovered.push({
          module: moduleName,
          success: true,
          result
        });
      } catch (error) {
        results.recovered.push({
          module: moduleName,
          success: false,
          error: error.message
        });
      }
    }

    // Run UI recovery
    try {
      const uiRecovery = await this.modules.voice.uiEmergencyFallbackAgent.detectAndRecover();
      results.recovered.push({
        module: 'uiEmergencyFallbackAgent',
        success: true,
        result: uiRecovery
      });
    } catch (error) {
      results.recovered.push({
        module: 'uiEmergencyFallbackAgent',
        success: false,
        error: error.message
      });
    }

    // Run AI Recovery Commander
    try {
      const aiRecovery = await this.modules.monitoring.aiRecoveryCommander.detectAndRecover();
      results.recovered.push({
        module: 'aiRecoveryCommander',
        success: true,
        result: aiRecovery
      });
    } catch (error) {
      results.recovered.push({
        module: 'aiRecoveryCommander',
        success: false,
        error: error.message
      });
    }

    return results;
  }
}

// Create singleton instance
const sentinelOrchestrator = new SentinelOrchestrator();

export default sentinelOrchestrator;
