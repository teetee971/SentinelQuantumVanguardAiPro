/**
 * Sentinel Quantum Vanguard AI Pro
 * Runtime security controls.
 *
 * Conservative defaults: write operations and optional capabilities remain
 * disabled until a real implementation and validation path exists.
 *
 * This module is a client-side control surface, not a security boundary.
 * Server-side authorization must never depend on these flags.
 */

const BASE_FEATURE_FLAGS = Object.freeze({
  FEATURE_BACKEND: false,
  FEATURE_BACKEND_READ_ONLY: true,
  FEATURE_BACKEND_WRITE: false,

  FEATURE_LOGS_LIVE: false,
  FEATURE_LOGS_READ_ONLY: true,
  FEATURE_LOGS_EXPORT: false,

  FEATURE_ANDROID_RELEASE: false,
  FEATURE_ANDROID_AUTO_UPDATE: false,

  FEATURE_QUANTUM_DEFENSE: false,
  FEATURE_THREAT_SCANNER: false,
  FEATURE_DDOS_PROTECTION: false,
  FEATURE_ADMIN_CONSOLE: false,
  FEATURE_AUDIT_LOG: true,

  VERSION: '2.0.0-pro',
  STATUS: 'DEFENSIVE_PLATFORM',
  MODE: 'CONTROLLED'
});

const runtimeState = {
  emergencyShutdown: false,
  killSwitchActive: false
};

export const FEATURE_FLAGS = Object.freeze({
  ...BASE_FEATURE_FLAGS,
  get EMERGENCY_SHUTDOWN() {
    return runtimeState.emergencyShutdown;
  },
  get KILL_SWITCH_ACTIVE() {
    return runtimeState.killSwitchActive;
  }
});

export function isFeatureEnabled(featureName) {
  if (runtimeState.emergencyShutdown || runtimeState.killSwitchActive) {
    return featureName === 'FEATURE_AUDIT_LOG';
  }
  return BASE_FEATURE_FLAGS[featureName] === true;
}

export function isBackendReadOnly() {
  if (!BASE_FEATURE_FLAGS.FEATURE_BACKEND) return true;
  return BASE_FEATURE_FLAGS.FEATURE_BACKEND_READ_ONLY && !BASE_FEATURE_FLAGS.FEATURE_BACKEND_WRITE;
}

export function getSystemStatus() {
  return {
    status: BASE_FEATURE_FLAGS.STATUS,
    mode: BASE_FEATURE_FLAGS.MODE,
    version: BASE_FEATURE_FLAGS.VERSION,
    emergencyShutdown: runtimeState.emergencyShutdown,
    killSwitchActive: runtimeState.killSwitchActive,
    backend: {
      enabled: BASE_FEATURE_FLAGS.FEATURE_BACKEND,
      readOnly: isBackendReadOnly(),
      writeEnabled: BASE_FEATURE_FLAGS.FEATURE_BACKEND_WRITE
    },
    logs: {
      liveEnabled: BASE_FEATURE_FLAGS.FEATURE_LOGS_LIVE,
      readOnly: BASE_FEATURE_FLAGS.FEATURE_LOGS_READ_ONLY,
      exportEnabled: BASE_FEATURE_FLAGS.FEATURE_LOGS_EXPORT
    },
    mobile: {
      releaseMode: BASE_FEATURE_FLAGS.FEATURE_ANDROID_RELEASE,
      autoUpdate: BASE_FEATURE_FLAGS.FEATURE_ANDROID_AUTO_UPDATE
    },
    security: {
      quantumDefense: BASE_FEATURE_FLAGS.FEATURE_QUANTUM_DEFENSE,
      threatScanner: BASE_FEATURE_FLAGS.FEATURE_THREAT_SCANNER,
      ddosProtection: BASE_FEATURE_FLAGS.FEATURE_DDOS_PROTECTION,
      adminConsole: BASE_FEATURE_FLAGS.FEATURE_ADMIN_CONSOLE,
      auditLog: BASE_FEATURE_FLAGS.FEATURE_AUDIT_LOG
    }
  };
}

export function emergencyShutdown() {
  runtimeState.emergencyShutdown = true;
  runtimeState.killSwitchActive = true;
  console.error('[EMERGENCY] Sentinel kill switch activated');
  return {
    success: true,
    timestamp: new Date().toISOString(),
    message: 'Emergency shutdown activated',
    auditLogActive: true
  };
}

export function verifyZeroTrustCompliance() {
  const checks = {
    backendReadOnlyMode:
      BASE_FEATURE_FLAGS.FEATURE_BACKEND_READ_ONLY === true &&
      BASE_FEATURE_FLAGS.FEATURE_BACKEND_WRITE === false,
    noWriteOperations: BASE_FEATURE_FLAGS.FEATURE_BACKEND_WRITE === false,
    killSwitchReady:
      runtimeState.killSwitchActive === false &&
      runtimeState.emergencyShutdown === false,
    auditLogActive: BASE_FEATURE_FLAGS.FEATURE_AUDIT_LOG === true,
    logsReadOnly:
      BASE_FEATURE_FLAGS.FEATURE_LOGS_READ_ONLY === true &&
      BASE_FEATURE_FLAGS.FEATURE_LOGS_LIVE === false,
    androidAutoUpdateDisabled: BASE_FEATURE_FLAGS.FEATURE_ANDROID_AUTO_UPDATE === false,
    optionalThreatFeaturesOff:
      BASE_FEATURE_FLAGS.FEATURE_QUANTUM_DEFENSE === false &&
      BASE_FEATURE_FLAGS.FEATURE_THREAT_SCANNER === false &&
      BASE_FEATURE_FLAGS.FEATURE_DDOS_PROTECTION === false &&
      BASE_FEATURE_FLAGS.FEATURE_ADMIN_CONSOLE === false
  };

  const compliant = Object.values(checks).every(Boolean);
  return {
    compliant,
    checks,
    riskLevel: compliant ? 'CONTROLLED' : 'REVIEW_REQUIRED',
    timestamp: new Date().toISOString(),
    version: BASE_FEATURE_FLAGS.VERSION
  };
}

// Deliberately no mutable feature object or recovery function is exported to window.
// Browser-visible flags are informational and must not be treated as authorization.
