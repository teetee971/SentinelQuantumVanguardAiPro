# Detection Rules

This directory contains detection rules for identifying security events and threats.

## Principles

All detection rules follow these principles:

1. **Defensive Only**: Rules detect threats, never exploit them
2. **Explainable**: Every rule has a clear explanation
3. **Local Execution**: Rules run on the device, no cloud dependency
4. **Low False Positives**: Rules are tuned to minimize false alerts
5. **Privacy Preserving**: Rules don't collect or transmit personal data

## Rule Format

Rules are defined in JSON format:

```json
{
  "id": "RULE_001",
  "name": "Suspicious Permission Pattern",
  "description": "Detects apps requesting dangerous permission combinations",
  "severity": "WARNING",
  "type": "permission_analysis",
  "conditions": {
    "permissions": [
      "READ_CONTACTS",
      "SEND_SMS",
      "ACCESS_FINE_LOCATION"
    ],
    "threshold": 2
  },
  "action": "log_and_alert",
  "explanation": "This combination of permissions could allow data exfiltration"
}
```

## Rule Categories

### 1. Permission Rules

Detect suspicious permission requests or combinations.

**Example**: App requesting SMS + Contacts + Location

### 2. Network Rules

Monitor network connections for unusual patterns.

**Example**: Connection to known malicious IPs (from public threat feeds)

### 3. Behavioral Rules

Detect anomalous application behavior.

**Example**: Background app consuming excessive battery

### 4. Phone Rules

Identify spam/scam phone calls.

**Example**: Number matches known spam pattern

### 5. Storage Rules

Monitor file system for suspicious activity.

**Example**: Mass file encryption (ransomware indicator)

## Rule Engine

The rule engine processes rules in this order:

1. **Load Rules**: Load enabled rules from configuration
2. **Collect Data**: Gather relevant data from the system
3. **Evaluate**: Check each rule's conditions
4. **Trigger**: Execute actions for matched rules
5. **Explain**: Generate human-readable explanation
6. **Log**: Record event with full context

## Creating New Rules

To create a new detection rule:

1. Identify the threat or anomaly to detect
2. Define clear, testable conditions
3. Determine appropriate severity
4. Write clear explanation for users
5. Test for false positives
6. Document the rule

## Compliance

All rules are designed to comply with:

- ✅ GDPR (no personal data collection without consent)
- ✅ Local privacy laws
- ✅ Ethical security practices
- ✅ Defensive security principles

## Examples

See the `examples/` subdirectory for sample rules.

## Testing

Rules should be tested against:

- ✅ Known malicious patterns (should trigger)
- ✅ Normal user behavior (should not trigger)
- ✅ Edge cases
- ✅ Performance impact
