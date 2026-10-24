# Sentinel Offline Mesh Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Audit and harden the phase-1 authenticated BLE transport, qualify every available Android build/test boundary, and keep the unrelated APK lint repair isolated.

**Architecture:** Keep the existing pure Kotlin frame/ACK core and Android GATT adapters. Harden the secure channel with strict P-256 identity validation, dynamic trust/revocation callbacks, session-bound lifecycle, synchronized state transitions, bounded handshake expiry, and explicit fail-closed behavior. The `SelectedApkInspector.kt` API-level repair is performed in a separate worktree/branch and is never merged into the Offline Mesh commit.

**Tech Stack:** Kotlin, Android Bluetooth GATT APIs, AndroidKeyStore bridge, JCA ECDH/ECDSA/HKDF/AES-GCM, JUnit 4, AndroidX instrumentation, Gradle Android plugin, JDK 17.

**Spec:** `docs/superpowers/specs/2026-10-24-sentinel-offline-mesh-secure-transport-design.md`

## Global Constraints

- Work only from `d7215a9eb683180c21252df223b17a214c9b9f34` on `feature/sentinel-offline-mesh-foundation-20261024`.
- Never modify `main`; keep PR #1739 open and draft.
- Do not modify Phone Core, SMS, MMS, VPN, or unrelated APK code in the BLE branch.
- Do not call the JCA handshake Noise; report the exact guarantees and gaps.
- Private data stays locked unless the identity, peer trust, session, and encryption gates all succeed.
- Do not put private keys, trust secrets, or test credentials in Git.
- Physical BLE results require a real device log; compilation is not a physical result.

## Review Focus

- Non-P-256 or malformed public keys must fail before ECDH/signature work.
- A signer/public-key mismatch, revoked peer, altered transcript, bad finish MAC, or bad GCM tag must leave the session locked.
- A handshake must expire and a reconnect must create fresh ephemeral keys, counters, and direction keys.
- Concurrent offer/receive/ACK/tick calls must not duplicate tickets, bypass authentication, or deliver plaintext twice.
- Stale peer generations, old GATT objects, old CCCD callbacks, and delayed radio callbacks must not affect a new session.
- MTU overflow, malformed controls, duplicate fragments, stale ACKs, and retry exhaustion must fail closed without unbounded allocation.

### Task 1: Crypto audit and hardening

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattCrypto.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattSecureTransport.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattOwnerIdentity.kt` only if the identity boundary requires it
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/OfflineMeshGattCryptoTest.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/OfflineMeshGattSecureTransportTest.kt`

- [ ] Add failing tests for non-P-256 keys, signer/public mismatch, dynamic revocation, handshake expiry, fresh keys after reset, concurrent secure-channel calls, and malformed handshake lengths.
- [ ] Run only these tests and record the expected RED result.
- [ ] Enforce exact P-256 public-key parameters and validate the signer against its advertised public key at identity construction.
- [ ] Add an explicit trust/revocation callback contract; preserve default deny and document that persistence is caller-owned.
- [ ] Add bounded handshake expiry and reset all session state on timeout, authentication failure, radio interruption, or reconnect.
- [ ] Synchronize the secure wrapper’s stateful public operations and preserve the raw transport’s generation checks.
- [ ] Verify direction-key, counter/nonce, replay, transcript, and GCM-tag properties with deterministic assertions.
- [ ] Run the crypto/security tests and the existing transport tests to GREEN.

### Task 2: GATT and concurrency hardening

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattClient.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattServer.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattPeerSessions.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/OfflineMeshGattFrame.kt` only if control/MTU validation needs a bounded fix
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/OfflineMeshGattPeerSessionsTest.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/OfflineMeshGattFrameTest.kt`
- Test: new deterministic concurrency/security tests in the existing Offline Mesh test package

- [ ] Add tests for concurrent offers, one in-flight GATT write/notification per peer, stale ACKs, duplicated fragments, MTU overflow, disconnect during retry, and reconnect isolation.
- [ ] Run those tests RED before implementation.
- [ ] Ensure every Android callback checks exact epoch/generation, GATT object, characteristic/descriptor identity, and active peer token before state mutation.
- [ ] Ensure CCCD disable, disconnect, notification failure, permission loss, and retry exhaustion revoke private-data authorization.
- [ ] Ensure malformed or oversized control/data frames do not allocate beyond fixed limits or invoke application callbacks.
- [ ] Re-run targeted BLE tests and inspect the changed-file boundary.

### Task 3: APK lint isolation

**Separate worktree/branch:** `fix/selected-apk-inspector-api28-20261024`, based on `d7215a9...`.

**Files:**
- Modify only: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SelectedApkInspector.kt`
- Test/verification: existing APK inspection tests and `:app:lintDebug`

- [ ] Add an API 28 guard around `SigningInfo` calls without changing APK semantics.
- [ ] Run focused compile/lint/tests in the separate worktree.
- [ ] Do not merge or cherry-pick this branch into the Offline Mesh branch unless explicitly requested later.

### Task 4: Reports and qualification

**Files:**
- Create: `docs/superpowers/reports/2026-10-24-sentinel-offline-mesh-phase2-crypto-audit.md`
- Create: `docs/superpowers/reports/2026-10-24-sentinel-offline-mesh-phase2-android-regression.md`
- Create: `docs/superpowers/reports/2026-10-24-sentinel-offline-mesh-s24-physical-procedure.md`
- Modify: `docs/superpowers/reports/2026-10-24-sentinel-offline-mesh-phase1-qualification.md` only to link Phase 2 evidence

- [ ] Record exact commands, exit codes, test counts, lint blockers, artifact paths, and `git diff --check` output.
- [ ] Compile unit and instrumented tests; execute instrumentation only if an emulator/device is actually available.
- [ ] Verify manifest BLE permissions without modifying unrelated permissions.
- [ ] Write a reproducible two-device/Samsung Galaxy S24+ procedure with expected logs, negative identity/revocation cases, MTU/loss/reconnect cases, and evidence retention.
- [ ] Report verdicts independently: `CODE_COMPLETE`, `UNIT_TESTS_PASS`, `INSTRUMENTED_PASS`, `CRYPTO_REVIEW_PASS`, `PHYSICAL_BLE_PASS`, and final `READY` only if every gate has evidence.

### Task 5: Final handoff

- [ ] Run full targeted and complete unit regressions on the exact final Offline Mesh SHA.
- [ ] Run Kotlin compile, debug APK assembly, instrumented compilation, static analysis, and `git diff --check`.
- [ ] Push only the Offline Mesh commit(s) to the authorized branch.
- [ ] Update PR #1739 evidence while preserving `draft=true`; do not merge.
- [ ] Report remaining limitations explicitly and use `LIMITED`, `BLOCKED`, or `NOT_READY` as evidence requires.
