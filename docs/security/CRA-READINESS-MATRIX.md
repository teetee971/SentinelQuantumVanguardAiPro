# Sentinel Quantum Vanguard — Cyber Resilience Act readiness matrix

> Internal engineering baseline. This document is **not legal advice** and does not constitute a formal CRA conformity assessment.

## Objective

Use Regulation (EU) 2024/2847 (Cyber Resilience Act, CRA) as a security engineering baseline for Sentinel Quantum Vanguard. The product's legal classification and applicable conformity-assessment route must be determined separately.

## Controls

| ID | CRA-oriented control | Engineering evidence required | Status |
|---|---|---|---|
| CRA-01 | Cybersecurity risk assessment | Threat model, assets, trust boundaries, risk register | IN PROGRESS |
| CRA-02 | Secure by default | Minimal permissions, secure configuration, fail-safe behavior | IN PROGRESS |
| CRA-03 | Vulnerability handling | Intake, triage, remediation, regression tests | IN PROGRESS |
| CRA-04 | Actively exploited vulnerability detection | CISA KEV + vendor advisories + validated intelligence | IN PROGRESS |
| CRA-05 | Zero-day classification | Evidence-based state machine; never equate CVE with exploitation | IN PROGRESS |
| CRA-06 | 24/72h incident workflow | Detection timestamp, acknowledgement clock, notification package | NOT IMPLEMENTED |
| CRA-07 | Final vulnerability reporting | Corrective measure timestamp + final report data model | NOT IMPLEMENTED |
| CRA-08 | SBOM | Machine-readable dependency inventory for JS/Android components | IN PROGRESS |
| CRA-09 | Dependency monitoring | Continuous dependency/CVE monitoring and update policy | IN PROGRESS |
| CRA-10 | Security testing | Fuzzing, SAST, dependency scanning, regression tests | IN PROGRESS |
| CRA-11 | Security updates | Reproducible build, signed release, update traceability | TO VERIFY |
| CRA-12 | Vulnerability disclosure | Security contact and coordinated disclosure process | TO VERIFY |
| CRA-13 | Technical documentation | Architecture, threat model, controls, test evidence | IN PROGRESS |
| CRA-14 | Product/project isolation | Sentinel must contain no A KI PRI SA YÉ configuration, secrets, package IDs, domains or build references | IN PROGRESS |

## Mandatory engineering rule for the Zero-Day Exploit Clock

The clock must measure **time since reliable evidence that Sentinel contains an actively exploited vulnerability**, not time since a CVE was published.

Required states:

- `UNKNOWN`
- `PUBLISHED`
- `EXPLOITABLE`
- `ACTIVELY_EXPLOITED`
- `MITIGATED`
- `PATCH_AVAILABLE`
- `RESOLVED`

An `ACTIVELY_EXPLOITED` transition requires reliable evidence. Good-faith security testing, investigation, correction or disclosure does not by itself establish active exploitation.

## CRA Article 14 operational timers

For an applicable manufacturer obligation, the engineering system must be capable of recording:

1. `aware_at` — when the manufacturer becomes aware of the actively exploited vulnerability;
2. early-warning deadline — 24 hours after `aware_at`;
3. vulnerability-notification deadline — 72 hours after `aware_at`;
4. corrective-measure availability;
5. final-report deadline — no later than 14 days after the corrective/mitigating measure is available.

These timers must use UTC timestamps and must be immutable in the audit trail.

## Separation requirement

Sentinel Quantum Vanguard and A KI PRI SA YÉ are independent products. Any cross-reference is a security/integrity failure. CI must reject known identifiers, package names, project IDs, domains and configuration paths belonging to A KI PRI SA YÉ.

## Acceptance gate

This matrix is not considered complete until every `IN PROGRESS`, `NOT IMPLEMENTED`, and `TO VERIFY` control has either:

- an implemented control with automated evidence, or
- a documented, reviewed exception with a clear owner and deadline.

No production-readiness claim should be made solely from the existence of this document.
