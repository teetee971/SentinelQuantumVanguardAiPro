# Sentinel Offline Messaging — isolated foundation

Status: EXPERIMENTAL. **Not** a working Bluetooth messenger, not cryptographically secure until an authenticated envelope format and key management are integrated. Do not advertise as READY.

## Product goal
Consent-based peer-to-peer messages across nearby Android devices, offline first. Implement as a distinct system from the existing WireGuard Private Mesh. Reference project: permissionlesstech/bitchat-android; review upstream license and dependencies before copying any source code. This module is an original transport-neutral foundation, not an upstream fork.

## First tested component
`router.mjs`: bounded candidate relay of opaque Uint8Array ciphertext, duplicate-id suppression, TTL and hop-limit checks, and no plaintext field. Local unit tests: `node --test offline-messaging/router.test.mjs`.

**Not yet provided:** encryption/authentication, peer discovery, BLE advertisements/GATT, Wi-Fi Aware transport, forwarding, persistence, UI, Android permissions/foreground service, radio-level tests, end-to-end security review or iOS interoperability.

## Security invariants to implement before Android integration
1. Authenticated message envelope (versioned and domain-separated), certified key agreement, nonce uniqueness, replay protection across restarts.
2. Trust-on-first-use with safety-number or QR confirmation; rotate ephemeral radio aliases to reduce tracking. No phone number needed.
3. Local-only forwarding; no content logging, contact upload, covert discovery or automatic geolocation.
4. Strict rate limits and quotas per peer; storage and battery limits; blocklist and reporting controls.
5. Expiry, duplicate detection, store-and-forward with explicit consent, bounded retries and backpressure.
6. Security tests: malformed frames, bogus peers, impersonation, replay, flooding, link downgrade, data exfiltration and radio failure.
7. Platform capability gate for Bluetooth, Wi-Fi Aware and permissions; report LIMITED/LOCKED rather than READY if requirements fail.
8. Separate radio mesh from Private Mesh VPN and from OSINT investigation. Do not interpret foreign radio peers as state actors.

## Milestones
M0 transport-neutral message state machine and fuzz tests.
M1 Android BLE discovery and authenticated pairwise encrypted exchange on two real Android devices.
M2 deduplicated multi-hop with tested TTL, dynamic peers, background/Doze limitations.
M3 optional Wi-Fi Aware, consent-based offline outbox, UI and accessibility.
M4 end-to-end security audit, supply-chain checks, reproducible build, Play compliance.

Do not merge to main without CI, security review and evidence from real radio devices.
