# Sentinel defensive VPN architecture

## Status

The Android application now contains a maintained WireGuard backend, Android `VpnService` consent handling and a fail-closed `SentinelVpnController`. The repository also contains Mesh control-plane, enrollment, relay, TLS, OIDC and overlay-addressing foundations. No Sentinel Internet exit gateway has yet been provisioned and validated as production infrastructure, so the public VPN service is not operational.

## Design

- Android `VpnService` is provided through the maintained WireGuard Android tunnel library.
- WireGuard should provide the encrypted tunnel and packet transport; Sentinel must not implement cryptography.
- `SentinelVpnController` validates bounded configuration before activation.
- Configuration should remain in memory by default. Private keys must never be written to logs.
- The defensive Internet-VPN configuration requires both IPv4 and IPv6 default routes (`0.0.0.0/0` and `::/0`) to avoid protocol-family leaks.
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

A client VPN is not a complete VPN service by itself. Sentinel now has client code and provisioning/control-plane foundations, but it still needs a reachable validated Internet exit gateway plus the listed external leak, reconnect and device tests before the public VPN service can be marked operational.
