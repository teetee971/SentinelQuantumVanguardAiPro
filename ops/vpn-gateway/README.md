# Sentinel VPN gateway bootstrap

This directory contains a **pre-production bootstrap** for a dedicated Sentinel WireGuard exit host.

It does not create a production-ready VPN service by itself and it never changes the Sentinel gateway catalog to `AVAILABLE`.

## Safety model

The bootstrap:

- generates the WireGuard server private key on the gateway host;
- keeps the private key under `/etc/wireguard` with mode 0600;
- enables IPv4 forwarding and IPv6 forwarding;
- configures IPv4 NAT for the private WireGuard subnet;
- installs a default-drop nftables input/forward policy;
- restricts SSH to an explicitly supplied administrative source CIDR;
- validates the generated nftables rules before applying them;
- requires an explicit acknowledgement before replacing the host firewall;
- provisions no client peer and stores no client private key.

Because the script replaces the active nftables ruleset, use it only on a dedicated gateway host after reviewing the current firewall and provider console access.

## Required environment

Example values are documentation only:

```bash
export SENTINEL_WG_IPV4_ADDRESS='10.73.0.1/24'
export SENTINEL_WG_IPV6_ADDRESS='fd73:1::1/64'
export SENTINEL_WG_IPV4_SUBNET='10.73.0.0/24'
export SENTINEL_WG_IPV6_SUBNET='fd73:1::/64'
export SENTINEL_ADMIN_SSH_CIDR='203.0.113.10/32'
export SENTINEL_SSH_PORT='22'
export SENTINEL_WG_PORT='51820'
export SENTINEL_CONFIRM_FIREWALL_RESET='YES'
sudo -E ./bootstrap-wireguard.sh
```

The example CIDRs and addresses above are not production values.

## IPv6

The bootstrap deliberately does **not** use NAT66. The provider must route an appropriate IPv6 prefix to the gateway, and the client addressing/routing plan must be configured accordingly.

`SENTINEL_IPV6_EGRESS_READY=YES` is only an operator declaration. It is not evidence. The gateway still fails production acceptance until an external probe confirms working IPv6 egress through the tunnel.

## Production acceptance gate

A gateway must remain `PLANNED`, `PROVISIONING` or `DEGRADED` until independent checks prove at least:

1. WireGuard UDP endpoint reachable externally.
2. Per-device provisioning and revocation work.
3. IPv4 full-tunnel egress works.
4. IPv6 full-tunnel egress works without leak or black-hole behavior.
5. DNS requests remain inside the intended Sentinel path.
6. Public IPv4/IPv6 geolocation matches the advertised country.
7. Wi-Fi/mobile-data transitions reconnect correctly on Android.
8. MTU/path-MTU behavior is stable.
9. Monitoring, rate limiting and abuse controls are active.
10. Gateway key rotation and emergency revocation are tested.

Only after all required evidence is retained may the control plane advertise the gateway as `AVAILABLE`.
