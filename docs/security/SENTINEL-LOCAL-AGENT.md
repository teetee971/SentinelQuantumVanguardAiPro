# Sentinel Local Agent — architecture foundation

Status: foundation only. No Local Agent daemon, installer or production endpoint is shipped by this repository yet.

## Purpose

The optional Sentinel Local Agent is intended to enrich Android network intelligence on networks the user owns or is explicitly authorized to administer. It can later provide a stable local source for device inventory, topology events, heartbeat state and bounded flow metadata without forcing Android to rediscover the entire network for every screen refresh.

The Android application remains useful without this agent. Agent loss must degrade gracefully and must never disable the normal local defensive scanner.

## Trust boundary

The agent must be treated as an untrusted network peer until authenticated. A production client must require:

- HTTPS with an authenticated endpoint;
- explicit enrollment;
- device-bound credentials or mutually authenticated transport;
- replay protection for state-changing operations;
- bounded payloads, timeouts and cancellation;
- schema/version validation before parsing;
- no secrets embedded in the APK or repository;
- no automatic trust merely because an endpoint is on a private IP range.

Plain HTTP is reserved for loopback development only.

## Initial read-only API contract

The first production-capable API should remain read-only:

- `GET /v1/status` — agent version, schema version, generated timestamp and health;
- `GET /v1/devices` — bounded device inventory with evidence/provenance;
- `GET /v1/events` — bounded new-device / returned-device / topology events;
- `GET /v1/flows` — bounded metadata summaries only;
- `GET /v1/topology` — explicit observed relationships with provenance.

No endpoint in the first phase should pair devices, modify routers, open ports, inject packets or alter third-party systems.

## Heartbeat

Android classifies an already-observed heartbeat using `LocalAgentHeartbeatPolicy`:

- HEALTHY: recent heartbeat;
- DEGRADED: several expected heartbeats missed;
- OFFLINE: prolonged absence;
- UNKNOWN: no valid heartbeat observed.

The UI must show the stale timestamp rather than reusing old inventory as if it were current.

## Device registry

A future registry should store a device-bound fingerprint plus bounded metadata and timestamps. Raw MAC/BSSID values should not be retained when a stable local fingerprint is sufficient. Identity claims must keep their evidence source and confidence rather than silently converting heuristics into facts.

## Flow metadata

A future collector may feed `NetworkFlowAnalyzer` with metadata such as application UID, destination, port, protocol, byte counts and timestamps. Payload capture and TLS decryption are out of scope for this foundation.

## Relationship to Device DNA

Device DNA, New Device Watch and the Flow Analyzer remain separate from the transport. The Local Agent is one evidence source, not a source of truth. Android-observed Wi-Fi/BLE evidence and explicit user trust remain independently visible.

## Production gates

Before enabling a real agent endpoint:

1. implement authenticated enrollment and credential rotation;
2. add transport timeouts, cancellation and circuit breaking;
3. test malformed/oversized payloads;
4. prove Android remains responsive when the agent is offline;
5. validate cache freshness and stale-state presentation;
6. test on mesh Wi-Fi, VLAN/IoT and multi-AP environments;
7. document retention, export and deletion behavior;
8. complete real-device and real-network acceptance testing.
