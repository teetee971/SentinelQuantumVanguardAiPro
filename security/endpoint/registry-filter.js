const SENSITIVE_PATHS = Object.freeze([
  { prefix: 'HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run', category: 'persistence', severity: 'high' },
  { prefix: 'HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run', category: 'persistence', severity: 'high' },
  { prefix: 'HKLM\\SYSTEM\\CurrentControlSet\\Services', category: 'service_persistence', severity: 'critical' },
  { prefix: 'HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon', category: 'logon_persistence', severity: 'critical' },
  { prefix: 'HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options', category: 'execution_hijack', severity: 'critical' },
  { prefix: 'HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies', category: 'security_policy', severity: 'high' },
  { prefix: 'HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies', category: 'security_policy', severity: 'high' }
]);

const ALLOWED_OPERATIONS = new Set(['create_key', 'delete_key', 'set_value', 'delete_value', 'rename_key']);

function normalizeRegistryPath(value) {
  if (typeof value !== 'string') return '';
  return value.trim().replaceAll('/', '\\').replace(/\\+/g, '\\').toUpperCase();
}

export function classifyRegistryEvent(event = {}) {
  const operation = typeof event.operation === 'string' ? event.operation.trim().toLowerCase() : '';
  const path = normalizeRegistryPath(event.path);

  if (!ALLOWED_OPERATIONS.has(operation) || !path) {
    return Object.freeze({ accepted: false, reason: 'INVALID_REGISTRY_EVENT' });
  }

  const match = SENSITIVE_PATHS.find(rule => path.startsWith(rule.prefix.toUpperCase()));
  if (!match) {
    return Object.freeze({
      accepted: true,
      monitor: false,
      severity: 'info',
      category: 'registry_change',
      operation,
      path
    });
  }

  return Object.freeze({
    accepted: true,
    monitor: true,
    severity: match.severity,
    category: match.category,
    operation,
    path
  });
}

export function shouldEscalateRegistryEvent(event) {
  const result = classifyRegistryEvent(event);
  return result.accepted === true && result.monitor === true && ['high', 'critical'].includes(result.severity);
}

export const REGISTRY_FILTER_BOUNDARY = Object.freeze({
  telemetry_only: true,
  kernel_driver_included: false,
  automatic_registry_blocking: false,
  automatic_registry_modification: false,
  requires_endpoint_agent_for_live_events: true,
  notes: 'This module filters and prioritizes registry telemetry. A signed Windows kernel callback/minifilter requires a separately reviewed endpoint-agent implementation.'
});
