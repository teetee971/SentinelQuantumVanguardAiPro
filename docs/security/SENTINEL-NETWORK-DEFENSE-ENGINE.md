# Sentinel Network Defense Engine

## Objective

Define the defensive network-security boundary for Sentinel Quantum Vanguard AI Pro. The engine is designed to reduce exposure to scanning, spoofing, flooding, malicious destinations, DNS abuse, tunnel abuse, and anomalous traffic without claiming that a client VPN can stop attacks that occur upstream of the protected device.

## Architecture

```text
Protected device
    |
    v
Sentinel VPN / WireGuard client
    |
    +--> Full-tunnel route enforcement
    +--> DNS leak prevention
    +--> Egress policy
    +--> Local anomaly/rate detection
    |
    v
Sentinel VPN Gateway / Edge
    |
    +--> Peer authentication
    +--> Anti-abuse rate limits
    +--> Connection/flood controls
    +--> Source-address validation
    +--> Threat-intelligence policy
    +--> Dynamic block / quarantine
    |
    v
Upstream network / DDoS mitigation
    |
    v
Internet
```

## Threat controls

| Threat | Sentinel control | Boundary |
|---|---|---|
| Port/network scanning | Detect destination fan-out and connection bursts; rate-limit or block | Detection is behavioral; Sentinel must not actively scan third-party networks |
| IP spoofing | Validate source/interface state and enforce gateway ingress/egress policy | Internet-scale anti-spoofing requires upstream/provider filtering |
| Volumetric DDoS | Gateway quotas, packet/connection limits, peer isolation | Large attacks require upstream scrubbing/capacity |
| TCP/UDP floods | Per-peer and aggregate rate limits with bounded state | Provider edge may still be required |
| DNS leakage | Force DNS through the protected path; verify IPv4 and IPv6 behavior | OS/provider behavior must be tested on real devices |
| DNS manipulation | Controlled resolver policy and authenticated/encrypted DNS transport where supported | Resolver trust remains an external dependency |
| Malicious destinations | Reputation/threat-intelligence policy with conservative fail-safe behavior | Feeds can be stale or incomplete |
| Brute-force abuse | Frequency thresholds and temporary quarantine | Application authentication remains separate |
| C2-like behavior | Destination reputation plus behavioral correlation | Never treat one weak signal as proof of compromise |
| Malformed VPN configuration | Strict schema validation before backend activation + fuzzing | Backend/library remains a security boundary |
| Tunnel failure | Explicit DEGRADED/FAILED state; never report PROTECTED prematurely | Android system VPN state must be authoritative |

## Security state machine

Allowed states:

- `NORMAL`: tunnel established and policy enforcement healthy.
- `SUSPICIOUS`: anomalous behavior observed; monitoring intensified.
- `RATE_LIMITED`: traffic constrained by policy.
- `BLOCKED`: destination, peer, or flow denied.
- `VPN_DEGRADED`: tunnel or enforcement health is uncertain.
- `UPSTREAM_ATTACK`: gateway/provider attack indicators exceed configured thresholds.
- `RECOVERY`: controls are being relaxed only after health criteria are satisfied.

State transitions must be deterministic, auditable, bounded, and reversible. No state may silently downgrade security while the UI continues to report `PROTECTED`.

## Rate-limiting requirements

The defense engine must use bounded-memory structures and monotonic time. Limits must exist at both peer and aggregate levels. Expired entries must be evicted deterministically. Attack traffic must not be able to grow an unbounded map, queue, log, or telemetry buffer.

Recommended dimensions:

- packets/second per peer
- bytes/second per peer
- new connections per peer/window
- unique destinations per peer/window
- failed handshakes per peer/window
- aggregate gateway packets/second and connections/second

Thresholds are policy, not hard-coded assumptions about an attack. They must be configurable, versioned, and fuzz-tested.

## Anti-spoofing boundary

Sentinel may reject locally impossible source/interface combinations and enforce gateway policy. It cannot guarantee prevention of Internet-originated source-address spoofing before traffic reaches an upstream network. Production deployment therefore requires provider-side anti-spoofing controls and documented ingress filtering.

## DDoS boundary

A phone cannot absorb or stop a multi-gigabit attack against a public VPN endpoint. Production Sentinel requires an edge architecture capable of rate limiting, peer isolation, horizontal capacity, and upstream DDoS mitigation. The client must fail closed from a security-state perspective if gateway health becomes unreliable.

## DNS requirements

Defensive full-tunnel mode must have an explicit DNS policy. IPv4 and IPv6 DNS paths must be tested. No DNS server outside the approved policy may be reachable through an unintended interface. If the selected security mode requires fail-closed DNS, resolver failure must not silently fall back to an uncontrolled resolver.

## Privacy and telemetry

Telemetry must be minimized. Do not log private keys, raw credentials, full payloads, or unnecessary browsing content. Security events should prefer coarse metadata such as event type, policy decision, bounded counters, and timestamps. Retention must be finite and documented.

## Separation invariant

Sentinel is a standalone security product. No application code, dependency, configuration, package identifier, cloud project identifier, domain, secret, workflow, artifact, or build output belonging to A KI PRI SA YÉ may be introduced into Sentinel. CI isolation checks are mandatory and must remain blocking.

## Fuzzing scope

The defensive engine must fuzz:

1. policy/configuration parsing;
2. threat-event parsing;
3. IP/domain normalization;
4. rate-limit state transitions;
5. malformed timestamps, counters, and durations;
6. oversized and adversarial Unicode input;
7. imported WireGuard configuration before activation.

Every fuzz target must assert no crash, no unbounded resource growth, deterministic handling where required, and preservation of security invariants.

## Production gate

This design is not a claim that Sentinel currently blocks every attack. Production readiness requires implementation plus automated tests and real-device/network validation for VPN establishment, kill-switch behavior, DNS leaks, IPv4/IPv6 routing, network transitions, gateway rate limiting, abuse isolation, and failure-state reporting.
