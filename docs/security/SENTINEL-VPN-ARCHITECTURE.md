# Sentinel defensive VPN architecture

## Status

The Android repository **does contain a fail-closed WireGuard client foundation**. The current implementation includes:

- the maintained WireGuard Android tunnel dependency and `GoBackend`;
- `SentinelVpnController` with Android `VpnService.prepare()` consent handling;
- explicit runtime states including `READY_NO_GATEWAY`, `CONSENT_REQUIRED`, `CONNECTING`, `PROTECTED`, `FAILED` and `DISCONNECTED`;
- full-tunnel validation requiring both `0.0.0.0/0` and `::/0`;
- gateway-pinned tunnel DNS validation;
- zeroing of imported configuration buffers after parsing;
- a UI that refuses to expose a connect action or protected state while no validated Sentinel exit gateway exists.

This is **not yet an operational public VPN service**. No production Sentinel exit gateway, authenticated production provisioning/control plane, or end-to-end physical validation is demonstrated by the repository.

## Implemented client boundary

`SentinelVpnController` owns the Android/WireGuard orchestration boundary. It does not invent infrastructure and only accepts a gateway whose state is `AVAILABLE`.

The controller must continue to:

- obtain explicit Android VPN consent through `VpnService.prepare()`;
- use WireGuard for cryptography and tunnel transport rather than implementing cryptography in Sentinel;
- require dual-stack full-tunnel routing before activation;
- require tunnel DNS addresses pinned to the selected validated gateway;
- treat a backend state other than `UP` as failure;
- release Sentinel's VPN/mesh mode arbitration when the tunnel stops or fails;
- keep private keys and raw configurations out of logs.

The Android screen is intentionally non-connectable while no eligible gateway exists. Code presence is not evidence of a working VPN service.

## Required production components

Before Sentinel may expose an operational VPN connection, the deployment still needs:

1. at least one Sentinel-controlled WireGuard gateway with dedicated production keys;
2. authenticated per-device provisioning tied to a real account/entitlement or other reviewed authorization boundary;
3. production gateway catalog publication with signature, monotonic versioning and replay protection;
4. production key custody, rotation and revocation procedures;
5. IPv4 and IPv6 external egress verification;
6. DNS leak testing and gateway DNS health monitoring;
7. reconnect and network-transition tests across Wi-Fi, mobile data, captive portal and airplane-mode transitions;
8. MTU/path-MTU validation;
9. Android Always-on/Lockdown compatibility testing where supported;
10. retained evidence for gateway health, geolocation, monitoring and incident operations.

## Security invariants

- No plaintext private key in logs, analytics, crash reports or Git.
- No `allowBypass()` for the defensive full-tunnel profile.
- No split tunnel for the default defensive mode.
- A gateway that is not `AVAILABLE` is not connectable.
- A VPN failure must be surfaced as a failure state; the UI must never display a false protected state.
- `PROTECTED` is valid only after the WireGuard backend reports `UP` for an eligible gateway and the Android VPN path is established.
- An imported configuration must remain bounded and fail closed on malformed, missing-default-route or unpinned-DNS input.
- Sentinel VPN and Sentinel private mesh remain mutually arbitrated; one must not silently steal the other's VPN slot.

## Product truth

A client VPN implementation is not a VPN service by itself. The accurate current product state is:

**Android WireGuard client integrated; Sentinel exit gateway/service not provisioned.**

The UI should therefore expose a non-connectable `READY_NO_GATEWAY`-style state rather than `NOT_IMPLEMENTED`, and it must not advertise a country, public exit IP or protected state without runtime evidence.
