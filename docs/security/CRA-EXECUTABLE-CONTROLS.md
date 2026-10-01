# CRA executable-controls implementation plan

This file tracks engineering work derived from Regulation (EU) 2024/2847. It is an internal engineering control plan, not a legal conformity declaration.

## Verified baseline

- Android release builds generate a CycloneDX SBOM from the repository npm lockfile.
- Release evidence binds the SBOM and signed Android artifacts to the exact CI execution.
- SECURITY.md contains a responsible-disclosure section.
- Security testing includes CodeQL, fuzzing, integrity and isolation controls.

## Open gaps

### CRA-SBOM-ANDROID — Native dependency coverage

Current release SBOM generation uses `npm sbom --package-lock-only`. This does not by itself prove complete coverage of Gradle/Kotlin/Android dependencies incorporated into the native application.

Acceptance criteria:

- generate a machine-readable inventory of resolved native Android release dependencies;
- preserve package name, version and dependency relationship where the build system exposes them;
- bind the native inventory to the same release evidence as the npm CycloneDX SBOM;
- fail the release evidence gate if a required SBOM artifact is missing or malformed;
- retain the generated inventory with the signed release artifacts.

Status: OPEN.

### CRA-CVD — Coordinated vulnerability disclosure

The repository currently instructs reporters to disclose sensitive vulnerabilities privately, but an auditable intake/triage/remediation process still requires verification.

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
- calculate the applicable final-report deadline;
- preserve source/provenance for exploitation evidence;
- reject invalid, future or contradictory timestamps;
- fuzz the parser and deadline calculator.

Status: OPEN.

## Evidence rule

A control remains OPEN until executable evidence exists and the corresponding CI job has actually run successfully. Documentation, workflow presence, or a skipped/unstarted runner is not a pass.

## Isolation rule

Sentinel Quantum Vanguard must remain isolated from unrelated products. CRA work must not introduce foreign package identifiers, configuration, secrets, deployment coupling or runtime dependencies.
