/**
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Phase F - Advanced Feature Flags System
 * 
 * Global feature flags for controlled activation
 * ALL features are OFF by default
 * 
 * DO NOT MODIFY THESE VALUES WITHOUT PROPER AUTHORIZATION
 */

export const FEATURE_FLAGS = {
  // ============================================
  // BACKEND SERVICES
  // ============================================
  
  // Backend API - Real backend services
  FEATURE_BACKEND: false,
  FEATURE_BACKEND_READ_ONLY: true,  // Health/Status endpoints only
  FEATURE_BACKEND_WRITE: false,     // POST/PUT/DELETE operations
  
  // ============================================
  // AI AGENTS - Progressive States
  // ============================================
  
  // Global agents control
  FEATURE_AGENTS: false,
  
  // Agent states: DORMANT → SANDBOX → MONITOR → ARMED
  AGENT_NETWORK_GUARDIAN: 'DORMANT',
  AGENT_PEGASUS_SCAN: 'DORMANT',
  AGENT_ANTI_FRAUD: 'DORMANT',
  AGENT_PRIVACY_GUARDIAN: 'DORMANT',
  AGENT_ROOTKIT_SCANNER: 'DORMANT',
  AGENT_CLOUD_SYNC: 'DORMANT',
  
  // ============================================
  // LOGGING & MONITORING
  // ============================================
  
  // Live Logs - Real-time log streaming
  FEATURE_LOGS_LIVE: false,
  FEATURE_LOGS_READ_ONLY: true,     // Read-only access to logs
  FEATURE_LOGS_EXPORT: false,       // Export logs capability
  
  // ============================================
  // MOBILE & BUILDS
  // ============================================
  
  // Android release mode (debug by default)
  FEATURE_ANDROID_RELEASE: false,
  FEATURE_ANDROID_AUTO_UPDATE: false,
  
  // ============================================
  // SECURITY FEATURES
  // ============================================
  
  // Additional security features
  FEATURE_QUANTUM_DEFENSE: false,
  FEATURE_THREAT_SCANNER: false,
  FEATURE_DDOS_PROTECTION: false,
  FEATURE_ADMIN_CONSOLE: false,
  FEATURE_AUDIT_LOG: true,          // Always enabled for compliance
  
  // ============================================
  // EMERGENCY CONTROLS
  // ============================================
  
  // Emergency shutdown capability
  EMERGENCY_SHUTDOWN: false,
  KILL_SWITCH_ACTIVE: false,
  
  // ============================================
  // METADATA
  // ============================================
  
  // Phase & Status
  PHASE: 'F',
  STATUS: 'PRO_READY',
  ACTIVATION_READY: true,
  ACTIVATION_ENABLED: false,
  MODE: 'CONTROLLED',
  
  // Versioning
  VERSION: '2.0.0-pro',
  BUILD_DATE: new Date().toISOString(),
  
  // Audit trail
  LAST_MODIFIED: new Date().toISOString(),
  LAST_MODIFIED_BY: 'SYSTEM'
};

/**
 * Check if a feature is enabled
 * @param {string} featureName - Name of the feature flag
 * @returns {boolean} - Whether the feature is enabled
 */
export function isFeatureEnabled(featureName) {
  // Emergency shutdown overrides everything
  if (FEATURE_FLAGS.EMERGENCY_SHUTDOWN || FEATURE_FLAGS.KILL_SWITCH_ACTIVE) {
    // Only audit log remains active during emergency
    if (featureName === 'FEATURE_AUDIT_LOG') return true;
    return false;
  }
  
  return FEATURE_FLAGS[featureName] === true;
}

/**
 * Get agent state
 * @param {string} agentName - Name of the agent
 * @returns {string} - Agent state (DORMANT, SANDBOX, MONITOR, ARMED)
 */
export function getAgentState(agentName) {
  if (FEATURE_FLAGS.EMERGENCY_SHUTDOWN || FEATURE_FLAGS.KILL_SWITCH_ACTIVE) {
    return 'DORMANT';
  }
  
  if (!FEATURE_FLAGS.FEATURE_AGENTS) {
    return 'DORMANT';
  }
  
  const agentKey = `AGENT_${agentName.toUpperCase().replace(/-/g, '_')}`;
  return FEATURE_FLAGS[agentKey] || 'DORMANT';
}

/**
 * Check if backend is in read-only mode
 * @returns {boolean}
 */
export function isBackendReadOnly() {
  if (!FEATURE_FLAGS.FEATURE_BACKEND) return true;
  return FEATURE_FLAGS.FEATURE_BACKEND_READ_ONLY && !FEATURE_FLAGS.FEATURE_BACKEND_WRITE;
}

/**
 * Get current system status
 * @returns {object} - System status object
 */
export function getSystemStatus() {
  return {
    phase: FEATURE_FLAGS.PHASE,
    status: FEATURE_FLAGS.STATUS,
    mode: FEATURE_FLAGS.MODE,
    version: FEATURE_FLAGS.VERSION,
    activationReady: FEATURE_FLAGS.ACTIVATION_READY,
    activationEnabled: FEATURE_FLAGS.ACTIVATION_ENABLED,
    emergencyShutdown: FEATURE_FLAGS.EMERGENCY_SHUTDOWN,
    killSwitchActive: FEATURE_FLAGS.KILL_SWITCH_ACTIVE,
    backend: {
      enabled: FEATURE_FLAGS.FEATURE_BACKEND,
      readOnly: isBackendReadOnly(),
      writeEnabled: FEATURE_FLAGS.FEATURE_BACKEND_WRITE
    },
    agents: {
      enabled: FEATURE_FLAGS.FEATURE_AGENTS,
      networkGuardian: FEATURE_FLAGS.AGENT_NETWORK_GUARDIAN,
      pegasusScan: FEATURE_FLAGS.AGENT_PEGASUS_SCAN,
      antiFraud: FEATURE_FLAGS.AGENT_ANTI_FRAUD,
      privacyGuardian: FEATURE_FLAGS.AGENT_PRIVACY_GUARDIAN,
      rootkitScanner: FEATURE_FLAGS.AGENT_ROOTKIT_SCANNER,
      cloudSync: FEATURE_FLAGS.AGENT_CLOUD_SYNC
    },
    logs: {
      liveEnabled: FEATURE_FLAGS.FEATURE_LOGS_LIVE,
      readOnly: FEATURE_FLAGS.FEATURE_LOGS_READ_ONLY,
      exportEnabled: FEATURE_FLAGS.FEATURE_LOGS_EXPORT
    },
    mobile: {
      releaseMode: FEATURE_FLAGS.FEATURE_ANDROID_RELEASE,
      autoUpdate: FEATURE_FLAGS.FEATURE_ANDROID_AUTO_UPDATE
    },
    security: {
      quantumDefense: FEATURE_FLAGS.FEATURE_QUANTUM_DEFENSE,
      threatScanner: FEATURE_FLAGS.FEATURE_THREAT_SCANNER,
      ddosProtection: FEATURE_FLAGS.FEATURE_DDOS_PROTECTION,
      adminConsole: FEATURE_FLAGS.FEATURE_ADMIN_CONSOLE,
      auditLog: FEATURE_FLAGS.FEATURE_AUDIT_LOG
    },
    buildDate: FEATURE_FLAGS.BUILD_DATE,
    lastModified: FEATURE_FLAGS.LAST_MODIFIED
  };
}

/**
 * Emergency shutdown - disable all features except audit
 * @returns {object} - Shutdown confirmation
 */
export function emergencyShutdown() {
  FEATURE_FLAGS.EMERGENCY_SHUTDOWN = true;
  FEATURE_FLAGS.KILL_SWITCH_ACTIVE = true;
  FEATURE_FLAGS.LAST_MODIFIED = new Date().toISOString();
  FEATURE_FLAGS.LAST_MODIFIED_BY = 'EMERGENCY_SHUTDOWN';
  
  // Log to audit
  console.error('[EMERGENCY] KILL SWITCH ACTIVATED - All systems shutdown');
  
  return {
    success: true,
    timestamp: new Date().toISOString(),
    message: 'Emergency shutdown activated - All features disabled',
    auditLogActive: true
  };
}

/**
 * Restore from emergency shutdown
 * @returns {object} - Restore confirmation
 */
export function restoreFromEmergency() {
  FEATURE_FLAGS.EMERGENCY_SHUTDOWN = false;
  FEATURE_FLAGS.KILL_SWITCH_ACTIVE = false;
  FEATURE_FLAGS.LAST_MODIFIED = new Date().toISOString();
  FEATURE_FLAGS.LAST_MODIFIED_BY = 'RESTORE_FROM_EMERGENCY';
  
  console.info('[RECOVERY] System restored from emergency shutdown');
  
  return {
    success: true,
    timestamp: new Date().toISOString(),
    message: 'System restored - Feature flags returned to previous state'
  };
}

// Export for global access
if (typeof window !== 'undefined') {
  window.SENTINEL_FEATURE_FLAGS = FEATURE_FLAGS;
  window.SENTINEL_isFeatureEnabled = isFeatureEnabled;
  window.SENTINEL_getAgentState = getAgentState;
  window.SENTINEL_isBackendReadOnly = isBackendReadOnly;
  window.SENTINEL_getSystemStatus = getSystemStatus;
  window.SENTINEL_emergencyShutdown = emergencyShutdown;
  window.SENTINEL_restoreFromEmergency = restoreFromEmergency;
}
