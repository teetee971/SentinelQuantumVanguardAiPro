# Sentinel VPN — multi-region foundation

Status: **design foundation only**. This file does not claim that a production VPN gateway is currently available.

## Product goal

Sentinel should eventually offer a country/server selector comparable in usability to major consumer VPN applications while keeping Sentinel-specific security controls and evidence-based status reporting.

The selector must never present a location as connectable unless at least one production gateway in that location is provisioned, reachable and passing the required health checks.

## User-facing modes

- **Quick Connect**: chooses the best eligible gateway according to measured latency, health and load.
- **Country**: user selects a country; Sentinel chooses the best eligible gateway within that country.
- **Server**: user explicitly selects an eligible gateway when the plan and deployment expose server-level selection.
- **Favorites**: local list of user-preferred countries or servers.

## Gateway state model

Every gateway MUST expose one of these internal states:

- `PLANNED`: catalog entry only; not provisioned.
- `PROVISIONING`: infrastructure is being created; not connectable.
- `DEGRADED`: gateway exists but fails one or more production acceptance criteria; not selected automatically.
- `AVAILABLE`: gateway passes all mandatory acceptance criteria and may be selected.
- `DRAINING`: no new sessions; existing sessions may complete.
- `OFFLINE`: gateway unavailable.
- `REVOKED`: gateway identity or key material revoked; never connect.

Only `AVAILABLE` gateways are eligible for normal connection.

## Initial catalog

The initial UI/catalog may contain these target regions, all starting as `PLANNED` until infrastructure exists:

| Country / region | ISO code | Initial status |
| --- | --- | --- |
| France | FR | PLANNED |
| Belgium | BE | PLANNED |
| Germany | DE | PLANNED |
| Netherlands | NL | PLANNED |
| Spain | ES | PLANNED |
| Switzerland | CH | PLANNED |
| United Kingdom | GB | PLANNED |
| Canada | CA | PLANNED |
| United States | US | PLANNED |
| Japan | JP | PLANNED |
| Singapore | SG | PLANNED |

Overseas territories must not be represented as a separate exit location unless a real gateway or legally and technically accurate regional exit exists there.

## Gateway record

A client-visible gateway descriptor should contain only non-secret metadata:

```json
{
  "id": "fr-par-01",
  "country_code": "FR",
  "city": "Paris",
  "display_name": "France — Paris 01",
  "status": "AVAILABLE",
  "protocol": "wireguard",
  "endpoint_hostname": "vpn-fr-par-01.example.invalid",
  "endpoint_port": 51820,
  "ipv4": true,
  "ipv6": true,
  "load_percent": 42,
  "latency_ms": 18,
  "health_checked_at": "2026-09-18T10:00:00Z",
  "catalog_version": 1
}
```

The production endpoint, public key and provisioning data must come from a signed, authenticated control plane. Private keys must never be stored in this catalog.

## Selection algorithm

Quick Connect should:

1. discard any gateway not in `AVAILABLE`;
2. discard gateways whose health information is stale;
3. respect the user's explicit country choice when one exists;
4. rank by a bounded score using latency, load and recent failure rate;
5. apply deterministic tie-breaking;
6. fail closed when there is no eligible gateway.

No synthetic latency or load value may be shown as a production measurement.

## Mandatory production acceptance criteria

A gateway cannot transition to `AVAILABLE` until all of the following have evidence:

- WireGuard endpoint reachable from an external probe;
- authenticated provisioning path operational;
- per-device key lifecycle tested;
- IPv4 and IPv6 full-tunnel behavior verified;
- DNS leak tests passed;
- public exit IP geolocation checked against the advertised country;
- reconnect after Wi-Fi/mobile-data transition tested;
- Android Always-on/Lockdown compatibility tested where supported;
- MTU/path-MTU validated;
- monitoring and alerting active;
- gateway keys rotatable and revocable;
- rate limiting and abuse controls active;
- infrastructure and software inventory recorded;
- operational runbook present.

## Security requirements

- Sentinel must not implement its own cryptographic primitives.
- WireGuard remains the tunnel engine; Sentinel owns orchestration, policy, provisioning, monitoring and infrastructure.
- Default defensive profile is full-tunnel IPv4 + IPv6.
- No `allowBypass()` in the defensive profile.
- A failed tunnel must never be rendered as `PROTECTED`.
- A country selector must be disabled or show “planned” when no eligible gateway exists.
- Gateway public keys must be pinned through authenticated provisioning.
- Catalog updates must be signed/versioned and protected against replay.
- Client configuration size and fields must be bounded and parsed fail-closed.
- No private key, raw tunnel configuration or sensitive network identifier in logs, analytics or crash reports.

## Android UI states

The VPN screen should distinguish:

- `NOT_IMPLEMENTED`
- `READY_NO_GATEWAY`
- `CONSENT_REQUIRED`
- `CONNECTING`
- `PROTECTED`
- `DEGRADED`
- `FAILED`
- `DISCONNECTED`

`PROTECTED` is allowed only after the Android VPN interface exists and the WireGuard backend reports the tunnel as UP against an eligible gateway.

## Commercial model

The initial project decision remains: the Android defensive VPN should remain free during the initial phase.

Future commercial differentiation may apply to advanced routing or organization features, but the repository must not claim paid multi-country availability before infrastructure and pricing are actually activated.

## Implementation order

1. Add a typed region/gateway catalog and validation tests.
2. Add Android selector UI driven by the catalog, with all initial locations non-connectable.
3. Add a maintained WireGuard Android backend and `SentinelVpnController`.
4. Add secure device provisioning.
5. Provision the first real Sentinel gateway.
6. Build automated health, geolocation and leak verification.
7. Enable the first country only after evidence is retained.
8. Expand country-by-country using the same acceptance gate.

## Non-goals

- No fake servers.
- No simulated “connected” state.
- No copied NordVPN branding, assets, server names or proprietary UX.
- No claim that local Android `VpnService` alone changes the user's country.
- No dependency, key material, infrastructure or data shared with A KI PRI SA YÉ.
