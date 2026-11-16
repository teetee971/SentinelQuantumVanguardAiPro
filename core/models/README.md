# Core Models

This directory contains data models used throughout the Sentinel platform.

## Models

### SecurityEvent

Represents a security event logged by the system.

```typescript
interface SecurityEvent {
  timestamp: number;      // Unix timestamp in milliseconds
  type: string;          // Event type (e.g., "INCOMING_CALL", "PERMISSION_REQUEST")
  severity: string;      // Severity level ("INFO", "WARNING", "CRITICAL")
  explanation: string;   // Human-readable explanation
  source: string;        // Source module that generated the event
}
```

### PhoneRisk

Represents the risk assessment of a phone number.

```typescript
interface PhoneRisk {
  number: string;        // Phone number analyzed
  spam: boolean;         // Whether it's in spam database
  riskLevel: string;     // Risk level ("SAFE", "LOW", "MEDIUM", "HIGH")
  recommendation: string; // Recommended action
  source: string;        // Source of information
}
```

### AuditResult

Represents the result of a security audit.

```typescript
interface AuditResult {
  permissionsRisk: number;     // Number of dangerous permissions granted
  systemScore: number;         // Overall security score (0-100)
  summary: string;             // Summary in plain language
  checks: SecurityCheck[];     // List of detailed checks
  timestamp: number;           // When the audit was performed
}
```

### SecurityCheck

Represents an individual security check.

```typescript
interface SecurityCheck {
  name: string;          // Name of the check
  passed: boolean;       // Whether the check passed
  severity: string;      // Severity level
  message: string;       // Descriptive message
}
```

### MitreTechnique

Represents a MITRE ATT&CK technique (read-only reference).

```typescript
interface MitreTechnique {
  id: string;            // MITRE ID (e.g., "T1059")
  name: string;          // Technique name
  description: string;   // Description
  tactics: string[];     // Associated tactics
  observed: boolean;     // Whether observed in the environment (always false)
  detection: string;     // Detection methods
  mitigation: string;    // Mitigation strategies
  reference: string;     // URL to MITRE documentation
  read_only: boolean;    // Always true
}
```

## Usage

These models ensure consistency across the platform and provide clear contracts for data exchange between modules.

## Principles

- **Immutability**: Models should be treated as immutable once created
- **Validation**: All data should be validated before creating model instances
- **Explainability**: Every model includes human-readable explanations
- **Privacy**: No personally identifiable information (PII) in models unless explicitly required and consented
