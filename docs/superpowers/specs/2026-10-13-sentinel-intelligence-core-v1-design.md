# Sentinel Intelligence Core v1 — Architecture Design

Status: DESIGN APPROVED — Architecture B (event-driven typed contracts)
Date: 2026-10-13
Target branch: `release/android-apk-integration-20261013`
Implementation status: NOT STARTED

## 1. Purpose

Sentinel currently contains multiple useful intelligence-producing modules — contacts, call screening, caller identity, SMS/MMS, reputation, private timeline, network observations — but they do not share one typed evidence model or one authoritative aggregation contract.

Sentinel Intelligence Core v1 introduces a local, event-driven integration layer that connects those modules without rewriting them. Existing product modules remain owners of their domain behavior. The core standardizes identity correlation, evidence, provenance, freshness, risk aggregation and read projections.

The design must preserve the current production invariants:

- `CallScreeningService` remains local, synchronous and independent from network or disk on the mandatory response path.
- Unknown, stale or unavailable evidence must never be silently upgraded into confidence.
- Community reputation is evidence, not proof of identity or maliciousness.
- Raw phone numbers, message bodies, URLs, sender identifiers and OTP values are excluded from shared intelligence persistence unless a separately approved contract explicitly requires them.
- Existing role and permission fail-open/fail-closed semantics are not weakened.
- Existing modules are adapted, not replaced.

## 2. Scope v1

### In scope

Four existing producer domains are connected first:

1. Android contacts / Sentinel-local contact identity.
2. Incoming call screening decisions and caller identity context.
3. SMS/MMS bounded security metadata.
4. Local and signed remote phone reputation evidence.

The v1 core provides:

- canonical pseudonymous identity keys;
- immutable typed evidence events;
- evidence provenance and freshness;
- deterministic local aggregation;
- an `IntelligenceSnapshot` read model;
- projection adapters for caller ID, risk UI and precomputed call-screening cache;
- bounded private persistence;
- test contracts and migration seams.

### Explicitly out of scope for v1

- Cloud-side community ingestion or federated synchronization.
- Automatic remote blocking from community reports.
- A new database replacing existing stores.
- Network lookups on incoming-call screening path.
- Rewriting `CallRuleEngine`, `LocalContactLookup`, SMS/MMS transport or the signed reputation verifier.
- Cross-device identity linking.
- Social-profile scraping or automatic external enrichment.
- Any raw-message content retention by the intelligence core.

Those capabilities may consume the v1 contracts later but do not belong to this first integration slice.

## 3. Existing components retained

The design wraps current components rather than replacing them.

### `LocalContactLookup`

Current responsibilities retained:

- Android `READ_CONTACTS` permission gating;
- provider access state (`READY`, `PERMISSION_REQUIRED`, `PROVIDER_UNAVAILABLE`);
- canonical phone normalization;
- local display-name and organisation lookup;
- safe handling of provider inconsistencies.

The new adapter converts a successful lookup into typed identity evidence. Permission failure remains an explicit absence of evidence, not a negative identity conclusion.

### `SentinelCallScreeningService`

Current responsibilities retained:

- system callback boundary;
- role verification;
- emergency-number fail-open behavior;
- synchronous in-memory rule evaluation;
- immediate Android response;
- post-response logging/timeline work.

The service MUST NOT synchronously call the Intelligence Core aggregator, persistence layer, contact provider or network. It may consume only a precomputed immutable screening projection already present in process memory.

After the Android response, a post-response adapter may publish a bounded call evidence event.

### `PhoneRiskCard`

Current responsibilities retained:

- UI-safe risk bands;
- explicit `UNKNOWN` / `STALE` freshness;
- no blocking authority.

It becomes a consumer/projection of `IntelligenceSnapshot`, not the global source of truth.

### `PhonePrivateTimelineStore`

Current privacy boundaries retained:

- no raw phone number;
- no message body;
- no URL;
- no sender identifier;
- no OTP;
- bounded local chronology;
- certification provenance.

The v1 core may reuse the same privacy principles, but the timeline remains an audit/read surface rather than the authoritative intelligence event store.

## 4. Architecture

### 4.1 High-level flow

```text
Domain observation
    |
    v
Domain Adapter
    |
    v
IntelligenceEvent (typed, immutable, privacy-bounded)
    |
    v
Local Intelligence Aggregator
    |
    +--> Evidence ledger / bounded persistence
    |
    +--> IntelligenceSnapshot
             |
             +--> Caller-ID projection
             +--> Risk-card projection
             +--> Diagnostics/history projection
             +--> Precomputed screening projection
```

The event flow is asynchronous relative to user-facing critical paths except where a caller explicitly reads an already-built snapshot.

### 4.2 Core packages

Recommended Kotlin package boundary:

`com.sentinel.quantum.intelligence`

Initial types:

- `SentinelIdentityKey`
- `IdentityAlias`
- `IntelligenceEvent`
- `IntelligenceEvidence`
- `EvidenceSource`
- `EvidenceProvenance`
- `EvidenceFreshness`
- `PrivacyClass`
- `RiskAssessment`
- `IntelligenceSnapshot`
- `IntelligenceAggregator`
- `IntelligenceEventSink`
- `IntelligenceSnapshotStore`
- `ScreeningProjection`
- adapters under `com.sentinel.quantum.intelligence.adapters`

The package must not depend on Compose UI classes.

## 5. Identity model

### 5.1 `SentinelIdentityKey`

The shared key must not be a raw telephone number.

Proposed representation:

```kotlin
data class SentinelIdentityKey(
    val namespace: Namespace,
    val value: String,
    val version: Int
)

enum class Namespace {
    PHONE_LOCAL_FINGERPRINT,
    ANDROID_CONTACT,
    SENTINEL_CONTACT,
    UNKNOWN
}
```

Phone identity is produced from the existing normalized-number/fingerprint infrastructure. The exact cryptographic/fingerprint implementation must reuse existing Sentinel phone fingerprinting rather than creating a parallel hash algorithm.

Properties:

- local/device-scoped unless a future explicitly approved protocol defines otherwise;
- versioned so migration is possible;
- never reversible by the core;
- never emitted into logs in full;
- equality is exact on `(namespace, version, value)`.

### 5.2 Identity aliases

One entity can have multiple aliases:

```kotlin
data class IdentityAlias(
    val key: SentinelIdentityKey,
    val relationship: Relationship,
    val source: EvidenceSource
)
```

Examples:

- phone fingerprint ↔ Android contact ID;
- phone fingerprint ↔ Sentinel-local contact ID.

Alias creation is evidence-driven. Name equality alone MUST NOT merge identities.

### 5.3 Contact names

Contact display names and organisations are presentation attributes, not identity keys. Their provenance is `ANDROID_CONTACT_PROVIDER` or `SENTINEL_LOCAL_CONTACT`.

If `READ_CONTACTS` is unavailable, the snapshot records contact state as unavailable/unknown. It must not infer "not in contacts".

## 6. Evidence contract

### 6.1 Immutable event envelope

```kotlin
data class IntelligenceEvent(
    val eventId: String,
    val identityKey: SentinelIdentityKey?,
    val evidence: IntelligenceEvidence,
    val observedAtMs: Long,
    val expiresAtMs: Long?,
    val source: EvidenceSource,
    val provenance: EvidenceProvenance,
    val privacyClass: PrivacyClass
)
```

Required rules:

- `eventId` must be opaque and collision-resistant.
- `observedAtMs` cannot be silently moved forward when source time is invalid; invalid temporal evidence becomes rejected or `UNKNOWN` according to adapter policy.
- `expiresAtMs` may be null only for evidence classes explicitly defined as durable.
- source and provenance are mandatory.
- persistence is allowed only for approved `PrivacyClass` values.

### 6.2 Evidence types

Use sealed/typed evidence, not free-form maps.

Initial evidence families:

```text
ContactIdentityEvidence
CallDecisionEvidence
SmsSecurityEvidence
MmsSecurityEvidence
ReputationEvidence
NetworkVerificationEvidence (future-compatible, not connected in v1)
```

Examples of bounded fields:

`ContactIdentityEvidence`
- contact source state;
- known-contact boolean only when provider state is authoritative;
- bounded display label;
- bounded organisation label;
- contact alias ID.

`CallDecisionEvidence`
- action `ALLOW | SILENCE | BLOCK`;
- rule source category;
- bounded reason code;
- network verification state;
- no raw number.

`SmsSecurityEvidence` / `MmsSecurityEvidence`
- direction;
- transport state;
- bounded phishing/fraud category code;
- analysis outcome;
- no message body, OTP, URL or sender literal.

`ReputationEvidence`
- score 0..100 when valid;
- community signal count;
- bounded fraud taxonomy codes;
- issuer/source label;
- observed time;
- TTL/expiry;
- signed-package verification status where applicable.

## 7. Provenance

Every evidence item carries machine-readable provenance.

```kotlin
data class EvidenceProvenance(
    val producer: Producer,
    val producerVersion: Int,
    val trust: TrustClass,
    val certification: CertificationRef?,
    val issuer: String? = null
)
```

Trust classes:

- `LOCAL_PLATFORM_ASSERTION`
- `LOCAL_USER_ASSERTION`
- `LOCAL_DERIVED`
- `SIGNED_REMOTE_ASSERTION`
- `COMMUNITY_AGGREGATE`
- `UNVERIFIED_REMOTE`

The aggregator must never flatten these trust classes into one indistinguishable score.

A remote community count is not equivalent to a verified Android contact or signed reputation package.

## 8. Freshness and uncertainty

Freshness is first-class.

```kotlin
enum class EvidenceFreshness {
    FRESH,
    STALE,
    UNKNOWN
}
```

Rules:

- expired remote reputation becomes `STALE` and stops contributing numeric risk;
- missing timestamp/TTL is `UNKNOWN`, not fresh;
- future timestamps outside an approved skew are rejected;
- stale evidence may remain visible for explanation/history but cannot silently drive enforcement;
- absence of evidence remains absence, never a zero-risk conclusion.

This preserves the semantics already present in `PhoneRiskCard`.

## 9. Risk aggregation

### 9.1 Separation of evidence and enforcement

No producer may directly mutate a shared global risk score.

Producers publish evidence. `IntelligenceAggregator` computes a `RiskAssessment` deterministically from the currently valid evidence set.

```kotlin
data class RiskAssessment(
    val score: Int?,
    val band: RiskBand,
    val confidence: Confidence,
    val reasons: List<RiskReason>,
    val evaluatedAtMs: Long,
    val freshness: EvidenceFreshness
)
```

### 9.2 Initial aggregation policy

v1 MUST be conservative:

- reuse existing validated reputation score where available;
- attach local evidence/reasons without inventing additive arithmetic;
- do not create a new machine-learning score;
- community evidence alone cannot produce automatic blocking;
- verified contact membership may affect presentation/context but must not automatically override a signed blocking rule;
- stale remote evidence contributes explanation only, not active risk score;
- conflicting evidence remains represented as conflict/reasons rather than being silently discarded.

A later version can introduce a formally specified weighted model only after independent tests and calibration data exist.

## 10. `IntelligenceSnapshot`

The main read model:

```kotlin
data class IntelligenceSnapshot(
    val identityKey: SentinelIdentityKey,
    val aliases: Set<IdentityAlias>,
    val identity: IdentityView,
    val risk: RiskAssessment,
    val activeEvidence: List<IntelligenceEvidenceRef>,
    val staleEvidence: List<IntelligenceEvidenceRef>,
    val updatedAtMs: Long,
    val revision: Long
)
```

Requirements:

- immutable value object;
- monotonically increasing local revision;
- deterministic for the same evidence set and clock input;
- safe to cache;
- contains references/summaries, not raw PII payloads;
- serializable with schema version.

## 11. Event transport

v1 uses an in-process typed event sink, not a general-purpose distributed event bus.

```kotlin
interface IntelligenceEventSink {
    fun publish(event: IntelligenceEvent): PublishResult
}
```

Properties:

- non-blocking for Android callback producers;
- bounded queue;
- explicit overflow metric;
- no unbounded coroutine/channel buffer;
- idempotent event handling by `eventId`;
- failure to publish secondary intelligence must not break telephony/SMS platform obligations.

For processes/components that cannot safely share memory, adapters may persist bounded evidence asynchronously and rebuild snapshots later.

## 12. Critical-path isolation

This is a hard architectural invariant.

`SentinelCallScreeningService.onScreenCall()` MUST NOT:

- query contacts;
- read SharedPreferences/Room for intelligence;
- make HTTP/network calls;
- await event publication;
- calculate full snapshots;
- perform remote reputation lookup.

It may only:

1. perform existing Android role/emergency checks;
2. evaluate current in-memory screening projection;
3. call `respondToCall()`;
4. asynchronously emit bounded post-response evidence.

The screening projection is built outside the callback from trusted active rules.

This preserves the existing sub-500 ms target and fail-open behavior.

## 13. Screening projection

`ScreeningProjection` is deliberately narrower than `IntelligenceSnapshot`.

It contains only enforcement-safe material already authorized by existing policy:

- local user blocking fingerprints/prefixes;
- validated signed remote blocking/silence rules;
- expiry/sequence metadata required to invalidate stale rules.

It MUST NOT include:

- arbitrary community score thresholds;
- contact names;
- UI risk band;
- unverified remote evidence;
- message-derived speculative identity.

Projection rebuild happens outside the screening callback. Publication uses immutable snapshot replacement / atomic reference semantics.

## 14. Persistence

v1 does not introduce a large new relational database.

Recommended split:

- existing domain stores remain authoritative for their domain;
- core persists only bounded evidence/snapshot metadata required for restart and correlation;
- no raw phone number or message payload in core persistence;
- schema-version every persisted structure;
- enforce retention limit by count and age;
- corrupted persistence fails to `UNKNOWN` / empty evidence, not to permissive fabricated facts;
- sensitive local state remains app-private and excluded from Android backup where applicable.

Before implementation, the plan must choose the minimal existing persistence mechanism compatible with these constraints and benchmark startup/rebuild cost.

## 15. Producer adapters v1

### Contact adapter

Input: `LocalContactLookup` result.

Output:

- `ContactIdentityEvidence` only when permission/provider state permits a truthful assertion;
- Android contact alias;
- optional display name/organisation presentation attributes.

### Call adapter

Input: completed `CallRuleEngine.Decision` and network verification status after `respondToCall()`.

Output:

- `CallDecisionEvidence`;
- no raw number;
- links to local fingerprint when available from already-computed bounded context.

### SMS/MMS adapter

Input: existing analysis/transport outcomes.

Output:

- direction/state/category/outcome metadata;
- no content, URL, OTP or sender literal.

### Reputation adapter

Input: existing signed/local reputation result.

Output:

- validated score;
- category codes;
- community count;
- signed/unverified provenance;
- source observation time and TTL.

## 16. Consumer projections v1

### Caller ID

Reads `IntelligenceSnapshot` outside the mandatory call-response path and displays:

- contact-derived identity when available;
- provenance/source label;
- risk band/freshness;
- explanation reasons.

No invented person/company identity is permitted.

### Phone Security / Risk UI

`PhoneRiskCard` becomes a presentation mapping from snapshot assessment and retains current thresholds/labels unless a separately approved product change modifies them.

### Private timeline

Timeline can receive a compact reference to intelligence outcome categories but remains bounded and PII-free. It must not become a duplicate event store.

### Call screening

Consumes only `ScreeningProjection`, never general `IntelligenceSnapshot`.

## 17. Concurrency and consistency

- Event ingestion is serialized per identity key or otherwise deterministic.
- Snapshot replacement is atomic.
- Readers never observe partially-built mutable snapshots.
- Duplicate event IDs are no-ops.
- Out-of-order events are evaluated by observed time plus source sequence where available.
- Signed reputation rollback/replay protections remain in their existing verifier and are not reimplemented in the aggregator.
- Core restart rebuild must produce the same snapshot from the same retained evidence.

## 18. Failure semantics

### Core unavailable

Product domains continue their current behavior.

- Call screening: existing local rule engine still functions.
- SMS/MMS transport: continues independently.
- Contacts: UI reports its existing permission/provider state.
- Reputation enrichment: reports unavailable/unknown.

### Event queue full

- do not block platform callbacks;
- increment a bounded diagnostic counter;
- preserve existing domain audit where already available;
- do not fabricate successful publication.

### Persistence corrupt/unreadable

- isolate the bad core state;
- rebuild from authoritative domain sources where possible;
- otherwise return `UNKNOWN` rather than stale fabricated confidence.

### Conflicting identity evidence

- do not auto-merge by display name;
- retain separate aliases until a strong matching key exists;
- surface conflict diagnostically where useful.

## 19. Privacy model

`PrivacyClass` initial values:

- `PUBLIC_SECURITY_METADATA`
- `LOCAL_PSEUDONYMOUS`
- `LOCAL_CONTACT_ATTRIBUTE`
- `LOCAL_SENSITIVE_DERIVED`
- `NOT_PERSISTABLE`

Rules:

- raw phone numbers are `NOT_PERSISTABLE` in the core;
- message bodies, OTPs and URLs are `NOT_PERSISTABLE`;
- local phone fingerprints are `LOCAL_PSEUDONYMOUS`;
- contact display name/organisation remain local and require the original Android/Sentinel contact permission/context;
- community export is out of scope and therefore no v1 core event is automatically uploadable;
- future upload adapters require explicit consent and a separate data-minimization spec.

## 20. Observability

Observability must be useful without leaking identity.

Allowed metrics/logs:

- event type;
- producer;
- accepted/rejected/duplicate counts;
- queue depth/overflow;
- aggregation latency;
- snapshot revision counts;
- freshness state counts;
- failure reason codes.

Forbidden in generic logs:

- raw number;
- contact name;
- organisation;
- message content;
- URL;
- OTP;
- full fingerprint/key value.

## 21. Migration strategy

### Phase 1 — contracts only

Add typed core models, deterministic aggregator and unit tests. No existing product path changes.

### Phase 2 — passive producers

Attach contact, reputation, SMS/MMS and post-response call adapters. Core snapshots are generated but not used for enforcement.

### Phase 3 — read-only consumers

Caller ID and Phone Security UI read snapshot projections. Existing fallback paths remain available until equivalence tests pass.

### Phase 4 — screening projection bridge

Connect only enforcement-safe, already-authorized rule material to the immutable `ScreeningProjection`. Existing `CallRuleEngine` remains unchanged.

### Phase 5 — remove duplicate glue

Only after equivalence and physical/emulator qualification, remove obsolete per-screen correlation code that duplicates the core. Domain stores remain unless a separate migration is approved.

No phase may mix implementation with community-cloud synchronization.

## 22. Testing strategy

### Unit tests

Required before adapters are wired:

- identity-key equality/versioning;
- no raw-number persistence contract;
- alias merge rules;
- duplicate-event idempotence;
- deterministic aggregation;
- stale/unknown freshness behavior;
- conflicting evidence preservation;
- community-only evidence cannot authorize blocking;
- corrupted/invalid evidence rejection;
- snapshot revision monotonicity;
- bounded queue overflow semantics.

### Contract tests

Static/structural gates must assert:

- `SentinelCallScreeningService` does not instantiate/read general core persistence on callback path;
- no network API is reachable from screening projection evaluation;
- core persisted DTOs contain no raw number/message/OTP/URL field;
- `ScreeningProjection` accepts only approved evidence/rule classes;
- all persisted schemas include a version.

### Integration tests

- Android contact permission denied → no false `not-known` assertion;
- contact provider unavailable → `UNKNOWN` identity context;
- valid contact → snapshot identity projection;
- fresh signed reputation → active risk assessment;
- expired reputation → stale explanation, no active score contribution;
- SMS/MMS events remain content-free;
- call decision publication failure does not change Android call response;
- restart/rebuild produces equivalent snapshot.

### Emulator qualification

Extend existing Phone Core qualification with evidence that:

- call-screening response latency stays within existing budget;
- general core persistence is not touched before response;
- post-response event publication happens only after platform response;
- role revocation behavior remains unchanged;
- API 24/29/36/37 behavior remains valid.

### Physical-device qualification

Galaxy S24+ remains required for final telephony/contact behavior validation, especially OEM contact-provider behavior, actual carrier call verification, SMS/MMS and permission flows.

## 23. Security invariants

The following are release blockers if violated:

1. No network dependency on `CallScreeningService` decision path.
2. No raw phone number/message body/OTP/URL in core persistence.
3. No automatic block from community reputation alone.
4. No identity merge by display name alone.
5. No stale reputation contribution to active numeric risk.
6. No silent conversion of unavailable evidence into `LOW` risk.
7. No mutable snapshot shared across threads.
8. No unsigned/unverified remote rule entering `ScreeningProjection`.
9. Emergency-number safety behavior remains fail-open.
10. Core failure never prevents mandatory Android Telecom/SMS callback completion.

## 24. Success criteria for v1

v1 is complete when all of the following are proven:

- contacts, calls, SMS/MMS and reputation publish typed local evidence;
- one deterministic snapshot correlates those facts by pseudonymous identity key;
- Caller ID and risk UI can consume the snapshot without inventing identity;
- screening still runs only on immutable enforcement projection and existing rule engine;
- no raw sensitive content appears in core persistence/logs;
- stale/unknown/provenance states remain explicit end to end;
- existing API 24/29/36/37 Phone Core qualification remains green;
- new core contract tests are green;
- physical S24+ qualification finds no regression in contacts/calls/SMS/MMS.

## 25. Deferred v2+ work

The following require separate design approval:

- anonymous community contribution/upload protocol;
- cross-device/federated identity correlation;
- automatic contact enrichment from external sources;
- INSEE/business-directory enrichment;
- campaign clustering across devices;
- server-side risk fusion;
- referral/customer-growth features;
- machine-learning scoring;
- cloud event streaming.

The v1 contracts are intentionally designed so these can be added through new adapters without weakening local privacy or telephony isolation.

## 26. Decision record

Selected architecture: **B — event-driven typed contracts with local aggregation and isolated enforcement projection**.

Rejected for v1:

- monolithic central service: excessive coupling and risk to telephony latency;
- database-centered integration: persistence becomes an implicit cross-module API and encourages I/O on critical paths.

The implementation plan must preserve incremental migration, TDD, exact-head CI evidence and independent verification before any product path switches from existing glue to the new core.
