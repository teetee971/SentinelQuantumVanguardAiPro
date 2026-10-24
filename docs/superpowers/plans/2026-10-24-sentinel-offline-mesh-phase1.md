# Sentinel Offline Mesh Phase 1 Implementation Plan

> Historical plan for the pre-crypto scaffold. The implementation and security
> boundary for the current head are defined in
> `docs/superpowers/specs/2026-10-24-sentinel-offline-mesh-secure-transport-design.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real, bounded, bidirectional BLE GATT transport foundation with fragmentation, reassembly, ACKs, timeouts, retries, and interruption handling while keeping private messages locked until cryptographic authentication exists.

**Architecture:** A pure Kotlin protocol core owns frame encoding, fragmentation, reassembly, ACK tracking, retry deadlines, and session generations. Thin Android GATT adapters expose one client-write characteristic and one server-notify characteristic in the existing service, while the existing host gate keeps service-registration-before-advertising ordering. Every production endpoint uses a deny-by-default authenticator; tests inject a deterministic verifier and clock.

**Tech Stack:** Kotlin, Android Bluetooth GATT APIs, JUnit 4, existing Android Gradle project, no new runtime dependency.

**Spec:** `docs/superpowers/specs/2026-10-24-sentinel-offline-mesh-phase1-design.md`

## Global Constraints

- Never modify `main`; all commits target `feature/sentinel-offline-mesh-foundation-20261024`.
- Do not modify Phone Core, SMS, MMS, or their tests.
- Runtime transport is offline and has no Internet dependency.
- Private payloads remain locked until a real cryptographic authenticator is integrated.
- Bound frame size, message size, fragment count, queue depth, and retry count.
- Treat every callback as stale unless its link generation and operation ticket match.
- Do not report READY as authenticated or operational without observed test/build evidence.
- Use the existing Android compatibility floor: minSdk 24, compileSdk 37, JDK 17.

## Review Focus

- Malformed, oversized, empty, and inconsistent frames must be rejected without allocation leaks (protocol-core tests).
- Out-of-order, duplicate, expired, and interrupted fragment sequences must ACK safely and deliver once (reassembler/transport tests).
- ACK loss and radio write failure must retry only within the configured bound and then invalidate the link (transport tests).
- Old-session callbacks, duplicate callbacks, and late MTU/descriptor callbacks must not change current state (link-gate/client tests).
- Discovery or an arbitrary proof must never unlock production private transport (authentication-lock tests).

### Task 1: Protocol core

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattFrame.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattReassembler.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattTransport.kt`
- Test: corresponding `OfflineMeshGattFrameTest.kt`, `OfflineMeshGattReassemblerTest.kt`, `OfflineMeshGattTransportTest.kt`

- [ ] Write failing tests for frame round trips, fragmentation, malformed input, out-of-order/duplicate reassembly, delivery-once, bounded ACK retry, write failure, interruption, and default-deny authentication.
- [ ] Run the focused unit tests and observe failure because the protocol classes do not yet exist.
- [ ] Implement the bounded protocol core with exact limits and a deterministic clock injection.
- [ ] Run the focused tests, then the full `testDebugUnitTest` task.

### Task 2: GATT link and adapters

**Files:**
- Modify: `OfflineMeshGattLinkGate.kt`
- Modify: `OfflineMeshGattClient.kt`
- Modify: `OfflineMeshGattServer.kt`
- Modify: `OfflineMeshConnectableAdvertiser.kt` only for transport-facing documentation/constants if needed
- Test: `OfflineMeshGattLinkGateTest.kt` and new pure adapter-contract tests where possible

- [ ] Add notification-subscription state between service discovery and MTU readiness.
- [ ] Add direction-specific data characteristics and CCCD handling.
- [ ] Wire client writes, server writes/notifications, notification reception, ATT completion, app ACK processing, and stale-session invalidation.
- [ ] Keep production authentication closed by default.
- [ ] Run focused BLE tests and the full Android unit-test task.

### Task 3: Coordinator and regression evidence

**Files:**
- Modify: `OfflineMeshBleHost.kt` only if needed to expose the transport-ready boundary without changing its failure ordering.
- Modify: `OfflineMeshBleHostGate.kt` only if required by the adapter integration.
- Create/update: phase-1 evidence documentation.
- Do not modify any Phone Core, SMS, MMS, or UI file.

- [ ] Add tests for host teardown on advertising/server interruption and for the unchanged authentication boundary.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace --no-daemon`.
- [ ] Run `./gradlew lintDebug --stacktrace --no-daemon`.
- [ ] Run `./gradlew assembleDebug --stacktrace --no-daemon`.
- [ ] Record exact observed results and commit only after reading the command output.

### Task 4: Review handoff

- [ ] Inspect changed filenames against the scope boundary.
- [ ] Create a draft pull request from `feature/sentinel-offline-mesh-foundation-20261024` to `main`; do not merge it.
- [ ] Attach exact commit SHAs, changed files, test/build commands, and any infrastructure limitations.
- [ ] Do not call the branch READY unless the focused tests, full unit tests, lint, and debug compilation all pass on the exact head.
