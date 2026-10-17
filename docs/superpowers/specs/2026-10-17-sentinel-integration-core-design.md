# Sentinel Integration Core — Design Specification

Date: 2026-10-17
Status: Proposed / awaiting user review
Scope: Sentinel Quantum Vanguard AI Pro

## 1. Objective

Make product integration truth explicit and enforceable across Sentinel. A feature must never appear production-ready merely because code, a screen, a test fixture, or a dormant library exists. Every user-visible or runtime-relevant capability must either have a complete, testable integration chain or expose a truthful locked/limited state with its missing dependency identified.

The target architecture is a Sentinel Integration Core layered alongside the existing Sentinel Intelligence Core. It coordinates integration contracts and product truth; it does not replace domain engines such as Phone Core, SMS/MMS transport, Network Center, VPN, Wearable, SOC, Threat Intelligence, OSINT, or Decision Plane.

## 2. Non-goals

- Do not create a God object that owns all product logic.
- Do not route heavyweight IO, network calls, disk reads, AndroidKeyStore work, or remote intelligence into the CallScreening critical callback before respondToCall().
- Do not convert unverified community or remote intelligence into autonomous blocking authority.
- Do not mark external services as READY when backends, credentials, gateways, identity bindings, stores, or physical transports are absent.
- Do not activate intentionally dormant privileged/autonomous modules merely to improve an integration percentage.
- Do not treat emulator CI as physical Samsung/modem/operator proof.

## 3. Product integration states

Every product capability registered with the Integration Core must resolve to exactly one externally auditable state:

- `CONNECTED`: producer, contract, runtime consumer, observable product surface, and required persistence/transport all exist and pass their qualification gates.
- `LOCKED_EXTERNAL_DEPENDENCY`: product code exists, but a required real dependency is absent, for example backend, credential, gateway, identity provider binding, Redis/PostgreSQL deployment, signing key custody, or server runtime.
- `PHYSICAL_REQUIRED`: software integration is present, but final truth requires a real device/modem/operator/OEM/peripheral test that CI cannot establish.
- `INTENTIONALLY_DISABLED`: a capability is deliberately excluded from production runtime for safety, legal, privacy, or architecture reasons.
- `CI_ONLY`: tooling, fixtures, red-team components, verification utilities, or test-only code that must never be represented as a runtime feature.

Legacy inventory labels such as `DORMANT_LIBRARY`, `INTENTIONALLY_DORMANT`, `CI_TOOLING`, and `TOOLING_ENTRYPOINT` remain useful for source-continuity analysis, but product surfaces must map them into one of the five integration states above.

## 4. Required integration chain

A capability can be `CONNECTED` only when the following chain is explicit and machine-checkable where applicable:

`producer -> bounded typed contract -> integration/intelligence sink -> consumer -> product surface/action -> observable evidence`

Each link must identify:

- owner module;
- input contract and validation bounds;
- identity model and provenance;
- execution context/threading requirement;
- privacy classification;
- persistence and retention behavior;
- failure semantics;
- consumer(s);
- qualification evidence required for `CONNECTED`.

A missing link is not silently tolerated. It must change the product state away from `CONNECTED`.

## 5. Architecture boundaries

### 5.1 Sentinel Intelligence Core

The Intelligence Core remains the shared evidence plane for bounded, provenance-aware evidence such as call decisions, SMS/MMS security results, contact identity, reputation, community context, and future compatible evidence.

It must not become the owner of transport, UI navigation, telecom callbacks, VPN lifecycle, wearable transport, or privileged remediation.

### 5.2 Sentinel Integration Core

The new Integration Core owns only integration metadata and truth:

- registry of product capabilities;
- required producers and consumers;
- integration state computation;
- external dependency declarations;
- physical-proof requirements;
- diagnostics explaining why a capability is not connected;
- machine-readable output for UI/System Doctor/release gates.

It must not perform domain side effects itself.

### 5.3 Domain modules

Domain modules retain their current authority. For example:

- Phone Core owns Telecom/InCall/CallScreening behavior.
- SMS/MMS Core owns Android messaging transport/provider flows.
- Network modules own Wi-Fi/Bluetooth observation and network-risk logic.
- VPN controller owns tunnel transitions.
- Wearable/Mesh transports own their own channel/session state.
- Threat Intelligence/OSINT adapters own source-specific retrieval/normalization.

Integration is achieved through typed adapters/events and explicit consumers, not direct cross-module mutation.

## 6. Initial product capability matrix

The first registry must cover at minimum:

1. Dialer / ROLE_DIALER / outgoing call initiation.
2. InCallService UI, audio routing, DTMF and call lifecycle.
3. CallScreeningService, local rules, cold-start rule availability and emergency fail-open.
4. Caller ID local identity.
5. Remote caller reputation with explicit consent and provenance.
6. Community phone reports and moderation context.
7. SMS default-role ownership, send lifecycle, receive ingress, callbacks, anti-phishing and inline reply.
8. MMS ingress/download/provider projection/deduplication.
9. Contacts read/write/local Sentinel identities/Android synchronization boundary.
10. Wi-Fi observation.
11. Bluetooth/BLE observation.
12. Network trust/history/behavior baseline.
13. VPN client runtime state.
14. VPN gateway/catalog/provisioning/control plane.
15. Mesh transport/runtime.
16. Wearable transport/runtime.
17. Digital Exposure / breach intelligence.
18. Threat Intelligence feeds and query gate.
19. Regulatory intelligence such as ARCEP/FSMA/other reviewed sources.
20. SOC passive pipeline and Decision Plane handoff boundaries.
21. OSINT/foreign-influence analyst tooling where applicable.
22. Storage cleaner/device utility modules.
23. Voice/RTC/PTT modules where present.
24. First-run activation assistant and permission/role orchestration.
25. System Doctor / diagnostics and release evidence surfaces.

The implementation plan may decompose this matrix into independently shippable slices; the registry schema must be stable enough to include all of them.

## 7. Known current integration truth

The repository already proves that integration is not yet 100% complete.

Examples currently declared as dormant or intentionally dormant include parts of Decision Plane, SOC, Threat Intelligence, FSMA ingestion/publication, community moderation backend adapters, Redis/PostgreSQL persistence adapters, VPN gateway provisioning/publication, owner/device identity bindings, and other server-side components.

The existing cross-module integration work in PR #1695 connects several Android producers to Intelligence Core and runtime surfaces: caller reputation, SMS, MMS, contacts, community reports, Wi-Fi/Bluetooth timeline, and VPN runtime state. This work is a candidate foundation, but it is not sufficient to classify the entire product as fully connected. Mesh and Wearable remain dependent on real runtime/transport availability, and physical S24+/SIM/carrier/OEM proof remains separate.

## 8. Fail-closed and truth rules

- A capability with an absent required dependency cannot be `CONNECTED`.
- Missing evidence resolves to UNKNOWN/LOCKED/LIMITED, never implicit success.
- A stale runtime observation cannot prove current protection.
- Remote or community evidence must retain provenance and trust class.
- Community reports pending moderation do not generate enforcement scores.
- External dependency availability must be validated through a real runtime contract, not a compile-time constant.
- UI labels must derive from the same integration-state source used by System Doctor and release gates.
- CI-only modules must never count toward production integration completion.
- `PHYSICAL_REQUIRED` is not an error; it is an explicit release boundary that remains unresolved until physical evidence is bound to the exact build/source/certificate where required.

## 9. UI and diagnostics behavior

System Doctor and relevant product screens must consume Integration Core state rather than inventing readiness locally.

For every non-`CONNECTED` capability, diagnostics must expose a bounded explanation code such as:

- `BACKEND_NOT_DEPLOYED`
- `CREDENTIAL_NOT_PROVISIONED`
- `GATEWAY_NOT_AVAILABLE`
- `RUNTIME_NOT_OBSERVED`
- `TRANSPORT_NOT_IMPLEMENTED`
- `IDENTITY_BINDING_MISSING`
- `PHYSICAL_VALIDATION_REQUIRED`
- `INTENTIONALLY_DISABLED_BY_POLICY`
- `CI_ONLY_COMPONENT`

User-facing copy may be localized, but the diagnostic code remains stable for tests and release evidence.

## 10. Testing strategy

Implementation follows strict TDD.

Required layers:

1. Registry/schema unit tests: valid state transitions, invalid/missing dependencies, stale evidence, unknown capability IDs, duplicate registrations.
2. Product-truth contract tests: every declared product surface has a registry entry and cannot silently default to CONNECTED.
3. Cross-module wiring tests: producer and consumer references exist in the intended execution order and respect privacy/threading boundaries.
4. Android unit/instrumentation tests where platform behavior is involved.
5. Emulator qualification for Android paths that are emulatable.
6. Physical S24+ tests for modem/operator/OEM/Bluetooth/wearable behaviors that cannot be proven by emulator.
7. Release gate: fail if any capability claimed as available/connected lacks required evidence for the exact head/build.

The new release/product-truth gate must not require intentionally disabled, CI-only, or externally locked capabilities to pretend to be operational. Instead it requires truthful classification and forbids false READY/CONNECTED claims.

## 11. Migration strategy

Migration should be incremental and avoid a flag-day rewrite.

Phase A: establish registry schema, diagnostic reasons, and integration-product-truth gate.

Phase B: import already-real Android connections, including the bounded work represented by #1695, without changing domain behavior.

Phase C: register all remaining product capabilities and explicitly classify missing dependencies.

Phase D: close genuine wiring gaps one domain at a time using RED->GREEN integration contracts.

Phase E: provision external backends/transports where desired and promote only when runtime evidence allows `CONNECTED`.

Phase F: run exact-head CI/emulator qualification, then bind physical S24+ evidence for `PHYSICAL_REQUIRED` capabilities.

## 12. Acceptance criteria

This architecture is complete when:

- every user-visible/runtime-relevant product capability is present in the integration registry;
- no capability defaults to `CONNECTED`;
- every `CONNECTED` capability has a complete producer-to-consumer chain and required observable evidence;
- every dormant or external-dependency module is truthfully mapped to a non-connected state;
- UI, System Doctor and release gates consume the same integration truth;
- no critical CallScreening latency/privacy invariant is weakened;
- no fake backend, simulated credential, hardcoded READY state, or test fixture is used as production proof;
- the release gate can produce an exact-head integration report separating software-connected, external-locked, intentionally-disabled, CI-only and physical-required capabilities;
- final commercial-readiness claims remain blocked until required physical S24+ evidence is complete.

## 13. Implementation decomposition requirement

Because this design spans independent subsystems, implementation must not be one giant PR. The implementation plan must decompose work into independently reviewable slices, starting with the registry/gate foundation and then domain-by-domain integration. Existing PRs and unique deltas must be replayed or absorbed selectively; stale stacked PRs must not be merged wholesale.
