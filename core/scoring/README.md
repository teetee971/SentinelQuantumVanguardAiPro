# Security Scoring System

This directory contains the security scoring logic for evaluating system security posture.

## Overview

The scoring system evaluates the overall security posture of a device on a scale of 0-100.

## Scoring Components

### 1. Permissions Score (Weight: 30%)

Evaluates the risk posed by granted permissions.

**Calculation**:
```
permissions_score = 100 - (dangerous_permissions_count * 5)
```

**Dangerous Permissions**:
- Camera
- Microphone
- Location
- Contacts
- SMS/Phone
- Storage

### 2. System Configuration Score (Weight: 40%)

Evaluates system security settings.

**Factors**:
- Screen lock configured (+20)
- Encryption enabled (+20)
- Unknown sources disabled (+10)
- Developer mode disabled (+10)
- Google Play Protect enabled (+10)

### 3. Application Security Score (Weight: 20%)

Evaluates installed applications.

**Factors**:
- No side-loaded apps from unknown sources (+10)
- All apps up-to-date (+10)
- No apps with known vulnerabilities (+20)

### 4. Network Security Score (Weight: 10%)

Evaluates network configuration.

**Factors**:
- VPN configured (+5)
- No open WiFi connections saved (+5)

## Final Score Calculation

```
final_score = (
  permissions_score * 0.30 +
  system_config_score * 0.40 +
  app_security_score * 0.20 +
  network_security_score * 0.10
)
```

## Score Ranges

| Range | Rating | Action Required |
|-------|--------|----------------|
| 90-100 | Excellent | No action needed |
| 70-89 | Good | Minor improvements recommended |
| 50-69 | Fair | Improvements necessary |
| 0-49 | Poor | Urgent action required |

## Scoring Principles

1. **Transparent**: All factors are documented and explainable
2. **Actionable**: Score includes specific recommendations
3. **Fair**: No arbitrary penalties
4. **Local**: Calculated entirely on-device
5. **Privacy-Preserving**: No personal data required

## Example

```json
{
  "overall_score": 78,
  "breakdown": {
    "permissions": 85,
    "system_config": 80,
    "app_security": 70,
    "network_security": 60
  },
  "recommendations": [
    "Remove unused permissions from 3 apps",
    "Update 2 applications",
    "Remove unsecured WiFi networks"
  ]
}
```

## Implementation

See `scoring_engine.kt` (Android) or `scoring_engine.ts` (Web) for implementation details.
