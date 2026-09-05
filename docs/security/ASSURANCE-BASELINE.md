# Sentinel Assurance Baseline

## Assurance posture

Sentinel is designed toward high assurance through zero trust, defense in depth, fail-closed decisions, verified supply-chain evidence, and compartmentalization. It is not described as unhackable, military-grade, certified, or free of vulnerabilities without independent evidence and applicable certification.

## Responsibility boundaries

| Zone | Repository components | Authority |
|---|---|---|
| Control Plane | `ai-governance/`, `decision-plane/safety/`, `ai-governance/audit/` | Policy, trust, approval and audit decisions; no direct sensitive execution |
| Analysis Plane | `osint-intelligence/`, `security-digital-twin/`, `ai-orchestrator/` | Bounded analysis and decision support; no action authority |
| Action Plane | Approved external execution adapters referenced by action plans | Must be separately sandboxed, least-privileged, target-scoped and human-approved |

`decision-plane/safety/action-gate.js` requires policy approval, evidence integrity, bounded trust uncertainty, safe simulation and, for critical actions, target authorization plus human validation. `decision-plane/action-verification/action-plan.js` requires bounded preconditions, postconditions, rollback, and an approved adapter. These controls deny by default; they do not execute actions.

## Evidence and release controls

- Security checks, source export and Android signing are evidence only when their GitHub Actions steps complete successfully.
- `scripts/evidence-trust.js` binds autonomous-maintenance evidence to the exact repository, commit, workflow, workflow reference, run, attempt, ref and event.
- Android release builds verify APK signatures, publish checksums, generate a CycloneDX SBOM from the lockfile, and publish `release-evidence.json` binding hashes to the CI execution.
- `scripts/check-github-actions-pinning.js`, Dependabot, lockfiles and `npm ci --ignore-scripts` reduce supply-chain exposure. They do not eliminate it.

## Secrets, permissions and recovery

- Release signing credentials remain GitHub Secrets and are never committed or emitted to normal logs.
- Workflows use read-only `contents` permission unless release publication requires `contents: write`.
- Revocation and rotation occur in GitHub environments or an approved secret manager/KMS. Incident handling must revoke compromised credentials, invalidate affected artifacts or sessions, reissue credentials, and retain incident evidence.
- Audit chains are tamper-evident in process; append-only storage, key custody, retention and external anchoring remain deployment responsibilities.

## Verification roadmap

Map implemented controls and deployment operations to NIST SSDF, OWASP ASVS, SLSA, CIS Benchmarks and ISO 27001 as applicable. FIPS and Common Criteria require scoped components, formal evaluation and external certification; neither is implied by this source repository.
