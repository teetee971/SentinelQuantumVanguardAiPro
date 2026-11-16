# Sentinel Mesh deployment

This directory packages the Mesh control plane, NAT probe and relay without exposing the
control-plane HTTP port directly to the Internet.

## Network surfaces

- TCP 443: HTTPS control plane through Caddy.
- UDP 3479: authenticated NAT mapping probe.
- UDP 3480: bounded pair-scoped relay.
- TCP 8788: internal Docker network only.

The relay is not a generic proxy. It forwards only inside short-lived sessions created after the
Zero Trust negotiation reaches `RELAY_REQUIRED`.

## Required external values

Set these outside source control before starting the deployment:

- `MESH_PUBLIC_DOMAIN`: DNS name pointing to the host.
- `MESH_ADMIN_TOKEN`: random administrator token, at least 24 characters.
- `MESH_STATE_SECRET`: random state-integrity secret, at least 32 characters.
- `MESH_RELAY_PUBLIC_ENDPOINT`: public host/IP and UDP port reachable by clients, for example
  `mesh.example.net:3480`.

Do not commit real values.

## Start

From this directory:

```sh
docker compose up -d --build
```

Caddy obtains and renews the public TLS certificate when DNS and inbound ports are correctly
configured. The Mesh service runs as an unprivileged user with a read-only root filesystem and no
Linux capabilities.

## Production gates

A deployment is not considered ACTIVE until all of the following have been verified:

1. HTTPS certificate and hostname validation from an external client.
2. UDP 3479 reachability from at least two external networks.
3. UDP 3480 relay reachability from at least two external networks.
4. State persistence across a controlled restart.
5. Revoked node credentials can no longer use control, probe or relay endpoints.
6. Direct path and relay fallback are exercised with two real clients behind distinct NATs.
7. IPv4 and IPv6 behavior is measured separately.
8. Logs contain no bearer tokens, WireGuard private keys or enrollment codes.
