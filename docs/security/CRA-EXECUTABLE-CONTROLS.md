# CRA executable-controls implementation plan

This file tracks engineering work derived from Regulation (EU) 2024/2847. It is an internal engineering control plan, not a legal conformity declaration. Article 14 reporting obligations apply from 11 September 2026; most other CRA provisions apply from 11 December 2027.

## Verified baseline

- Android release builds generate a CycloneDX SBOM from the repository npm lockfile.
- Release evidence binds the SBOM and signed Android artifacts to the exact CI execution.
- SECURITY.md contains a responsible-disclosure section.
- Security testing includes CodeQL, fuzzing, integrity and isolation controls.

## Open gaps

### CRA-SBOM-ANDROID — Native dependency coverage

The npm CycloneDX SBOM does not by itself prove coverage of Gradle/Kotlin/Android dependencies incorporated into the native application.

Implemented control:

- `exportReleaseDependencyInventory` resolves `releaseRuntimeClasspath`;
- the inventory records the root component, resolved component identities and resolved dependency relationships;
- unresolved dependency results fail the export instead of being silently omitted;
- module components retain Maven package coordinates and versions;
- CI validates non-empty component and relationship sets;
- the signed-release workflow binds the inventory hash and byte size into `release-evidence.json`;
- the evidence verifier rejects malformed graphs, duplicate component identities, unknown relationship endpoints, missing inventory files, size changes and hash changes;
- the inventory is retained with the signed APK/AAB release bundle.

Status: **OPEN — implementation present; first successful signed-release execution and retained evidence still required.**

### CRA-CVD — Coordinated vulnerability disclosure

Acceptance criteria:

- define an explicit private reporting route;
- define acknowledgement, triage, remediation and coordinated-disclosure states;
- prohibit secrets and personal data in public issues;
- record security-report timestamps in UTC;
- connect accepted reports to remediation and regression-test evidence.

Status: OPEN.

### CRA-AEV-CLOCK — Actively exploited vulnerability clock

Acceptance criteria:

- distinguish CVE publication from confirmed active exploitation;
- record immutable `aware_at` in UTC;
- calculate 24-hour and 72-hour deadlines deterministically;
- record corrective/mitigating measure availability;
- calculate the applicable final-report deadline, including the 14-day deadline after a corrective or mitigating measure becomes available for an actively exploited vulnerability;
- preserve source/provenance for exploitation evidence;
- reject invalid, future or contradictory timestamps;
- keep actively exploited vulnerability deadlines distinct from severe-incident reporting, whose final report follows a different timeline;
- fuzz the parser and deadline calculator.

Status: OPEN.

## Evidence rule

A control remains OPEN until executable evidence exists and the corresponding CI or release job has actually run successfully. Documentation, workflow presence, or a skipped/unstarted runner is not a pass.

## Isolation rule

Sentinel Quantum Vanguard must remain isolated from unrelated products. CRA work must not introduce foreign package identifiers, configuration, secrets, deployment coupling or runtime dependencies.
