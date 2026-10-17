# Sentinel Android Integration Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import and complete the real Android producer-to-consumer wiring around the Sentinel Intelligence Core without introducing false readiness or CallScreening latency regressions.

**Architecture:** Reuse the bounded cross-module work already represented by PR #1695, but replay only its unique deltas on the then-current main. Each producer publishes typed, privacy-bounded evidence or runtime state after its existing domain boundary; UI/System Doctor read the shared truth rather than locally hardcoding readiness.

**Tech Stack:** Kotlin, Android, Compose, JVM tests, Node.js wiring-contract tests.

**Spec:** `docs/superpowers/specs/2026-10-17-sentinel-integration-core-design.md`

## Global Constraints

- Never merge #1695 or its precursor PRs wholesale if stale; replay unique deltas only.
- Raw SMS/MMS bodies, URLs, OTPs, MAC/BSSID values, or raw phone identities must not enter shared intelligence persistence unless already required by an approved bounded contract.
- Remote/community evidence retains provenance and cannot autonomously block calls.
- CallScreening pre-response path remains free of remote/network/disk/KeyStore work.
- Android runtime claims remain separate from physical-device proof.

## Review Focus

- Consent revoked between remote reputation fetch and publication must prevent publication/UI acceptance.
- Replayed MMS/SMS ingress must not duplicate shared evidence.
- Contact fingerprint version must not silently downgrade.
- Stale VPN PROTECTED observation must resolve to UNKNOWN/not protected.
- Scanner persistence must not block Wi-Fi/Bluetooth callbacks or retain raw hardware identifiers.

---

### Task 1: Replay reputation integration boundary

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/data/SettingsStore.kt`
- Create/modify: `native-android-app/app/src/main/java/com/sentinel/quantum/data/CallerReputationAcceptanceBoundary.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceCoreRuntime.kt`
- Test: `security/phone-intelligence/reputation-intelligence-wiring.test.js`

**Interfaces:**
- Produces bounded remote-reputation evidence with `UNVERIFIED_REMOTE` provenance only after the same consent transaction that permits local persistence/UI use.

- [ ] Recreate RED wiring/consent tests against current main.
- [ ] Run targeted Node/JVM tests; confirm only the intended contract fails.
- [ ] Replay the minimal acceptance-boundary and prepared-publication implementation from #1695.
- [ ] Run phone-intelligence, Android product-truth, targeted JVM tests, syntax, and supply-chain tests.
- [ ] Commit `feat(intelligence): connect consented reputation evidence`.

### Task 2: Replay SMS/MMS evidence wiring

**Files:**
- Modify: `SentinelSmsDeliverReceiver.kt`, `SentinelMmsDeliverReceiver.kt`, `SentinelMmsDownloadReceiver.kt`
- Modify: `IntelligenceCoreRuntime.kt`, `SmsMmsEvidenceAdapter.kt`
- Test: `security/phone-intelligence/sms-analysis-intelligence-wiring.test.js`
- Test: `security/phone-intelligence/mms-intelligence-wiring.test.js`

**Interfaces:**
- Produces idempotent bounded SMS/MMS evidence keyed by provider identity and existing fingerprint identity.

- [ ] Add RED tests for publication order, content exclusion, provider replay, and missing canonical identity.
- [ ] Run targeted tests; expect RED.
- [ ] Replay minimal bounded publication APIs and receiver calls after provider/projection decisions.
- [ ] Re-run targeted suites plus Android product truth.
- [ ] Commit `feat(intelligence): connect messaging evidence`.

### Task 3: Replay contacts and community evidence

**Files:**
- Modify: `CallerIdActivity.kt`
- Modify: `IntelligenceCoreRuntime.kt`
- Modify: `intelligence/adapters/ContactIntelligenceAdapter.kt`
- Create: `CommunityReportEvidenceAdapter.kt`
- Modify: `IntelligenceEvidence.kt`, `IntelligenceSnapshotStore.kt`
- Tests: contact/community wiring and snapshot round-trip tests.

**Interfaces:**
- Produces local contact evidence with preserved fingerprint version and community-report context that is explicitly excluded from `ScreeningProjection`.

- [ ] Write/port RED tests for contact publication, fingerprint version preservation, community serialization, and non-enforcement.
- [ ] Run targeted tests; expect RED.
- [ ] Replay minimal implementation from #1695.
- [ ] Re-run phone-intelligence/JVM/product-truth suites.
- [ ] Commit `feat(intelligence): connect contact and community context`.

### Task 4: Replay Wi-Fi/Bluetooth bounded timeline

**Files:**
- Modify: `WifiScanner.kt`, `BluetoothScanner.kt`, `NetworkTrustStore.kt`, `NetworkSurveillanceScreen.kt`
- Create: `NetworkObservationTimelineAdapter.kt`, `NetworkTimelineStore.kt`
- Tests: scanner timeline, adapter, and UI source contracts.

**Interfaces:**
- Produces local pseudonymous timeline snapshots without raw MAC/BSSID persistence.

- [ ] Add RED tests for callback-thread isolation, pseudonym-only storage, duplicate observations, and corrupt/stale store behavior.
- [ ] Run targeted tests; expect RED.
- [ ] Replay the bounded off-callback persistence and UI read path.
- [ ] Re-run network/JVM/product-truth suites.
- [ ] Commit `feat(network): connect bounded local observation timeline`.

### Task 5: Replay VPN runtime truth

**Files:**
- Modify: `SentinelVpnController.kt`, `SentinelSystemDoctor.kt`, `VpnScreen.kt`, `NetworkVpnPresentation.kt`
- Create: `SentinelVpnRuntimeStateRegistry.kt`
- Tests: runtime registry and presentation tests.

**Interfaces:**
- Produces fresh process-local VPN runtime observation; System Doctor and UI consume the same source.

- [ ] Add RED tests proving no hardcoded runtime READY state and stale PROTECTED rejection.
- [ ] Run targeted tests; expect RED.
- [ ] Replay the bounded runtime registry and shared UI/doctor consumption.
- [ ] Re-run targeted suites.
- [ ] Commit `feat(vpn): connect runtime truth to diagnostics`.

### Task 6: Bind Android capabilities into Integration Registry

**Files:**
- Modify: Android Integration Registry mapping introduced by the foundation plan.
- Tests: new `AndroidIntegrationRegistryContractTest.kt` plus JS product-truth assertions.

**Interfaces:**
- Consumes runtime signals from Tasks 1–5.
- Produces truthful state for caller reputation, SMS, MMS, contacts, community, Wi-Fi, BLE, network timeline, and VPN client runtime.

- [ ] Write RED tests for each mapped capability and for absent runtime/dependency states.
- [ ] Implement mapping only after underlying runtime proof exists.
- [ ] Run full targeted integration truth suites.
- [ ] Commit `feat(integration): register Android runtime capability truth`.

## Acceptance

This slice is complete when all real Android cross-module flows represented by #1695 are replayed on current main, tested, and surfaced through the Integration Registry without claiming Mesh, Wearable, external VPN gateways, or physical carrier readiness.