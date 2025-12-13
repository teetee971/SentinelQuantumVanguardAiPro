/**
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Phase E - Feature Flags System
 * 
 * Global feature flags for controlled activation
 * ALL features are OFF by default
 * 
 * DO NOT MODIFY THESE VALUES WITHOUT PROPER AUTHORIZATION
 */

export const FEATURE_FLAGS = {
  // Backend API - Real backend services
  FEATURE_BACKEND: false,
  
  // AI Agents - Autonomous defense agents
  FEATURE_AGENTS: false,
  
  // Live Logs - Real-time log streaming
  FEATURE_LOGS_LIVE: false,
  
  // Additional safety flags
  FEATURE_QUANTUM_DEFENSE: false,
  FEATURE_THREAT_SCANNER: false,
  FEATURE_DDOS_PROTECTION: false,
  FEATURE_ADMIN_CONSOLE: false,
  
  // Phase E metadata
  PHASE: 'E',
  STATUS: 'ARMABLE_NOT_ARMED',
  ACTIVATION_READY: true,
  ACTIVATION_ENABLED: false
};

/**
 * Check if a feature is enabled
 * @param {string} featureName - Name of the feature flag
 * @returns {boolean} - Whether the feature is enabled
 */
export function isFeatureEnabled(featureName) {
  return FEATURE_FLAGS[featureName] === true;
}

/**
 * Get current system status
 * @returns {object} - System status object
 */
export function getSystemStatus() {
  return {
    phase: FEATURE_FLAGS.PHASE,
    status: FEATURE_FLAGS.STATUS,
    activationReady: FEATURE_FLAGS.ACTIVATION_READY,
    activationEnabled: FEATURE_FLAGS.ACTIVATION_ENABLED,
    features: {
      backend: FEATURE_FLAGS.FEATURE_BACKEND,
      agents: FEATURE_FLAGS.FEATURE_AGENTS,
      logsLive: FEATURE_FLAGS.FEATURE_LOGS_LIVE,
      quantumDefense: FEATURE_FLAGS.FEATURE_QUANTUM_DEFENSE,
      threatScanner: FEATURE_FLAGS.FEATURE_THREAT_SCANNER,
      ddosProtection: FEATURE_FLAGS.FEATURE_DDOS_PROTECTION,
      adminConsole: FEATURE_FLAGS.FEATURE_ADMIN_CONSOLE
    }
  };
}

// Export for global access
if (typeof window !== 'undefined') {
  window.SENTINEL_FEATURE_FLAGS = FEATURE_FLAGS;
  window.SENTINEL_isFeatureEnabled = isFeatureEnabled;
  window.SENTINEL_getSystemStatus = getSystemStatus;
}
