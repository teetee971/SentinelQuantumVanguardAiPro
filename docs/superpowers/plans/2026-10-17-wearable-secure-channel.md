# Wearable Secure Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real authenticated and encrypted Sentinel channel between Android and Wear OS without allowing Data Layer reachability or Ed25519 verification alone to publish `WEARABLE_RUNTIME=CONNECTED`.

**Architecture:** Data Layer remains transport only. Existing Ed25519 verification, replay guard, pairing policy, session authority, and message guard remain authoritative for identity/session authorization. Google Tink HPKE supplies confidentiality. Protocol v2 signs both HPKE public-key fingerprints. Runtime truth becomes connected only after platform reachability, signature verification, replay acceptance, explicit pairing confirmation, durable identity binding, session activation, secure-channel establishment, and authenticated liveness.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1, Google Play services Wearable 20.0.1, Google Tink 1.23.0, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-10-17-wearable-secure-channel-design.md`

## Global Constraints

- Pin Tink `1.23.0`; no dynamic versions.
- HPKE profile: `DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`.
- Ed25519 is the durable trust anchor; HPKE does not authenticate identity by itself.
- Protocol v1 never upgrades silently to privileged v2.
- Data Layer association, `ping`, or `pong` never publishes `CONNECTED`.
- Replay nonce is consumed only after signature and pairing/session policy checks.
- First pairing must require explicit user confirmation; persistence, replay consumption and authority activation must remain transactionally consistent and fail closed on partial failure.
- No raw HPKE private key in logs, preferences, product truth, or Data Layer.
- `WearableMessageGuard` remains mandatory after decryption.
- No wearable crypto in CallScreeningService or other Telecom latency-critical callbacks.
- Canonical registry remains `LOCKED_EXTERNAL_DEPENDENCY / IDENTITY_BINDING_MISSING` until runtime + physical evidence exists.
- No paid infrastructure.

## Review Focus

- Malformed/oversized public keys or ciphertext fail closed without handler dispatch.
- Replayed/stale signed handshakes cannot activate or refresh sessions.
- Session/role/capability context substitution fails.
- Process restart cannot resurrect an active session.
- Disconnect or authenticated-liveness expiry immediately revokes previous connected truth.
- First-contact self-signed identity cannot become a trusted pairing until explicit user confirmation is durably stored.
- Partial/corrupted watch identity persistence must fail closed instead of silently rotating the durable Ed25519 identity.
- Exceptions inside Data Layer secure-frame handlers must not crash either listener service.

---

### Task 1: Tink HPKE boundary

**Files:**
- Modify: `native-android-app/wearable-security/build.gradle`
- Create: `native-android-app/wearable-security/src/main/kotlin/com/sentinel/quantum/wearable/security/WearableHpke.kt`
- Create: `native-android-app/wearable-security/src/test/kotlin/com/sentinel/quantum/wearable/security/WearableHpkeTest.kt`

**Interfaces:** Produce encapsulated `WearableHpkeKeyPair`, `WearableHpkePublicKey`, sender/recipient primitives, public-key fingerprinting, and `encrypt/decrypt` with `contextInfo`.

- [x] Write failing tests: round trip, wrong key, wrong context, oversized input, no raw private-key API, stable public fingerprint.
- [x] Observe RED locally for missing HPKE types/fingerprint contract.
- [x] Add `com.google.crypto.tink:tink:1.23.0`; implement the exact HPKE profile and encapsulate private key handles.
- [ ] Run real `:wearable-security:test` against Tink 1.23.0. Blocked by current runner/dependency environment; do not treat stub compilation as GREEN.

### Task 2: Protocol-v2 signed transcript

**Files:** `VerifiedWearableHandshake.kt`, `Ed25519WearableHandshakeVerifier.kt`, tests and fixture.

- [x] Add v2 tests for both HPKE fingerprints, malformed bindings and v1 separation.
- [x] Preserve v1 canonical transcript for compatibility while forbidding HPKE bindings on v1.
- [x] Require both lowercase SHA-256 HPKE fingerprints on protocol v2.
- [x] Verify v2 behavior with local JDK Ed25519 harness; substitution fails and v1 replay compatibility remains.
- [ ] Run repository Gradle/JUnit suite when runners are executable.

### Task 3: Watch identity and secure key material

**Files:** wear-app Gradle, `WearableSecureIdentityStore.kt`, `WearableSecureHandshakeResponder.kt`, tests.

- [x] Define testable signing-identity backend and unit contracts.
- [x] Keep Ed25519 as identity anchor; because AndroidKeyStore does not guarantee Ed25519 generation across minSdk, wrap PKCS#8 material using non-exportable AES-256-GCM AndroidKeyStore key and wipe transient private buffers.
- [x] Add fresh session-scoped Tink HPKE material and duplex phone/watch public-key binding.
- [x] Fail closed on partial/malformed persisted identity state instead of silently generating a replacement identity.
- [x] Require successful durable reset metadata deletion before deleting the wrapping key.
- [x] Verify pure JVM identity/signature/session behavior locally where execution was available.
- [ ] Qualify AndroidKeyStore persistence and real Tink primitives on emulator/device.

### Task 4: Handset secure handshake coordinator

- [x] Add test contract for invalid-signature nonce preservation, confirmation gating, v1 rejection, HPKE substitution and replay.
- [x] Implement verify → policy → replay consume → authority activation ordering for already-bound identities.
- [x] Add first-pairing bootstrap with self-signed Ed25519 verification, fingerprint-bound confirmation, durable trust persistence and fail-closed rollback.
- [x] Prevent a concurrent replay loser from persisting a trust record.
- [x] Revoke activated authority if trust persistence subsequently fails.
- [x] Wire explicit first-pairing confirmation into the production app with fingerprint-bound Accept/Reject actions; rejection revokes the pending replay challenge without persisting trust.

### Task 5: Encrypted session messages

- [x] Add tests for round trip, context substitution, replay/order, expiry/future time and malformed/oversized ciphertext.
- [x] Implement binary bounded envelope + context domain `sentinel-wearable-channel-v2` binding protocol/session/direction/capability.
- [x] Decrypt before enforcing `WearableMessageGuard`; payload is returned only after `ACCEPT`.
- [ ] Execute real Tink round-trip through Gradle when dependency execution is available.

### Task 6: Shared Wear OS v2 transport contract

- [x] Create `WearableProtocolV2` with shared challenge/handshake/liveness/message paths, schema v2 and bounded frame decoding.
- [x] Add typed bounded challenge, handshake and liveness payload codecs.
- [x] Both phone and watch consume `:wearable-contract`.
- [x] Data Layer listeners decode bounded secure frames and delegate to fail-closed in-process inboxes; they never publish `CONNECTED`.
- [x] Contain secure handler exceptions at both phone and watch inbox boundaries.
- [x] Manifest routing accepts only the `/sentinel/` namespace; listeners still reject unknown paths.
- [x] Source/static gate covers challenge, handshake, liveness, disconnect and explicit pairing confirmation wiring.

### Task 7: Runtime truth, authenticated liveness, revocation

- [x] Add runtime tests for platform-only lock, authenticated liveness, pong non-refresh, expiry, disconnect/auth failure and process restart.
- [x] Implement short-lived 30s secure proof and explicit revocation path.
- [x] Route platform pong through the secure runtime so it cannot refresh authenticated TTL.
- [x] Persist only application-key identity bindings across restart; never restore active/session/replay state.
- [x] Install the source-level production secure transport composition: challenge → signed handshake → explicit first-pair confirmation/trusted reconnect → recurring HPKE liveness. Session keys remain process-memory-only.
- [x] Wire Data Layer disconnect into pending-challenge revocation, liveness shutdown and immediate authenticated-runtime revocation.
- [x] Refresh the watch-side secure-session TTL only after authenticated, monotonic liveness; replayed liveness does not extend authority.
- [ ] Prove the full source composition on a real paired emulator/device before changing customer-facing availability truth.

### Task 8: Release gates and end-to-end qualification

- [ ] Restore executable CI runners and run Tink/v2/pairing/liveness regression gates. Current Actions failures before steps are infrastructure failures, not test verdicts.
- [ ] Build phone + wear app and execute unit/instrumentation suites.
- [ ] Paired emulator handshake/replay/disconnect qualification.
- [ ] Physical S24+/Wear OS qualification.
- [ ] Keep PR draft and static registry locked until those proofs exist.

## Completion Criteria

1. Tink HPKE 1.23.0 is pinned and tested by real runtime tests.
2. v2 Ed25519 transcript binds both HPKE peer-key fingerprints.
3. Watch has a protected Ed25519 identity and session-scoped HPKE material.
4. First pairing cannot trust a presented identity until explicit user confirmation is durably stored.
5. Invalid signatures cannot burn valid replay challenges.
6. Explicit user pairing confirmation and durable trust persistence remain consistent with replay/session authority.
7. Data Layer listeners transport only bounded protocol frames and contain handler faults.
8. Encrypted messages are context-bound and still pass `WearableMessageGuard` before dispatch.
9. Only authenticated secure liveness can create/refresh `WearableChannelProof.ACTIVE`.
10. Disconnect/stale/auth failure immediately revokes product truth.
11. Static registry stays locked until runtime + physical evidence exists.
12. Phone Core latency-critical paths remain unaffected.
