# Sentinel Offline Mesh — Phase 1 audit

Date: 2026-10-24
Branch: `feature/sentinel-offline-mesh-foundation-20261024`
Audited head: `9ae352a911279a222cd56e2665061542bbd6037e`

## Evidence

The branch exists and is 97 commits ahead of `main` (merge base
`935344423b2af2a4a57c58123f2c5c178b46ec03`). Its current head is the
commit `9ae352a911279a222cd56e2665061542bbd6037e`, which adds three
unit tests for duplicate and stale GATT callbacks. No CI workflow run or
status check is associated with that head yet.

## Current BLE implementation

- `OfflineMeshGattClient` connects with `connectGatt`, validates the
  marker service/characteristic, requests MTU 247, and closes on setup
  failure or timeout. It has no data characteristic, no descriptor
  subscription, no write operation, no notification callback, and no
  receive path.
- `OfflineMeshGattServer` registers a primary marker service whose only
  characteristic has zero properties and zero permissions. It explicitly
  refuses reads and writes; it has no connected-peer registry and no
  notification path.
- `OfflineMeshConnectableAdvertiser` performs bounded, user-initiated,
  connectable advertising of the service UUID only. It is correctly
  ordered after successful server registration and stops on callback,
  timeout, permission, or radio failure.
- `OfflineMeshBleHost` is the coordinator for the peripheral side. Its
  gate correctly requires service registration followed by advertising
  confirmation and tears both components down on failure. It does not
  coordinate an application payload path.
- `OfflineMeshGattLinkGate` protects setup generations and MTU-derived
  payload size, but has no notification-subscription phase.
- `OfflineMeshGattOutbox` is a bounded, single-in-flight queue with a
  default-deny authentication verifier. It does not fragment, reassemble,
  ACK, retry, expire, or recover a bidirectional message.
- `OfflineMeshBleSession` only accepts an externally supplied boolean
  authentication result and is not wired to the GATT adapters.

## Security boundary

The branch contains no Noise implementation in the Android module. The
existing private queue is intentionally locked unless an injected verifier
returns true. Phase 1 preserves this invariant: the transport protocol and
GATT adapters are implemented, but the production constructors use a
deny-by-default authenticator and no private message becomes available from
an unauthenticated discovery or link callback.

## Scope

Phase 1 changes only the Offline Mesh BLE transport and its tests. Phone
Core, SMS, MMS, UI, permissions, Noise, and multi-hop routing remain
unchanged or deferred to their stated later phases.
