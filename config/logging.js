/**
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Unified logging primitives.
 *
 * Logging is local, deterministic and independent from legacy phase/agent
 * feature flags. Security-sensitive persistence belongs to the audit subsystem.
 */

export const LogLevel = {
  DEBUG: 'DEBUG',
  INFO: 'INFO',
  WARN: 'WARN',
  ERROR: 'ERROR',
  CRITICAL: 'CRITICAL'
};

export const LogSource = {
  SYSTEM: 'system',
  SECURITY: 'security',
  GOVERNANCE: 'governance',
  AUDIT: 'audit',
  UI: 'ui',
  API: 'api'
};

export function createLogEntry(level, source, message, metadata = {}) {
  return {
    timestamp: new Date().toISOString(),
    level,
    source,
    message,
    metadata: { ...metadata }
  };
}

export class SentinelLogger {
  constructor(source) {
    this.source = source;
    this.logs = [];
    this.maxLogs = 1000;
  }

  log(level, message, metadata = {}) {
    const entry = createLogEntry(level, this.source, message, metadata);

    this.logs.push(entry);
    if (this.logs.length > this.maxLogs) {
      this.logs.shift();
    }

    this._consoleLog(entry);
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

  getRecentLogs(limit = 100) {
    return this.logs.slice(-Math.max(0, limit));
  }

  clearLogs() {
    this.logs = [];
  }

  _consoleLog(entry) {
    const prefix = `[${entry.timestamp}] [${entry.level}] [${entry.source}]`;
    const output = `${prefix} ${entry.message}`;

    switch (entry.level) {
      case LogLevel.DEBUG:
        console.debug(output, entry.metadata);
        break;
      case LogLevel.INFO:
        console.info(output, entry.metadata);
        break;
      case LogLevel.WARN:
        console.warn(output, entry.metadata);
        break;
      case LogLevel.ERROR:
      case LogLevel.CRITICAL:
        console.error(output, entry.metadata);
        break;
      default:
        console.log(output, entry.metadata);
    }
  }

  _emitLogEvent(entry) {
    if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
      window.dispatchEvent(new CustomEvent('sentinel:log', { detail: entry }));
    }
  }
}

export function createLogger(source) {
  return new SentinelLogger(source);
}

const loggers = new Map();

export function getLogger(source) {
  if (!loggers.has(source)) {
    loggers.set(source, new SentinelLogger(source));
  }
  return loggers.get(source);
}

if (typeof window !== 'undefined') {
  window.SENTINEL_createLogger = createLogger;
  window.SENTINEL_getLogger = getLogger;
  window.SENTINEL_LogLevel = LogLevel;
  window.SENTINEL_LogSource = LogSource;
}

export const systemLogger = getLogger(LogSource.SYSTEM);
systemLogger.info('Unified logging initialized');
