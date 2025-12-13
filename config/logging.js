/**
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Phase E - Unified Logging System
 * 
 * Unified log format where simulation = future reality
 * All logs follow the same structure regardless of source
 */

/**
 * Log Levels
 */
export const LogLevel = {
  DEBUG: 'DEBUG',
  INFO: 'INFO',
  WARN: 'WARN',
  ERROR: 'ERROR',
  CRITICAL: 'CRITICAL'
};

/**
 * Log Sources
 */
export const LogSource = {
  SYSTEM: 'system',
  BACKEND: 'backend',
  AGENT_NETWORK: 'agent:network-guardian',
  AGENT_PEGASUS: 'agent:pegasus-scan',
  AGENT_FRAUD: 'agent:anti-fraud-pro',
  AGENT_PRIVACY: 'agent:privacy-guardian',
  AGENT_ROOTKIT: 'agent:system-rootkit',
  AGENT_CLOUD: 'agent:cloud-sync',
  UI: 'ui',
  API: 'api'
};

/**
 * Create a standardized log entry
 * @param {string} level - Log level (DEBUG, INFO, WARN, ERROR, CRITICAL)
 * @param {string} source - Source of the log
 * @param {string} message - Log message
 * @param {object} metadata - Additional metadata
 * @returns {object} - Standardized log entry
 */
export function createLogEntry(level, source, message, metadata = {}) {
  return {
    timestamp: new Date().toISOString(),
    level: level,
    source: source,
    message: message,
    metadata: {
      ...metadata,
      phase: 'E',
      simulation: !isRealMode()
    }
  };
}

/**
 * Check if system is in real mode (not simulation)
 * @returns {boolean}
 */
function isRealMode() {
  if (typeof window !== 'undefined' && window.SENTINEL_FEATURE_FLAGS) {
    return window.SENTINEL_FEATURE_FLAGS.FEATURE_LOGS_LIVE === true;
  }
  return false;
}

/**
 * Logger class for unified logging
 */
export class SentinelLogger {
  constructor(source) {
    this.source = source;
    this.logs = [];
    this.maxLogs = 1000; // Keep last 1000 logs in memory
  }

  /**
   * Log a message
   * @param {string} level - Log level
   * @param {string} message - Log message
   * @param {object} metadata - Additional metadata
   */
  log(level, message, metadata = {}) {
    const entry = createLogEntry(level, this.source, message, metadata);
    
    // Store in memory
    this.logs.push(entry);
    if (this.logs.length > this.maxLogs) {
      this.logs.shift();
    }
    
    // Console output (development)
    this._consoleLog(entry);
    
    // Send to backend (if enabled)
    if (isRealMode()) {
      this._sendToBackend(entry);
    }
    
    // Emit event
    this._emitLogEvent(entry);
    
    return entry;
  }

  debug(message, metadata) {
    return this.log(LogLevel.DEBUG, message, metadata);
  }

  info(message, metadata) {
    return this.log(LogLevel.INFO, message, metadata);
  }

  warn(message, metadata) {
    return this.log(LogLevel.WARN, message, metadata);
  }

  error(message, metadata) {
    return this.log(LogLevel.ERROR, message, metadata);
  }

  critical(message, metadata) {
    return this.log(LogLevel.CRITICAL, message, metadata);
  }

  /**
   * Get recent logs
   * @param {number} limit - Number of logs to return
   * @returns {Array} - Array of log entries
   */
  getRecentLogs(limit = 100) {
    return this.logs.slice(-limit);
  }

  /**
   * Clear logs
   */
  clearLogs() {
    this.logs = [];
  }

  /**
   * Console output
   * @private
   */
  _consoleLog(entry) {
    const prefix = `[${entry.timestamp}] [${entry.level}] [${entry.source}]`;
    const suffix = entry.metadata.simulation ? ' [SIMULATION]' : '';
    
    switch (entry.level) {
      case LogLevel.DEBUG:
        console.debug(`${prefix} ${entry.message}${suffix}`, entry.metadata);
        break;
      case LogLevel.INFO:
        console.info(`${prefix} ${entry.message}${suffix}`, entry.metadata);
        break;
      case LogLevel.WARN:
        console.warn(`${prefix} ${entry.message}${suffix}`, entry.metadata);
        break;
      case LogLevel.ERROR:
      case LogLevel.CRITICAL:
        console.error(`${prefix} ${entry.message}${suffix}`, entry.metadata);
        break;
      default:
        console.log(`${prefix} ${entry.message}${suffix}`, entry.metadata);
    }
  }

  /**
   * Send to backend
   * @private
   */
  async _sendToBackend(entry) {
    try {
      // Only send if backend is enabled
      if (!window.SENTINEL_FEATURE_FLAGS?.FEATURE_BACKEND) {
        return;
      }

      await fetch('/api/v1/logs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(entry)
      });
    } catch (error) {
      // Silently fail in simulation mode
      console.debug('Log backend not available:', error);
    }
  }

  /**
   * Emit log event
   * @private
   */
  _emitLogEvent(entry) {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('sentinel:log', {
        detail: entry
      }));
    }
  }
}

/**
 * Create a logger instance
 * @param {string} source - Source identifier
 * @returns {SentinelLogger} - Logger instance
 */
export function createLogger(source) {
  return new SentinelLogger(source);
}

// Global logger registry
const loggers = new Map();

/**
 * Get or create a logger for a source
 * @param {string} source - Source identifier
 * @returns {SentinelLogger} - Logger instance
 */
export function getLogger(source) {
  if (!loggers.has(source)) {
    loggers.set(source, new SentinelLogger(source));
  }
  return loggers.get(source);
}

// Export for global access
if (typeof window !== 'undefined') {
  window.SENTINEL_createLogger = createLogger;
  window.SENTINEL_getLogger = getLogger;
  window.SENTINEL_LogLevel = LogLevel;
  window.SENTINEL_LogSource = LogSource;
}

// System logger
export const systemLogger = getLogger(LogSource.SYSTEM);

// Log system initialization
systemLogger.info('Unified logging system initialized', {
  version: '1.0.0',
  phase: 'E',
  simulation: !isRealMode()
});
