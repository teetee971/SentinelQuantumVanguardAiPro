# Sentinel Offline Mesh — Phase 1 audit

Date: 2026-10-24
Branch: `feature/sentinel-offline-mesh-foundation-20261024`
Audited head: `9c3ec9240150ba903f6757d5db6ba60e5c320db0` before the current phase-1 changes.

## Evidence

The branch exists and is ahead of `main` (merge base
`935344423b2af2a4a57c58123f2c5c178b46ec03`). Its current head is the
commit `9c3ec9240150ba903f6757d5db6ba60e5c320db0`. The audit below is the
pre-change baseline; the secure-transport design and qualification report
describe the implementation added after this snapshot.

## Current BLE implementation

- `OfflineMeshGattClient` connects with `connectGatt`, validates the marker
  service and both data directions, requests MTU 247, subscribes to the CCCD,
  and (when explicitly configured with a secure channel factory) writes frames
  and consumes notifications. Setup alone remains unauthenticated.
- `OfflineMeshGattServer` registers the marker, client-write, and server-notify
  characteristics, tracks bounded per-address sessions, accepts CCCD writes,
  and (when explicitly configured with a secure channel factory) emits
  confirmed notifications. It rejects reads and unauthenticated writes.
- `OfflineMeshConnectableAdvertiser` performs bounded, user-initiated,
  connectable advertising of the service UUID only. It is correctly
  ordered after successful server registration and stops on callback,
  timeout, permission, or radio failure.
- `OfflineMeshBleHost` is the coordinator for the peripheral side. Its gate
  still requires service registration followed by advertising confirmation and
  tears both components down on failure; it now accepts the optional secure
  channel factory without making it implicit.
- `OfflineMeshGattLinkGate` protects setup generations and MTU-derived payload
  size; the CCCD gate is separate and never implies trust.
- `OfflineMeshGattOutbox` is a bounded, single-in-flight queue with a
  default-deny authentication verifier. It does not fragment, reassemble,
  ACK, retry, expire, or recover a bidirectional message.
- `OfflineMeshBleSession` only accepts an externally supplied boolean
  authentication result and is not wired to the GATT adapters.

## Security boundary

The branch contained no Noise implementation in the Android module. The
current phase adds a versioned secure channel using JCA ECDH P-256, ECDSA
transcript signatures, HKDF-SHA-256, and AES-GCM. It requires an explicit
identity and peer-trust verifier; the default client/server/coordinator
constructors provide none, so discovery, MTU, CCCD, and radio callbacks still
cannot authorize private data. The secure channel is intentionally not called
Noise and remains subject to independent protocol review.

## Scope

Phase 1 changes only the Offline Mesh BLE transport, its tests, and evidence
documents. Phone Core, SMS, MMS, UI, permissions, and multi-hop routing remain
unchanged or deferred to later phases. A vetted Noise implementation and
physical two-device qualification remain separate review gates.
