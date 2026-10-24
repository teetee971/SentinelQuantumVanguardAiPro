# Sentinel Offline Mesh — secure GATT transport design

Date: 2026-10-24
Branch: `feature/sentinel-offline-mesh-foundation-20261024`
Reference: `9c3ec9240150ba903f6757d5db6ba60e5c320db0`

## Decision

The phase-1 transport uses a small, versioned application security layer over
the existing bounded GATT frame protocol. It uses platform/JCA primitives
already available on the Android API floor instead of adding an unreviewed
runtime dependency:

- ECDH on NIST P-256 for an ephemeral session secret;
- ECDSA with SHA-256 using a long-lived device identity to authenticate both
  handshake messages;
- HKDF-SHA-256 to derive direction-specific application keys;
- AES-256-GCM for application payloads, with a direction-specific counter
  nonce and authenticated transcript data.

This is deliberately not called Noise. A future Noise integration can replace
the handshake behind the same channel boundary after a vetted implementation
is selected and reviewed. No connection, MTU, CCCD subscription, or radio
write confirmation is cryptographic evidence.

## Handshake boundary

The initiator sends a signed ephemeral-key hello. The responder verifies the
initiator identity against an explicit trust verifier, signs its own ephemeral
key and the complete transcript, and returns a response. The initiator then
sends a key-confirmation MAC. The responder becomes authenticated only after
that MAC verifies. The initiator becomes authenticated only after verifying the
responder signature and deriving the same transcript-bound key.

If an identity or trust verifier is absent, the secure channel remains locked.
The Android client and server constructors have no implicit trust store and
therefore do not accept private data unless a caller supplies one. An
AndroidKeyStore-backed identity can sign through `OwnerDeviceKeyManager`; its
private key is never exported.

## Transport boundary

After authentication, the secure channel encrypts a complete application
message before handing it to the existing stop-and-wait transport. That
transport fragments the encrypted envelope, reassembles it once, ACKs each
fragment, confirms every radio write, retries on bounded deadlines, and
invalidates the link after interruption or retry exhaustion. ACKs and data are
directional and each Android peer owns a separate channel instance.

The client writes the client-to-server characteristic and consumes
server-to-client notifications. The server accepts only writes to the
client-to-server characteristic and emits notifications only after a valid
CCCD subscription and an authenticated channel. Every callback is checked
against the current GATT/session token; stale callbacks are ignored.

## Deliberate limits

- Maximum application payload: 64 KiB.
- Maximum protocol frame: 244 bytes, matching the requested 247-byte ATT MTU.
- Maximum queued frames: 32 per peer.
- Maximum fragment count: 4096.
- Maximum data retries: 3.
- No background activity launch, SMS/MMS/VPN integration, mesh routing, or
  physical-radio qualification is part of this change.
- A negotiated MTU below the frame size remains a qualification case; it must
  be exercised on hardware before claiming broad device compatibility.

## Verification strategy

Unit tests cover handshake authentication, wrong-key and transcript tampering,
AES-GCM confidentiality/integrity, replay rejection, fragmentation,
reassembly, ACK/retry behavior, per-peer tokens, and stale callbacks.
Android instrumented tests cover the service characteristic contract and the
default-locked boundary. They do not constitute a physical two-device BLE
qualification. That qualification remains explicitly outstanding unless a
real device run produces logs and artifacts.
