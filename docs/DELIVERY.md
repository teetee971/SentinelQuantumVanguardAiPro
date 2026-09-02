# DELIVERY DOCUMENTATION

## Sentinel Quantum Vanguard AI Pro

This document is retained as a historical delivery record. It is not the current production validation record and must not be used as evidence that CI, security, performance, or release gates currently pass.

### Current source of truth

- Canonical web/PWA surface: repository root.
- Canonical Android project: `native-android-app/`.
- Android build validation: `.github/workflows/build-native-android.yml`.
- Signed Android release: `.github/workflows/android-release.yml`, tag-controlled and restricted to tags whose commits are reachable from `main`.
- Security validation: `.github/workflows/security-governance.yml`, `.github/workflows/security-fuzz.yml`, `.github/workflows/project-isolation.yml`, CodeQL, integrity and other active security controls.

### Validation policy

A correction is not automatically a validation. A local test is not a CI validation. A successful build is not proof of security. Production readiness must be supported by current, reproducible evidence from the relevant checks and release artefacts.

### Historical claims

The original version of this file contained obsolete workflow names, obsolete Android paths, estimated Lighthouse scores, unsupported vulnerability counts, and assertions that all CI checks were green. Those statements are historical and are intentionally not carried forward as current facts.

The repository currently has an outstanding GitHub Actions runner-startup blocker documented in issue #195. Until the affected workflows execute their steps successfully, this document must not state that the complete CI/security validation suite is green.

### Android security boundary

The maintained Android source is `native-android-app/`. Release signing material must remain in GitHub Actions secrets and temporary runtime storage only; keystores and private signing keys must never be committed.

### Project isolation

Sentinel Quantum Vanguard AI Pro is strictly separate from A KI PRI SA YÉ. No Firebase, A KI PRI SA YÉ application code, configuration, credentials, dependencies, or operational integration may be introduced into Sentinel.

### Historical status

This file documents an earlier delivery phase. For current release and validation decisions, use the active workflows and current validation documentation rather than this historical record.
