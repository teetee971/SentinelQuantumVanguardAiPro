# Wearable Secure Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real authenticated and encrypted Sentinel channel between the Android handset and Wear OS companion, without allowing Data Layer reachability or Ed25519 verification alone to publish `WEARABLE_RUNTIME=CONNECTED`.

**Architecture:** Keep Wear OS Data Layer as the transport only. Use the existing Ed25519 verifier, replay guard, pairing policy, session authority, and message guard for identity/session authorization, and add Google Tink HPKE for session confidentiality. Protocol v2 binds both HPKE public-key fingerprints into the canonical signed transcript; runtime truth becomes connected only after platform reachability, signature verification, replay acceptance, explicit pairing confirmation, authoritative session activation, and successful secure-channel establishment.

**Tech Stack:** Kotlin 2.4.20, Android Gradle Plugin 9.4.1, Google Play services Wearable 20.0.1, Google Tink 1.23.0, JUnit 4.13.2, Android/Wear OS Data Layer.

**Spec:** `docs/superpowers/specs/2026-10-17-wearable-secure-channel-design.md`

## Global Constraints

- Pin Tink to `1.23.0`; dynamic dependency versions are forbidden.
- Use HPKE profile `DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`.
- Ed25519 remains the durable peer-authentication anchor; HPKE never replaces identity verification.
- Protocol v1 must never be silently promoted to a secure privileged session.
- A Data Layer node, Bluetooth bond, association, `ping`, or `pong` never publishes `CONNECTED`.
- Replay challenge consumption occurs only after Ed25519 verification and pairing/session policy checks required by the spec.
- Private HPKE key material must not be logged, surfaced through product truth, stored in preferences, or sent over Data Layer.
- `WearableMessageGuard` remains mandatory after successful decryption.
- Wearable work must stay outside `CallScreeningService` and other latency-critical Telecom callbacks.
- Canonical registry remains `LOCKED_EXTERNAL_DEPENDENCY / IDENTITY_BINDING_MISSING` until physical validation and runtime evidence exist.
- No paid infrastructure is introduced.

## Review Focus

- Malformed or oversized HPKE/public-key/ciphertext material must fail closed without allocation explosions or handler dispatch — covered in Tasks 1, 4, and 6.
- Signature-valid but replayed/stale challenge material must not activate or refresh a session — covered in Task 3.
- Context substitution across session, sender role, recipient role, or capability must fail decryption/validation — covered in Task 4.
- Process death/restart must not resurrect an active channel from stale in-memory state — covered in Tasks 5 and 6.
- Data Layer disconnect or authenticated-channel TTL expiry must revoke previous `CONNECTED` truth immediately — covered in Task 6.

---

### Task 1: HPKE crypto boundary in `wearable-security`

**Files:**
- Modify: `native-android-app/wearable-security/build.gradle`
- Create: `native-android-app/wearable-security/src/main/kotlin/com/sentinel/quantum/wearable/security/WearableHpke.kt`
- Create: `native-android-app/wearable-security/src/test/kotlin/com/sentinel/quantum/wearable/security/WearableHpkeTest.kt`

**Interfaces:**
- Consumes: Google Tink `1.23.0` primitives.
- Produces: `WearableHpkeKeyPair`, `WearableHpkePublicKey`, `WearableHpkeRecipient`, and `WearableHpkeSender` with bounded public-key import/export and `encrypt(plaintext: ByteArray, contextInfo: ByteArray): ByteArray` / `decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray`.

- [ ] **Step 1: Write failing JVM tests**
  - `encryptDecryptRoundTripUsesRequiredHpkeProfile`
  - `wrongRecipientKeyFails`
  - `wrongContextInfoFails`
  - `oversizedPublicKeyOrCiphertextIsRejected`
  - `privateKeyMaterialIsNotExposedByPublicApi`

- [ ] **Step 2: Run RED verification**
  Run: `cd native-android-app && ./gradlew :wearable-security:test --tests '*WearableHpkeTest*'`
  Expected: FAIL because the HPKE boundary and Tink dependency do not exist.

- [ ] **Step 3: Pin dependency and implement minimal HPKE boundary**
  Add `com.google.crypto.tink:tink:1.23.0` to `wearable-security`. Register Tink primitives once inside the crypto boundary. Use the exact HPKE profile from Global Constraints. Keep private-key-bearing Tink handles encapsulated; expose only the public-key representation needed by the peer.

- [ ] **Step 4: Run GREEN verification**
  Run: `cd native-android-app && ./gradlew :wearable-security:test --tests '*WearableHpkeTest*'`
  Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "feat(wearable): add Tink HPKE crypto boundary"`

### Task 2: Protocol-v2 transcript binding

**Files:**
- Modify: `native-android-app/wearable-security/src/main/kotlin/com/sentinel/quantum/wearable/security/VerifiedWearableHandshake.kt`
- Modify: `native-android-app/wearable-security/src/main/kotlin/com/sentinel/quantum/wearable/security/Ed25519WearableHandshakeVerifier.kt`
- Modify: `native-android-app/wearable-security/src/test/kotlin/com/sentinel/quantum/wearable/security/Ed25519WearableHandshakeVerifierTest.kt`
- Modify: `native-android-app/wearable-security/src/testFixtures/kotlin/com/sentinel/quantum/wearable/security/VerifiedWearableHandshakeFixture.kt`

**Interfaces:**
- Consumes: Task 1 public-key fingerprints.
- Produces: protocol-v2 `WearableHandshakeCandidate` and `VerifiedWearableHandshake` containing `phoneHpkePublicKeyFingerprintSha256` and `wearableHpkePublicKeyFingerprintSha256`.

- [ ] **Step 1: Write failing tests**
  Add tests proving transcript bytes change when either HPKE public-key fingerprint changes, protocol v1 cannot authenticate as v2, delimiter/UTF-8 protections remain intact, and malformed/non-64-hex fingerprints are rejected.

- [ ] **Step 2: Run RED verification**
  Run: `cd native-android-app && ./gradlew :wearable-security:test --tests '*Ed25519WearableHandshakeVerifierTest*'`
  Expected: FAIL because protocol-v2 key-binding fields are absent.

- [ ] **Step 3: Extend candidate, verified proof, and canonical transcript**
  Exact new fields:
  - `phoneHpkePublicKeyFingerprintSha256: String`
  - `wearableHpkePublicKeyFingerprintSha256: String`
  Require protocol version `>= 2` for secure-session candidates. Include both fields in deterministic canonical encoding after `issuedAtMs` with explicit field order documented in the codec.

- [ ] **Step 4: Run GREEN verification**
  Run the targeted verifier tests and then `cd native-android-app && ./gradlew :wearable-security:test`.
  Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "feat(wearable): bind HPKE keys into handshake v2"`

### Task 3: Secure handshake orchestration on handset

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/WearableSecureHandshakeCoordinator.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/WearableSecureHandshakeCoordinatorTest.kt`
- Reuse: `WearableHandshakeReplayGuard.kt`, `WearableHandshakePolicy.kt`, `WearableSessionAuthority.kt`, `WearableTrustPolicy.kt`

**Interfaces:**
- Consumes: `WearableHandshakeVerifier.verify(candidate)`, `WearableHandshakeReplayGuard.consume(candidate)`, `WearableHandshakePolicy.evaluate(...)`, `WearableSessionAuthority.activate(...)`.
- Produces: `WearableSecureHandshakeResult` with states `PENDING_CONFIRMATION`, `ACTIVE`, or a fail-closed rejection reason; on `ACTIVE`, returns authoritative `WearableSession` plus HPKE peer binding metadata, never a fabricated `WearableChannelProof` from UI inputs.

- [ ] **Step 1: Write failing tests**
  Cover: invalid Ed25519 candidate does not consume challenge; valid signature + missing confirmation stays pending; stale/replayed challenge fails; identity/key/session/capability mismatch fails; successful path activates authority only after all checks; protocol v1 is rejected for secure activation.

- [ ] **Step 2: Run RED verification**
  Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests '*WearableSecureHandshakeCoordinatorTest*'`
  Expected: FAIL because coordinator is absent.

- [ ] **Step 3: Implement coordinator with fixed order**
  Required acceptance order:
  1. Ed25519 verifier returns proof.
  2. `WearableHandshakePolicy` accepts expected identity/session/confirmation.
  3. replay guard consumes the candidate.
  4. `WearableSessionAuthority.activate(...)` returns `ACTIVATED`.
  5. coordinator returns `ACTIVE` with immutable session/binding metadata.
  Never consume the replay challenge before step 1.

- [ ] **Step 4: Run GREEN verification**
  Run targeted app unit tests, then the complete app unit-test task.
  Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "feat(wearable): orchestrate authenticated secure handshake"`

### Task 4: Encrypted wearable message channel and context binding

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/WearableSecureChannel.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/WearableSecureChannelTest.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/WearableMessageGuard.kt` only if a narrowly required decode boundary is missing.

**Interfaces:**
- Consumes: Task 1 HPKE primitives, Task 3 authoritative `WearableSession`, existing `WearableMessageEnvelope` and `WearableMessageGuard`.
- Produces: `seal(payload: ByteArray, envelope: WearableMessageEnvelope, senderRole: WearablePeerRole): ByteArray` and `open(ciphertext: ByteArray, expectedCapability: WearableCapability?, senderRole: WearablePeerRole, nowMs: Long): WearableSecureMessageResult`.

- [ ] **Step 1: Write failing tests**
  Prove round-trip, wrong session context fails, sender/recipient role substitution fails, capability substitution fails, duplicate/out-of-order sequence is rejected after decryption, expired/future timestamp fails, malformed/oversized ciphertext never reaches handler code.

- [ ] **Step 2: Run RED verification**
  Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests '*WearableSecureChannelTest*'`
  Expected: FAIL because secure channel is absent.

- [ ] **Step 3: Implement canonical HPKE `contextInfo`**
  Create enum `WearablePeerRole { PHONE, WEARABLE }`. Canonical context must bind domain `sentinel-wearable-channel-v2`, protocol version, session ID, sender role, recipient role, and capability name or literal `NONE`. Decrypt before message-guard evaluation; dispatch only after `WearableMessageDecision.ACCEPT`.

- [ ] **Step 4: Run GREEN verification**
  Run targeted test, then full app unit tests.
  Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "feat(wearable): add HPKE protected message channel"`

### Task 5: Wear OS protocol transport for handshake and encrypted frames

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelWearListenerService.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/WearOsDataLayerAdapter.kt`
- Modify: `native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/SentinelWearListenerService.kt`
- Create: `native-android-app/wearable-contract/src/main/kotlin/com/sentinel/quantum/wearable/contract/WearableProtocolV2.kt`
- Modify: `native-android-app/app/build.gradle`
- Modify: `native-android-app/wear-app/build.gradle`
- Test: `scripts/wearable-roundtrip-wiring.test.js`
- Create appropriate JVM/unit tests in handset and wear-app modules where supported.

**Interfaces:**
- Consumes: Tasks 2–4 protocol/session artifacts.
- Produces: fixed Data Layer paths and bounded frame codec for challenge, handshake response, secure liveness, and encrypted application messages.

- [ ] **Step 1: Write failing contract/wiring tests**
  Assert both app modules consume `:wearable-contract`; exact v2 paths are shared constants; frame sizes are bounded; platform `ping/pong` remains separate from secure paths; neither listener can call `publishConnected` directly.

- [ ] **Step 2: Run RED verification**
  Run: `node --test scripts/wearable-roundtrip-wiring.test.js`
  Expected: FAIL because shared secure-protocol paths/codecs do not exist.

- [ ] **Step 3: Implement shared protocol contract and transport routing**
  Define exact paths under `/sentinel/v2/secure/` for `challenge`, `handshake`, `liveness`, and `message`. Use bounded binary/JSON framing with explicit schema version and maximum payload sizes. Keep all crypto decisions outside the listeners; listeners parse bounds/path, then delegate.

- [ ] **Step 4: Run GREEN verification**
  Run Node wiring test plus targeted Android/Wear module tests/build compilation tasks available locally.
  Expected: PASS for executable tests; if Gradle cannot resolve due environment/network, report that separately and do not claim green.

- [ ] **Step 5: Commit**
  `git commit -m "feat(wearable): route secure protocol over Wear OS Data Layer"`

### Task 6: Runtime truth activation, liveness, and revocation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/WearableRuntimeCoordinator.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/WearableSecureSessionRuntime.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/WearableSecureSessionRuntimeTest.kt`
- Modify: `config/integration-capabilities.json` only if diagnostic wording changes; do not change the capability to `CONNECTED` statically.

**Interfaces:**
- Consumes: active authoritative session from Task 3 and authenticated secure traffic from Task 4/5.
- Produces: short-lived `WearableChannelProof(ACTIVE, lastProofAtMs, ttlMs)` only after secure establishment/liveness; immediate locked replacement on disconnect/revoke/auth failure/staleness.

- [ ] **Step 1: Write failing tests**
  Cover: platform pong alone stays locked; successful secure handshake + authenticated liveness publishes connected observation; plain pong cannot refresh authenticated TTL; TTL expiry revokes; disconnect revokes immediately; process-new runtime starts locked; identity/key/decryption failure revokes without touching Phone Core.

- [ ] **Step 2: Run RED verification**
  Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests '*WearableSecureSessionRuntimeTest*'`
  Expected: FAIL because secure-session runtime is absent.

- [ ] **Step 3: Implement secure-session runtime**
  Choose a single short TTL constant in this class (recommended `30_000L` unless an existing product constant already governs wearable liveness). Refresh only from authenticated secure-channel traffic. On every revocation path, overwrite prior truth with `LOCKED_EXTERNAL_DEPENDENCY / IDENTITY_BINDING_MISSING` immediately.

- [ ] **Step 4: Run GREEN verification**
  Run targeted and full app unit tests.
  Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "feat(wearable): gate runtime truth on authenticated liveness"`

### Task 7: Release gates and end-to-end verification

**Files:**
- Modify: `.github/workflows/product-capability-truth.yml`
- Modify: `.github/workflows/android-instrumentation.yml` or the existing Wear/Android instrumentation workflow selected by repository conventions.
- Modify: `scripts/wearable-production-wiring.test.js`
- Modify: `scripts/wearable-roundtrip-wiring.test.js`
- Add instrumentation tests under the existing Android/Wear test layout for paired emulator validation.
- Update: `docs/superpowers/specs/2026-10-17-wearable-secure-channel-design.md` only for implementation-confirmed deviations, not status promotion.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: CI gates proving dependency pinning, protocol-v2 key binding, no direct `publishConnected` from Data Layer listeners, secure-channel tests, and paired-emulator qualification when runners are functional.

- [ ] **Step 1: Write failing release-gate assertions**
  Require Tink `1.23.0`, HPKE profile identifier, v2 transcript key bindings, secure runtime revocation tests, and shared secure Data Layer paths.

- [ ] **Step 2: Run RED verification locally where possible**
  Run Node gates and targeted Gradle tests. Record any infrastructure failure distinctly from code failures.

- [ ] **Step 3: Wire CI jobs**
  Add wearable-security JVM tests, app unit tests, wear-app compilation/test task, and paired-emulator test job without removing existing Phone Core qualification. Keep physical-device validation outside automated `CONNECTED` claims.

- [ ] **Step 4: Run complete repository verification**
  Minimum commands when environment permits:
  - `node --test scripts/wearable-production-wiring.test.js scripts/wearable-roundtrip-wiring.test.js`
  - `cd native-android-app && ./gradlew :wearable-security:test :app:testDebugUnitTest :app:assembleDebug :wear-app:assembleDebug`
  - repository canonical integration/product-truth test command.
  Expected: all executable checks pass. If GitHub still reports `runner_id=0` / `steps=[]`, leave PR draft and report CI as infrastructure-blocked.

- [ ] **Step 5: Update PR #1699 truthfully and commit**
  Keep `wearable_runtime` locked until paired emulator plus physical Wear OS validation exists. Document exactly which automated evidence executed and which remains unproven.
  `git commit -m "test(wearable): gate secure channel release evidence"`

## Completion Criteria

The implementation phase is complete only when:

1. Tink HPKE `1.23.0` is pinned and JVM-tested.
2. Ed25519 protocol v2 binds both HPKE peer-key fingerprints.
3. Invalid signatures cannot burn valid replay challenges.
4. Explicit pairing confirmation is required before authority activation.
5. Data Layer listeners only transport bounded protocol frames.
6. Application messages are encrypted and context-bound, then checked by `WearableMessageGuard` before dispatch.
7. Only authenticated secure liveness can create/refresh `WearableChannelProof.ACTIVE`.
8. Disconnect/stale/auth failure immediately revokes product truth.
9. `WEARABLE_RUNTIME` remains statically locked until runtime/physical evidence exists.
10. Phone Core latency-critical paths remain untouched by wearable crypto work.
