/**
 * Sentinel Modules - Main Export
 * 
 * This file exports all Sentinel AI modules organized by category.
 */

// MODULE 7 - Infrastructure & CI/CD
export * from './infrastructure/index.js';

// MODULE 8 - Voice & Communication
export * from './voice/index.js';

// MODULE 9 - Supervision & Monitoring
export * from './monitoring/index.js';

// MODULE 10 - Self-Healing Modules
export * from './self-healing/index.js';

/**
 * Initialize all Sentinel modules
 */
export function initializeSentinelModules() {
  return {
    infrastructure: {
      infraGuard: null,
      buildPilot: null,
      firebaseDeployExecutor: null,
      cloudflarePropagateWatcher: null,
      scriptForge: null,
      zeroDowntimeSwitcher: null
    },
    voice: {
      sentinelVoiceCore: null,
      deepFakeVoiceDetection: null,
      sentinelChatAssistant: null,
      uiEmergencyFallbackAgent: null,
      touchFeedbackOptimizer: null
    },
    monitoring: {
      sentinelConsoleAdmin: null,
      liveConsoleErrorLogger: null,
      agentLatencyMonitor: null,
      aiRecoveryCommander: null,
      regressionDetectorAI: null
    },
    selfHealing: {
      serviceWorkerHealer: null,
      manifestRecoveryAgent: null,
      brokenLinkMapper: null,
      ghost404Handler: null,
      emptyStateHealer: null,
      cdnConsistencyAgent: null
    }
  };
}
