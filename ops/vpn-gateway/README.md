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


## Gestion bornée des peers provisionnés

Le script `manage-peer.sh` applique ou révoque un peer WireGuard sur une passerelle déjà démarrée. Il est destiné au runtime de provisioning Sentinel, pas à une saisie utilisateur libre.

Contraintes :

- exécution root uniquement ;
- interface WireGuard déjà active ;
- clé publique WireGuard canonique uniquement ;
- ajout avec exactement une IPv4 `/32` et une IPv6 `/128` ;
- verrouillage exclusif avec `flock` pour éviter les mutations concurrentes ;
- refus d'une adresse déjà attribuée à une autre clé publique ;
- aucune clé privée client acceptée, stockée ou affichée ;
- révocation par clé publique uniquement.

Exemple opérateur :

```bash
sudo SENTINEL_WG_INTERFACE=sentinel0 ./manage-peer.sh add \
  '<CLIENT_PUBLIC_KEY>' \
  '10.73.0.42/32' \
  'fd73:1::2a/128'

sudo SENTINEL_WG_INTERFACE=sentinel0 ./manage-peer.sh remove \
  '<CLIENT_PUBLIC_KEY>'
```

Le script ne constitue pas à lui seul un control plane de production. Le service de provisioning doit rester l'unique source des leases valides et la passerelle ne doit être marquée `AVAILABLE` qu'après validation opérationnelle complète.

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


## Filtering DNS on the same VPN path

After the WireGuard gateway is running, `install-filtering-dns.sh` can install an Unbound resolver bound only to the WireGuard gateway addresses.

This keeps Sentinel on **one Android VPN path**:

```text
Android apps
   |
WireGuard / VpnService
   |
Sentinel gateway
   |
Unbound filtering DNS
   |
authenticated DNS-over-TLS upstream
```

The resolver:

- accepts DNS only from the configured WireGuard IPv4/IPv6 subnets;
- forwards upstream DNS with TLS certificate authentication;
- keeps `forward-first: no` so a TLS-forwarding failure does not silently fall back to ordinary recursive DNS;
- generates `always_nxdomain` local zones from the local blocklist;
- generates `always_transparent` exceptions from the allowlist;
- disables per-query/reply logging;
- performs no automatic blocklist download;
- blocks VPN-client plaintext DNS forwarding to external port 53 at the gateway firewall.

Example environment:

```bash
export SENTINEL_WG_IPV4_ADDRESS='10.73.0.1/24'
export SENTINEL_WG_IPV6_ADDRESS='fd73:1::1/64'
export SENTINEL_WG_IPV4_SUBNET='10.73.0.0/24'
export SENTINEL_WG_IPV6_SUBNET='fd73:1::/64'

# Example only. Choose upstreams according to the project's privacy/legal policy.
export SENTINEL_DNS_UPSTREAM_IPV4_1='203.0.113.53'
export SENTINEL_DNS_UPSTREAM_TLS_NAME_1='resolver.example'
export SENTINEL_BLOCKLIST_FILE='/etc/sentinel-vpn/blocklist.txt'
export SENTINEL_ALLOWLIST_FILE='/etc/sentinel-vpn/allowlist.txt'

sudo -E ./install-filtering-dns.sh
```

The documentation IP/hostname above are placeholders and are not usable production resolvers.

Provisioned WireGuard client configurations must use the gateway tunnel address as DNS. The Android client must not start a second DNS-only `VpnService`.

### Filter-list format

Accepted entries are either one domain per line or basic hosts-file entries:

```text
tracker.example
0.0.0.0 ads.example
127.0.0.1 telemetry.example
```

Comments beginning with `#` are ignored. The installer rejects oversized inputs and bounds accepted rules.

No source is downloaded automatically. Production lists require explicit provenance, licensing, update signatures/versioning and rollback protection.

### Known limitations

DNS filtering is not equivalent to HTTPS interception.

- DNS-over-HTTPS inside ordinary HTTPS traffic can bypass DNS-domain filtering unless an additional explicit policy controls those endpoints.
- Android Private DNS / application-specific encrypted DNS requires compatibility testing and user-facing guidance.
- CNAME cloaking and first-party tracker aliases can require additional resolver logic or curated intelligence.
- The filtering resolver does not justify a claim that every advertisement or tracker is blocked.

Unbound's official configuration supports `local-zone` filtering and authenticated TLS forwarding:
https://unbound.docs.nlnetlabs.nl/en/latest/manpages/unbound.conf.html
