# Sentinel defensive VPN architecture

## Status

The Android application does **not** currently contain a VPN implementation. This document is the target security architecture. The current Gradle dependencies, manifest and Kotlin sources contain no WireGuard backend, no Sentinel VPN controller and no provisioned VPN gateway.

## Design

- Android `VpnService` should be provided by a maintained WireGuard tunnel library.
- WireGuard should provide the encrypted tunnel and packet transport; Sentinel must not implement cryptography.
- A future `SentinelVpnController` must validate the configuration before activation.
- Configuration should remain in memory by default. Private keys must never be written to logs.
- A configuration must explicitly contain a full-tunnel `AllowedIPs` route (`0.0.0.0/0` or `::/0`).
- VPN consent must be obtained through Android `VpnService.prepare()`.
- Always-on / lockdown remains controlled by Android's system VPN settings. Sentinel must never pretend that a normal app can programmatically force device lockdown without the required Android management privileges.

## Required production components

1. A Sentinel-controlled WireGuard gateway/server with dedicated keys.
2. Secure provisioning of the per-device client configuration.
3. Android Keystore-backed protection for any persisted private key, if persistence is later required.
4. DNS leak testing for IPv4 and IPv6.
5. Kill-switch testing using Android always-on + lockdown where supported.
6. Reconnect and network-transition tests (Wi-Fi, mobile data, captive portal, airplane mode).
7. MTU/path-MTU validation.
8. Tunnel endpoint health monitoring without logging sensitive tunnel configuration.
9. Fuzzing of imported WireGuard configuration before it reaches the tunnel backend.
10. SBOM and license tracking for the WireGuard dependency.

## Security invariants

- No plaintext private key in logs, analytics, crash reports, or Git.
- No `allowBypass()` for the defensive full-tunnel profile.
- No split tunnel for the default defensive mode.
- No dependency or configuration shared with an external application or project.
- A VPN failure must be surfaced as a security state; the UI must not display a false "protected" state.
- Network protection is considered active only after the Android VPN interface is established and the WireGuard backend reports `UP`.

## Important limitation

A client VPN is not a VPN service by itself. Sentinel needs client code, a reachable WireGuard server/gateway and a secure provisioning path before this becomes an end-to-end VPN service. Until those pieces and the listed tests exist, the product must display the VPN status as `NOT_IMPLEMENTED`.
