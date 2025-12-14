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
}

/**
 * Default Feature Flags - Phase B
 * 
 * CRITICAL: All features DISABLED by default
 * Transparency > Functionality
 * Security > Features
 */
export const DEFAULT_FEATURE_FLAGS: FeatureFlags = {
  // Phone Module - ALL OFF
  PHONE_CONTACTS_ACCESS: false,
  PHONE_CALL_LOG_ACCESS: false,
  PHONE_SMS_READ_ACCESS: false,
  PHONE_CALL_RECORDING: false,
  PHONE_AI_CALL_ANALYSIS: false,
  PHONE_SMART_CALL_HANDLING: false,
  PHONE_CALLER_ID_ENRICHMENT: false,
  PHONE_COUNTRY_DETECTION: false,
  PHONE_ROBOCALL_DETECTION: false,
  PHONE_DEFAULT_APP_MODE: false,
  
  // Mobile Security - ALL OFF
  SECURITY_BEHAVIORAL_ANALYSIS: false,
  SECURITY_NETWORK_ANOMALY_DETECTION: false,
  SECURITY_APP_ANOMALY_DETECTION: false,
  SECURITY_PERMISSIONS_MONITORING: false,
  
  // SOC/Console - Dashboard enabled for transparency
  SOC_DASHBOARD: true,
  SOC_MODULE_STATUS: true,
  SOC_EVENTS_JOURNAL: true,
  
  // Development
  DEBUG_LOGGING: __DEV__,
  DEVELOPMENT_MODE: __DEV__,
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
