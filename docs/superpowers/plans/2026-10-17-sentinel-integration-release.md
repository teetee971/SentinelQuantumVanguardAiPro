# Sentinel Integration Release & Physical Qualification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make System Doctor, UI, CI, emulator evidence, and physical S24+ qualification consume the same integration truth and prevent false commercial-readiness claims.

**Architecture:** The Integration Registry exports a bounded exact-head report. UI/System Doctor consume runtime snapshots, CI validates static/runtime evidence, and physical proof upgrades only capabilities that explicitly require device/modem/OEM/peripheral evidence.

**Tech Stack:** Kotlin/Android, Node.js, GitHub Actions, existing evidence schemas, Samsung Galaxy S24+ physical qualification.

**Spec:** `docs/superpowers/specs/2026-10-17-sentinel-integration-core-design.md`

## Global Constraints

- Physical evidence must bind to exact source SHA/APK/certificate where the existing release-evidence system requires it.
- Emulator PASS cannot promote `PHYSICAL_REQUIRED` to CONNECTED.
- UI and System Doctor may not invent state independently of the Integration Registry.
- Required release gates remain fail closed.
- Existing Phone Core evidence semantics and emergency fail-open behavior remain unchanged.

## Review Focus

- Same capability showing different status in UI vs System Doctor must fail tests.
- Evidence from another source SHA/build must not satisfy current-head release truth.
- Expired physical proof must not silently remain valid.
- Missing physical proof must not block unrelated CI_ONLY/INTENTIONALLY_DISABLED capabilities.
- Partial S24+ campaigns must preserve unresolved PHYSICAL_REQUIRED states.

---

### Task 1: System Doctor integration-state surface

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSystemDoctor.kt`
- Add/modify presentation models and tests.

- [ ] Write RED tests requiring System Doctor to enumerate all registered product capabilities and stable diagnostic reasons.
- [ ] Run targeted JVM tests; expect RED.
- [ ] Consume `IntegrationRegistry.snapshot()` rather than local readiness constants.
- [ ] Re-run targeted tests; expect PASS.
- [ ] Commit `feat(doctor): surface canonical integration truth`.

### Task 2: User-visible capability status consistency

**Files:**
- Modify relevant Compose screens only where they currently hardcode readiness/availability.
- Add source/JVM tests tying visible states to Integration Registry capability IDs.

- [ ] Write RED tests for VPN, Mesh, Wearable, Threat Intelligence/Digital Exposure, Voice/PTT and other user-visible availability surfaces.
- [ ] Replace hardcoded READY/available labels with canonical integration state presentation.
- [ ] Ensure locked/physical-required copy remains truthful and localized.
- [ ] Run UI/JVM/product-truth suites.
- [ ] Commit `feat(ui): consume canonical integration status`.

### Task 3: Exact-head integration report artifact

**Files:**
- Create: `scripts/generate-integration-report.js`
- Create: `scripts/generate-integration-report.test.js`
- Modify: required validation workflow to upload report artifact.

**Interfaces:**
- Produces JSON containing source SHA, capability IDs, states, diagnostic reasons, evidence classes, and unresolved physical requirements.

- [ ] Write RED tests for deterministic ordering, source SHA binding, invalid state rejection, and category totals.
- [ ] Implement report generation from canonical registry plus available CI/runtime evidence inputs.
- [ ] Add workflow artifact generation without changing PASS criteria yet.
- [ ] Run tests and workflow source contracts.
- [ ] Commit `ci(integration): publish exact-head integration report`.

### Task 4: Release gate integration

**Files:**
- Modify: existing Production Merge Gate / product capability truth selector scripts and tests.

- [ ] Write RED tests proving a claimed CONNECTED capability without required exact-head evidence fails the gate.
- [ ] Ensure locked/disabled/CI-only states are accepted only when truthfully declared and not advertised as available.
- [ ] Wire integration report into the existing exact-PR/exact-head gate selection logic.
- [ ] Re-run product-truth, security-governance, and selector tests.
- [ ] Commit `ci(release): enforce exact-head integration truth`.

### Task 5: Physical capability mapping

**Files:**
- Modify/add physical qualification schema/config to map evidence IDs to Integration Registry capability IDs.
- Tests: physical evidence verifier contract tests.

- [ ] Write RED tests for wrong SHA/APK/certificate, missing evidence, partial campaigns, and stale proof.
- [ ] Map at minimum real carrier calls, first-call-after-process-kill screening, SMS/MMS callbacks, dual-SIM where available, audio/Bluetooth, Wi-Fi/BLE OEM behavior, contacts sync, wearable peripheral behavior if hardware exists.
- [ ] Preserve `PHYSICAL_REQUIRED` until valid bound proof is accepted.
- [ ] Run physical verifier/product-truth tests.
- [ ] Commit `test(release): bind physical proof to integration capabilities`.

### Task 6: S24+ release-candidate campaign

**Evidence target:** one exact release-candidate APK/source/certificate set.

- [ ] Execute first-run/permission/role/restart/process-death scenarios.
- [ ] Execute real incoming/outgoing carrier calls, emergency fail-open, DTMF, speaker/Bluetooth routing, and CallScreening latency capture.
- [ ] Execute SMS send/receive callbacks, multipart SMS, MMS download/send/dedup and dual-SIM scenarios when available.
- [ ] Execute contacts read/write/sync, Wi-Fi/BLE scans under Samsung/Android restrictions, process restart durability, and wearable scenarios if hardware is available.
- [ ] Sign/verify the physical evidence manifest through the existing fail-closed verifier.
- [ ] Generate final integration report; unresolved hardware-not-available cases remain explicitly PHYSICAL_REQUIRED rather than fabricated PASS.

## Acceptance

Release integration is complete when UI, System Doctor, CI, emulator evidence, and physical evidence agree on the same capability states for the exact release candidate, and no commercial-readiness claim can be produced while required physical capability proof remains unresolved.