# Wearable Secure Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real authenticated and encrypted Sentinel channel between Android and Wear OS without allowing Data Layer reachability or Ed25519 verification alone to publish `WEARABLE_RUNTIME=CONNECTED`.

**Architecture:** Data Layer remains transport only. Existing Ed25519 verification, replay guard, pairing policy, session authority, and message guard remain authoritative for identity/session authorization. Google Tink HPKE supplies confidentiality. Protocol v2 signs both HPKE public-key fingerprints. Runtime truth becomes connected only after platform reachability, signature verification, replay acceptance, explicit pairing confirmation, session activation, secure-channel establishment, and authenticated liveness.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1, Google Play services Wearable 20.0.1, Google Tink 1.23.0, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-10-17-wearable-secure-channel-design.md`

## Global Constraints

- Pin Tink `1.23.0`; no dynamic versions.
- HPKE profile: `DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`.
- Ed25519 is the durable trust anchor; HPKE does not authenticate identity by itself.
- Protocol v1 never upgrades silently to privileged v2.
- Data Layer association, `ping`, or `pong` never publishes `CONNECTED`.
- Replay nonce is consumed only after signature and pairing/session policy checks.
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

---

### Task 1: Tink HPKE boundary

**Files:**
- Modify: `native-android-app/wearable-security/build.gradle`
- Create: `native-android-app/wearable-security/src/main/kotlin/com/sentinel/quantum/wearable/security/WearableHpke.kt`
- Create: `native-android-app/wearable-security/src/test/kotlin/com/sentinel/quantum/wearable/security/WearableHpkeTest.kt`

**Interfaces:** Produce encapsulated `WearableHpkeKeyPair`, `WearableHpkePublicKey`, sender/recipient primitives, public-key fingerprinting, and `encrypt/decrypt` with `contextInfo`.

- [ ] Write failing tests: round trip, wrong key, wrong context, oversized input, no raw private-key API.
- [ ] Run: `cd native-android-app && ./gradlew :wearable-security:test --tests '*WearableHpkeTest*'` → expected RED.
- [ ] Add `com.google.crypto.tink:tink:1.23.0`; implement the exact HPKE profile and encapsulate private key handles.
- [ ] Re-run targeted test → PASS; then `./gradlew :wearable-security:test` → PASS.
- [ ] Commit: `feat(wearable): add Tink HPKE crypto boundary`.

### Task 2: Protocol-v2 signed transcript

**Files:**
- Modify: `wearable-security/.../VerifiedWearableHandshake.kt`
- Modify: `wearable-security/.../Ed25519WearableHandshakeVerifier.kt`
- Modify tests + test fixture in `wearable-security`.

**Interfaces:** Add `phoneHpkePublicKeyFingerprintSha256` and `wearableHpkePublicKeyFingerprintSha256` to v2 candidate/proof.

- [ ] Write failing tests proving either HPKE fingerprint changes transcript bytes, v1 cannot authenticate as v2, malformed fingerprints fail, delimiter/UTF-8 guards remain.
- [ ] Run verifier tests → expected RED.
- [ ] Extend candidate, verified proof, fixture, and canonical codec; require secure-session protocol `>= 2`.
- [ ] Run targeted + full `:wearable-security:test` → PASS.
- [ ] Commit: `feat(wearable): bind HPKE keys into handshake v2`.

### Task 3: Watch identity and secure key material

**Files:**
- Modify: `native-android-app/wear-app/build.gradle`
- Create: `native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/WearableSecureIdentityStore.kt`
- Create: `native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/WearableSecureHandshakeResponder.kt`
- Add wear-app tests under its supported unit-test layout.

**Interfaces:** Produce a stable Ed25519 public identity/fingerprint and session-scoped HPKE public key; sign canonical v2 transcripts; never expose private key bytes.

- [ ] Write failing tests: stable identity across store reload, private key never exported, fresh HPKE session key on new session, transcript signature verifies, identity reset requires re-pairing.
- [ ] Run wear-app unit tests → expected RED.
- [ ] Implement Android Keystore-backed Ed25519 identity storage where platform support allows; keep HPKE session material scoped to the active session and use Task 1 abstraction.
- [ ] Run targeted tests and `:wear-app:assembleDebug` when dependency resolution is available.
- [ ] Commit: `feat(wearable): add watch secure identity and handshake responder`.

### Task 4: Handset secure handshake coordinator

**Files:**
- Create: `app/.../security/WearableSecureHandshakeCoordinator.kt`
- Create: `app/src/test/.../WearableSecureHandshakeCoordinatorTest.kt`

**Interfaces:** Consume Ed25519 verifier, replay guard, pairing policy, session authority, phone/watch HPKE bindings. Produce `PENDING_CONFIRMATION`, `ACTIVE`, or fail-closed rejection.

- [ ] Write failing tests: invalid signature does not burn nonce; missing confirmation stays pending; replay/stale fails; identity/key/session/capability mismatch fails; v1 fails; valid v2 activates authority.
- [ ] Run targeted app test → expected RED.
- [ ] Implement exact acceptance order: verify signature → pairing/session policy → consume replay challenge → activate session authority → return ACTIVE metadata.
- [ ] Run targeted + full app unit tests → PASS.
- [ ] Commit: `feat(wearable): orchestrate authenticated secure handshake`.

### Task 5: Encrypted session messages

**Files:**
- Create: `app/.../security/WearableSecureChannel.kt`
- Create: `app/src/test/.../WearableSecureChannelTest.kt`
- Modify `WearableMessageGuard.kt` only if a narrow decode boundary is required.

**Interfaces:** `seal(payload, envelope, senderRole)` and `open(ciphertext, expectedCapability, senderRole, nowMs)` using Task 1 HPKE and authoritative `WearableSession`.

- [ ] Write failing tests: round trip, wrong session, role substitution, capability substitution, duplicate/out-of-order sequence, stale/future timestamp, malformed/oversized ciphertext.
- [ ] Run targeted test → expected RED.
- [ ] Implement canonical context domain `sentinel-wearable-channel-v2` binding protocol, session, sender, recipient, capability/`NONE`; decrypt first, then enforce `WearableMessageGuard`, dispatch only after `ACCEPT`.
- [ ] Run targeted + full app unit tests → PASS.
- [ ] Commit: `feat(wearable): add HPKE protected message channel`.

### Task 6: Shared Wear OS v2 transport contract

**Files:**
- Create: `native-android-app/wearable-contract/src/main/kotlin/com/sentinel/quantum/wearable/contract/WearableProtocolV2.kt`
- Modify: handset `SentinelWearListenerService.kt`, `WearOsDataLayerAdapter.kt`, wear-app `SentinelWearListenerService.kt`
- Modify app/wear-app Gradle files to consume `:wearable-contract` as needed.
- Modify: `scripts/wearable-roundtrip-wiring.test.js`

**Interfaces:** Shared paths under `/sentinel/v2/secure/`: `challenge`, `handshake`, `liveness`, `message`; explicit schema version and bounded frame sizes.

- [ ] Extend wiring test first: both apps use shared constants; platform ping/pong stays separate; listeners cannot call `publishConnected`; all frame sizes bounded.
- [ ] Run Node test → expected RED.
- [ ] Implement shared codec/routing; listeners only validate path/bounds and delegate security decisions.
- [ ] Run Node gate + available Android/Wear unit/build tasks → PASS where executable; report environment/network failures separately.
- [ ] Commit: `feat(wearable): route secure protocol over Wear OS Data Layer`.

### Task 7: Runtime truth, authenticated liveness, revocation

**Files:**
- Modify: `app/.../security/WearableRuntimeCoordinator.kt`
- Create: `app/.../security/WearableSecureSessionRuntime.kt`
- Create: `app/src/test/.../WearableSecureSessionRuntimeTest.kt`

**Interfaces:** Produce short-lived `WearableChannelProof.ACTIVE` only from authoritative secure session + authenticated traffic; revoke immediately on disconnect/stale/auth failure.

- [ ] Write failing tests: pong alone stays locked; secure handshake + authenticated liveness can publish connected observation; pong cannot refresh TTL; TTL expiry revokes; disconnect revokes; new process starts locked; identity/decryption failure revokes.
- [ ] Run targeted test → expected RED.
- [ ] Implement one short liveness TTL (use existing product constant if one exists; otherwise `30_000L`); refresh only from authenticated secure traffic; overwrite truth immediately with `LOCKED_EXTERNAL_DEPENDENCY / IDENTITY_BINDING_MISSING` on revocation.
- [ ] Run targeted + full app unit tests → PASS.
- [ ] Commit: `feat(wearable): gate runtime truth on authenticated liveness`.

### Task 8: Release gates and end-to-end qualification

**Files:**
- Modify: `.github/workflows/product-capability-truth.yml`
- Modify the existing Android/Wear instrumentation workflow selected by repository conventions.
- Modify: `scripts/wearable-production-wiring.test.js`, `scripts/wearable-roundtrip-wiring.test.js`
- Add paired emulator instrumentation tests under existing layouts.

**Interfaces:** CI proof for pinned Tink, v2 key binding, secure-session tests, no direct listener truth promotion, paired-emulator qualification.

- [ ] Write failing release-gate assertions first.
- [ ] Run Node gates + targeted Gradle tests locally where possible and record infrastructure failures separately.
- [ ] Wire CI: `:wearable-security:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:wear-app:assembleDebug`, paired emulator test; preserve Phone Core gates.
- [ ] Run full verification when environment permits. If GitHub still shows `runner_id=0` / `steps=[]`, keep PR draft and report CI as infrastructure-blocked.
- [ ] Update #1699 truthfully; keep static wearable capability locked until paired emulator + physical Wear OS evidence.
- [ ] Commit: `test(wearable): gate secure channel release evidence`.

## Completion Criteria

1. Tink HPKE 1.23.0 is pinned and tested.
2. v2 Ed25519 transcript binds both HPKE peer-key fingerprints.
3. Watch has a protected Ed25519 identity and session-scoped HPKE material.
4. Invalid signatures cannot burn valid replay challenges.
5. Explicit user pairing confirmation precedes authority activation.
6. Data Layer listeners transport only bounded protocol frames.
7. Encrypted messages are context-bound and still pass `WearableMessageGuard` before dispatch.
8. Only authenticated secure liveness can create/refresh `WearableChannelProof.ACTIVE`.
9. Disconnect/stale/auth failure immediately revokes product truth.
10. Static registry stays locked until runtime + physical evidence exists.
11. Phone Core latency-critical paths remain unaffected.
