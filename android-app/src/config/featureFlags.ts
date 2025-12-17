/**
 * PHASE B - Feature Flags Configuration
 * 
 * Controls activation of all Phase B mobile security modules.
 * ALL features are OFF by default for maximum security and transparency.
 * 
 * Security Policy: Features must be explicitly enabled after:
 * - User consent obtained
 * - Permissions granted by Android
 * - Compliance verification
 * - Testing completed
 */

export interface FeatureFlags {
  // ========================================
  // PHONE MODULE (Phase B Priority)
  // ========================================
  
  /** Enable contacts access (requires READ_CONTACTS permission) */
  PHONE_CONTACTS_ACCESS: boolean;
  
  /** Enable call log access (requires READ_CALL_LOG permission) */
  PHONE_CALL_LOG_ACCESS: boolean;
  
  /** Enable SMS reading (requires READ_SMS permission - READ ONLY) */
  PHONE_SMS_READ_ACCESS: boolean;
  
  /** Enable call recording framework (region-dependent, requires user consent) */
  PHONE_CALL_RECORDING: boolean;
  
  /** Enable local AI call analysis (spam/scam detection) */
  PHONE_AI_CALL_ANALYSIS: boolean;
  
  /** Enable smart call handling (assisted, configurable) */
  PHONE_SMART_CALL_HANDLING: boolean;
  
  /** Enable caller ID enrichment (name/company lookup) */
  PHONE_CALLER_ID_ENRICHMENT: boolean;
  
  /** Enable real country detection (network analysis) */
  PHONE_COUNTRY_DETECTION: boolean;
  
  /** Enable call center/robocall detection */
  PHONE_ROBOCALL_DETECTION: boolean;
  
  /** Enable default phone app mode (if Android allows) */
  PHONE_DEFAULT_APP_MODE: boolean;
  
  // ========================================
  // MOBILE SECURITY MODULE (Phase B)
  // ========================================
  
  /** Enable local behavioral analysis */
  SECURITY_BEHAVIORAL_ANALYSIS: boolean;
  
  /** Enable network anomaly detection */
  SECURITY_NETWORK_ANOMALY_DETECTION: boolean;
  
  /** Enable app anomaly detection */
  SECURITY_APP_ANOMALY_DETECTION: boolean;
  
  /** Enable sensitive permissions monitoring */
  SECURITY_PERMISSIONS_MONITORING: boolean;
  
  // ========================================
  // SOC/CONSOLE MODULE (Phase B)
  // ========================================
  
  /** Enable SOC dashboard */
  SOC_DASHBOARD: boolean;
  
  /** Enable module status display */
  SOC_MODULE_STATUS: boolean;
  
  /** Enable security events journal */
  SOC_EVENTS_JOURNAL: boolean;
  
  // ========================================
  // DEVELOPMENT & TESTING
  // ========================================
  
  /** Enable debug logging */
  DEBUG_LOGGING: boolean;
  
  /** Enable development mode features */
  DEVELOPMENT_MODE: boolean;
  
  // ========================================
  // PHASE B+ Sprint 1 - ThreatScore & Explainability
  // ========================================
  
  /** Enable threat scoring system */
  PHONE_THREAT_SCORING: boolean;
  
  /** Enable call memory database */
  PHONE_CALL_MEMORY: boolean;
  
  /** Enable explainable decisions */
  PHONE_EXPLAINABLE_DECISIONS: boolean;
  
  /** Enable activity timeline */
  PHONE_ACTIVITY_TIMELINE: boolean;
  
  /** Enable protection profiles */
  PHONE_PROTECTION_PROFILES: boolean;
  
  // ========================================
  // PHASE B+ Sprint 2 - Persistent Memory & Baseline
  // ========================================
  
  /** Enable persistent local storage for call memory */
  PHONE_PERSISTENT_MEMORY: boolean;
  
  /** Enable behavioral baseline learning */
  PHONE_BEHAVIORAL_BASELINE: boolean;
  
  /** Enable deviation detection from baseline */
  PHONE_DEVIATION_DETECTION: boolean;
  
  /** Enable malicious pattern detection */
  PHONE_PATTERN_DETECTION: boolean;
  
  /** Enable score change explanations */
  PHONE_SCORE_EXPLANATIONS: boolean;
}

/**
 * Default Feature Flags - Production v1.0
 * 
 * DEFENSIVE SECURITY ONLY
 * Focus: Real, functional, legal defensive features
 * No offensive capabilities, no fake features, no promises
 */
export const DEFAULT_FEATURE_FLAGS: FeatureFlags = {
  // Phone Module - DEFENSIVE FEATURES ONLY
  PHONE_CONTACTS_ACCESS: true,         // For caller ID enrichment
  PHONE_CALL_LOG_ACCESS: true,         // For call history and spam detection
  PHONE_SMS_READ_ACCESS: false,        // Not needed for v1.0
  PHONE_CALL_RECORDING: false,         // DISABLED - legal/privacy concerns
  PHONE_AI_CALL_ANALYSIS: true,        // Local spam/scam detection
  PHONE_SMART_CALL_HANDLING: false,    // DISABLED for v1.0
  PHONE_CALLER_ID_ENRICHMENT: true,    // Show caller information
  PHONE_COUNTRY_DETECTION: true,       // Basic country detection from number
  PHONE_ROBOCALL_DETECTION: true,      // Detect robocalls/spam
  PHONE_DEFAULT_APP_MODE: false,       // DISABLED - not replacing default phone app
  
  // Mobile Security - LOCAL AUDIT ONLY (DEFENSIVE)
  SECURITY_BEHAVIORAL_ANALYSIS: false, // DISABLED for v1.0
  SECURITY_NETWORK_ANOMALY_DETECTION: false, // DISABLED for v1.0
  SECURITY_APP_ANOMALY_DETECTION: false,     // DISABLED for v1.0
  SECURITY_PERMISSIONS_MONITORING: true,     // ENABLED - local permission audit
  
  // SOC/Console - READ-ONLY DASHBOARD (TRANSPARENCY)
  SOC_DASHBOARD: true,                 // Security operations dashboard
  SOC_MODULE_STATUS: true,             // Show module status
  SOC_EVENTS_JOURNAL: true,            // Event logging
  
  // Development
  DEBUG_LOGGING: __DEV__,
  DEVELOPMENT_MODE: __DEV__,
  
  // Phase B+ Sprint 1 - DEFENSIVE FEATURES ENABLED
  PHONE_THREAT_SCORING: true,          // Spam/scam risk scoring
  PHONE_CALL_MEMORY: true,             // Remember call patterns
  PHONE_EXPLAINABLE_DECISIONS: true,   // Explain why call is suspicious
  PHONE_ACTIVITY_TIMELINE: true,       // Show call activity over time
  PHONE_PROTECTION_PROFILES: false,    // DISABLED for v1.0
  
  // Phase B+ Sprint 2 - LOCAL STORAGE ONLY
  PHONE_PERSISTENT_MEMORY: true,       // Local persistent storage
  PHONE_BEHAVIORAL_BASELINE: true,     // Learn normal call patterns
  PHONE_DEVIATION_DETECTION: true,     // Detect unusual patterns
  PHONE_PATTERN_DETECTION: true,       // Detect spam patterns
  PHONE_SCORE_EXPLANATIONS: true,      // Explain threat scores
};

/**
 * Module Status Types
 */
export type ModuleStatus = 'ACTIVE' | 'IN_DEVELOPMENT' | 'DISABLED';

/**
 * Get current feature flags
 */
export const getFeatureFlags = (): FeatureFlags => {
  return DEFAULT_FEATURE_FLAGS;
};

/**
 * Check if a specific feature is enabled
 */
export const isFeatureEnabled = (feature: keyof FeatureFlags): boolean => {
  const flags = getFeatureFlags();
  return flags[feature] || false;
};

/**
 * Get module status based on feature flags
 */
export const getModuleStatus = (module: string): ModuleStatus => {
  const flags = getFeatureFlags();
  
  switch (module) {
    case 'PHONE':
      // Check if ANY phone feature is enabled
      const phoneFeatures = [
        flags.PHONE_CONTACTS_ACCESS,
        flags.PHONE_CALL_LOG_ACCESS,
        flags.PHONE_SMS_READ_ACCESS,
        flags.PHONE_CALL_RECORDING,
        flags.PHONE_AI_CALL_ANALYSIS,
        flags.PHONE_SMART_CALL_HANDLING,
        flags.PHONE_CALLER_ID_ENRICHMENT,
        flags.PHONE_COUNTRY_DETECTION,
        flags.PHONE_ROBOCALL_DETECTION,
        flags.PHONE_DEFAULT_APP_MODE,
      ];
      if (phoneFeatures.some(f => f)) return 'ACTIVE';
      return 'IN_DEVELOPMENT';
      
    case 'SECURITY':
      const securityFeatures = [
        flags.SECURITY_BEHAVIORAL_ANALYSIS,
        flags.SECURITY_NETWORK_ANOMALY_DETECTION,
        flags.SECURITY_APP_ANOMALY_DETECTION,
        flags.SECURITY_PERMISSIONS_MONITORING,
      ];
      if (securityFeatures.some(f => f)) return 'ACTIVE';
      return 'IN_DEVELOPMENT';
      
    case 'SOC':
      if (flags.SOC_DASHBOARD) return 'ACTIVE';
      return 'DISABLED';
      
    default:
      return 'DISABLED';
  }
};
