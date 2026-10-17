# Sentinel Wearable Secure Channel — Design

Date: 2026-10-17

## Goal

Provide a real authenticated and encrypted Sentinel channel between the Android handset app and the Wear OS companion without treating Wear OS Data Layer reachability as authentication.

The secure-channel design must preserve existing product-truth rules: `WEARABLE_RUNTIME` remains locked until both peer identity and a fresh encrypted session are established. A platform `ping/pong`, Bluetooth bond, companion association, or Data Layer node is never sufficient to publish `CONNECTED`.

## Constraints

- Android handset remains the primary product runtime.
- Wear OS is the first concrete wearable platform adapter.
- The design must remain vendor-neutral above the Data Layer transport boundary.
- No paid infrastructure or external server is required for the handset↔watch channel.
- No custom cryptographic primitive, home-grown KDF, or ad-hoc AEAD construction.
- Existing Ed25519 identity verification, replay protection, `WearableHandshakePolicy`, `WearableSessionAuthority`, and `WearableMessageGuard` are retained and composed rather than replaced.
- Existing release truth remains fail-closed.
- Physical S24+/Wear OS validation remains a separate release gate.

## Current State

The branch already contains:

- a phone-side Wear OS Data Layer adapter;
- a Wear OS companion module with the same application identity/signing chain;
- a bounded platform `ping/pong` probe;
- one-shot nonce correlation for the platform round-trip;
- an Ed25519 transcript verifier;
- a wearable handshake replay guard;
- user-confirmation and identity/session policy;
- session authority;
- message replay, freshness, protocol-version and capability guards.

The missing security primitive is session confidentiality/key establishment. `wearable-security` currently has no X25519/HPKE/KDF/session-encryption implementation. Therefore Ed25519 success alone must not activate the channel.

## Cryptographic Construction

Use Google Tink Hybrid Encryption with the HPKE key type:

`DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`

This implements RFC 9180 HPKE using X25519, HKDF-SHA-256 and AES-256-GCM. Tink's hybrid encryption provides confidentiality but not sender authentication, so Ed25519 remains mandatory and is composed with HPKE rather than replaced by it.

The implementation must pin a reviewed Tink Android version in Gradle. Dynamic versions such as `+` are forbidden.

Official references:

- https://developers.google.com/tink/exchange-data
- https://developers.google.com/tink/supported-key-types
- https://www.rfc-editor.org/rfc/rfc9180

## Trust Model

Sentinel distinguishes four independent facts:

1. Platform reachability: Wear OS Data Layer can reach the peer.
2. Peer identity: the expected application-level Ed25519 identity is bound and verifies the canonical handshake transcript.
3. User pairing intent: the user explicitly confirms the peer/session binding.
4. Secure session: encrypted application messages can be exchanged under the negotiated HPKE/session context with replay/freshness protection.

`WEARABLE_RUNTIME=CONNECTED` requires all four. Losing any required condition revokes the connected truth immediately.

## Components

### 1. Platform Probe

Existing `WearOsDataLayerAdapter`, phone `SentinelWearListenerService`, watch `SentinelWearListenerService`, and one-shot probe registry remain responsible only for platform reachability.

They must never call `observeAuthenticatedChannel`, construct `WearableChannelProof.ACTIVE`, or publish `CONNECTED`.

### 2. Secure Handshake Coordinator

Add a small coordinator in the handset app that orchestrates existing security primitives. It owns no cryptographic algorithms itself.

Responsibilities:

- request a new handshake challenge from `WearableHandshakeReplayGuard`;
- create a proposed `WearableSession` with immutable protocol version and capability snapshot;
- accept a candidate only after Ed25519 verification succeeds;
- evaluate `WearableHandshakePolicy` using the expected identity, proposed session and explicit pairing confirmation;
- consume the replay challenge only after cryptographic verification and policy acceptance prerequisites are satisfied;
- activate the session through `WearableSessionAuthority`;
- require secure-channel establishment before publishing an active `WearableChannelProof`.

The coordinator must not consume the replay nonce before signature verification. Invalid signed candidates must therefore not burn a valid challenge.

### 3. HPKE Key Material Boundary

Add a crypto-owned abstraction in `wearable-security` around Tink HPKE.

The abstraction exposes only:

- generation/import of a recipient HPKE key pair;
- export of the public key material required by the peer;
- encrypt(plaintext, contextInfo);
- decrypt(ciphertext, contextInfo).

Private key material must never be returned as a raw application-domain value.

The abstraction must use Tink key handles/serialization APIs appropriate for Android. No raw private key should be logged, persisted in preferences, included in product truth, or sent over Data Layer.

### 4. Handshake Transcript Binding

The existing Ed25519 canonical transcript must be extended to bind the secure-session key agreement inputs.

Protocol v2 will bind at minimum:

- stable Sentinel wearable identity;
- key fingerprint;
- session ID;
- protocol version;
- exact negotiated capability set;
- replay challenge nonce;
- issued timestamp;
- handset HPKE public-key fingerprint or canonical serialized public key hash;
- wearable HPKE public-key fingerprint or canonical serialized public key hash.

The canonical encoding must remain deterministic, delimiter-safe and UTF-8 lossless.

Protocol v1 handshakes must never be upgraded silently to a secure v2 session. A v1 peer remains non-connected for privileged Sentinel capabilities.

### 5. Session Context

All encrypted application messages must bind immutable context information to HPKE/Tink `context_info`.

The context must include a canonical domain separator plus at least:

- protocol version;
- session ID;
- sender role (`phone` or `wearable`);
- recipient role;
- required capability, or an explicit `NONE` marker.

A ciphertext produced for one role/session/capability must fail under any different context.

### 6. Message Envelope

The existing `WearableMessageEnvelope` remains authoritative for application-level replay and freshness checks.

Encrypted payload handling order is:

1. verify that a current authoritative session exists;
2. reject malformed/bounded-size violations before expensive work where safe;
3. decrypt with the HPKE context bound to that session/direction/capability;
4. decode the authenticated plaintext envelope;
5. run `WearableMessageGuard` for session ID, protocol version, sequence, timestamp, expiry and capability checks;
6. dispatch to the capability handler only after `ACCEPT`.

Decryption success does not bypass `WearableMessageGuard`.

## Pairing and Key Lifecycle

First pairing requires explicit user confirmation before session activation.

The Ed25519 application identity is the durable trust anchor. HPKE session key material is session-scoped and must rotate when any of these occur:

- peer reconnect after channel loss;
- session expiry;
- app data reset/reinstall;
- identity/key mismatch;
- protocol upgrade requiring new transcript binding;
- replay-guard/session-authority revocation;
- message guard returns `REKEY_REQUIRED`.

No automatic trust transfer is allowed from a new Ed25519 identity simply because the Data Layer node ID matches an earlier device.

## Runtime Truth

`WearableRuntimeCoordinator` must publish `CONNECTED` only when a short-lived `WearableChannelProof` is created after:

- Data Layer peer reachability;
- accepted Ed25519 transcript;
- replay challenge success;
- explicit pairing confirmation;
- session authority activation;
- HPKE secure-channel establishment.

The proof TTL must be short and refreshed by authenticated secure-channel traffic or an authenticated liveness message. A plain platform `pong` must not refresh authenticated-channel TTL.

On disconnect, decryption/authentication failure, stale proof, revoked session, identity mismatch, or key mismatch, the previous connected observation must be immediately replaced with a locked state.

Before physical validation, the canonical registry remains:

`LOCKED_EXTERNAL_DEPENDENCY / IDENTITY_BINDING_MISSING`

A code-complete secure channel does not by itself remove the physical-validation requirement.

## Error Handling

Fail closed for trust, fail safely for availability.

- Missing platform peer: no secure session; no privileged wearable capability.
- Invalid signature: reject candidate; do not activate or refresh truth.
- Unknown/replayed challenge: reject.
- HPKE decryption failure: reject message and keep capability handlers untouched.
- Session/capability mismatch: reject.
- Future/stale timestamp: reject.
- Duplicate sequence: reject.
- Malformed or oversized payload: reject before dispatch.
- User confirmation missing: pairing stays pending.
- Tink initialization or key-storage failure: wearable secure channel stays locked; Phone Core remains unaffected.

No wearable failure may block CallScreeningService or other latency-critical Phone Core callbacks.

## Persistence

Persist only long-lived trust material that is required to recognize an explicitly paired wearable, and store it through Android secure storage/Keystore-backed mechanisms.

Allowed durable values include the expected Ed25519 public-key fingerprint and non-secret pairing metadata.

Do not persist:

- raw Data Layer node IDs as durable Sentinel identity;
- replay nonces;
- plaintext session keys;
- raw HPKE private keys outside Tink/secure key storage;
- decrypted message bodies solely for transport debugging.

## Testing Strategy

### Pure JVM/security tests

- HPKE encrypt/decrypt round trip.
- wrong private key fails.
- wrong context info fails.
- key serialization/public-key extraction remains deterministic where required.
- protocol-v2 transcript changes when either HPKE key binding changes.
- invalid Ed25519 candidate cannot consume replay challenge.
- valid signature with stale/replayed challenge fails.
- missing pairing confirmation fails.
- session activation rejects protocol/capability substitution.
- replayed or out-of-order encrypted message fails at message guard.
- role/capability/session context substitution fails decryption or validation.
- channel proof becomes stale and truth revokes.

### Android/unit integration tests

- Data Layer `pong` alone cannot call authenticated-channel publication.
- secure coordinator cannot be invoked by ordinary UI code with fabricated proof material.
- foreground maintenance remains outside Telecom cold-start path.
- encrypted wearable listener performs no heavy work in CallScreeningService.

### Wear OS instrumentation/emulator tests

When CI runners are functional:

- paired phone/watch emulator round-trip;
- first pairing requires user confirmation;
- successful v2 handshake establishes encrypted exchange;
- wrong watch identity fails;
- reinstall/key rotation requires re-pairing;
- replay and stale packets fail;
- disconnect revokes active truth.

### Physical validation

A real handset + Wear OS device remains mandatory before production availability. Validate at minimum connection/reconnect, process death, Bluetooth/Wi-Fi handoff, watch reboot, phone reboot, stale-session revocation and lock-screen notification behavior.

## CI and Product-Truth Gates

Extend the existing wearable product-truth gate so it rejects regressions where:

- Tink dependency disappears or becomes dynamically versioned;
- protocol v2 lacks both HPKE public-key bindings;
- Data Layer probe directly publishes `CONNECTED`;
- a secure channel activates without Ed25519 verification;
- replay guard is bypassed;
- pairing confirmation is bypassed;
- plaintext privileged wearable messages are dispatched;
- registry is promoted before runtime/physical evidence exists.

GitHub Actions infrastructure failure (`runner_id=0`, `steps=[]`) remains an infrastructure blocker and must not be interpreted as test failure or success.

## Out of Scope

This design does not add:

- Samsung proprietary SDK integration;
- Apple Watch support;
- cloud relay for wearable traffic;
- paid infrastructure;
- remote wearable management;
- arbitrary Bluetooth GATT transport;
- production claim before physical validation.

## Acceptance Criteria

The implementation is code-complete only when:

1. phone and Wear OS companion compile with a pinned Tink dependency;
2. protocol v2 binds both HPKE public-key identities into the Ed25519-authenticated transcript;
3. replay challenge is one-shot and consumed only after cryptographic candidate verification;
4. explicit pairing confirmation is required;
5. session authority activates only the verified immutable capability snapshot;
6. privileged messages are encrypted with HPKE and context-bound to session/direction/capability;
7. application message replay/freshness checks remain enforced after decryption;
8. platform `ping/pong` alone cannot publish `CONNECTED`;
9. loss/staleness/revocation replaces prior connected truth immediately;
10. tests cover substitution, replay, stale session, wrong key, wrong context and reconnect paths;
11. product truth remains locked until runtime and physical evidence exists.
