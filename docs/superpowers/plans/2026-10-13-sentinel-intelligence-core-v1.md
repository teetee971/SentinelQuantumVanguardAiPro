# Sentinel Intelligence Core v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a local typed intelligence integration layer that correlates contacts, calls, SMS/MMS and reputation into deterministic privacy-bounded snapshots without rewriting existing domain engines or weakening Phone Core real-time guarantees.

**Architecture:** Add `com.sentinel.quantum.intelligence` as a pure Kotlin domain boundary with immutable typed events, pseudonymous identity keys, provenance/freshness, deterministic aggregation and bounded projections. Existing producers adapt into the core asynchronously; `CallScreeningService` continues to make synchronous decisions only from precomputed in-memory screening state and never depends on the general intelligence snapshot path.

**Tech Stack:** Kotlin, Android SDK, existing Sentinel fingerprinting and Phone Core classes, JUnit, existing Gradle test/lint/build gates, GitHub Actions emulator qualification API 24/29/36/37.

**Spec:** `docs/superpowers/specs/2026-10-13-sentinel-intelligence-core-v1-design.md`

## Global Constraints

- `SentinelCallScreeningService.onScreenCall()` MUST NOT query contacts, read general intelligence persistence, make network calls, await event publication, calculate full snapshots or perform remote reputation lookup before `respondToCall()`.
- Raw phone numbers, SMS/MMS bodies, URLs, sender literals and OTP values MUST NOT be persisted by Intelligence Core v1.
- Existing `CallRuleEngine`, `LocalContactLookup`, SMS/MMS transport, signed reputation verification and role/permission semantics are adapted, not rewritten.
- Community reputation is evidence only and MUST NOT independently authorize automatic blocking.
- Missing, stale, conflicting or unavailable evidence MUST remain explicit and MUST NOT be silently converted to fresh/low-risk/verified state.
- Identity correlation MUST reuse existing Sentinel normalized-number/fingerprint infrastructure; do not introduce a parallel hash algorithm.
- Persisted structures MUST be schema-versioned and bounded by retention policy.
- General `IntelligenceSnapshot` MUST remain distinct from enforcement-only `ScreeningProjection`.
- Snapshot generation MUST be deterministic for the same retained evidence set and supplied clock.
- Duplicate event IDs MUST be idempotent no-ops.
- Product verification must preserve Android API 24/29/36/37 emulator qualification and physical S24+ follow-up boundaries.

## Review Focus

1. **Missing contact permission/provider failure** — must produce unknown/unavailable identity evidence, never “not in contacts”. Covered in Task 4 adapter tests.
2. **Stale or future-dated reputation evidence** — stale must stop contributing numeric risk; invalid future time must be rejected/unknown. Covered in Task 3 aggregation tests and Task 6 reputation adapter tests.
3. **Duplicate/out-of-order events** — duplicates must be idempotent and ordering deterministic using observed time/source sequence when present. Covered in Task 3 tests.
4. **Event queue saturation/core failure** — Phone/SMS platform obligations must continue; overflow is observable without blocking callback paths. Covered in Task 2 tests and Task 7 integration tests.
5. **PII leakage through persistence/logging** — raw numbers/message bodies/URLs/sender literals/OTP must be rejected from core persistence and must not appear in serialized snapshots. Covered in Task 5 tests and Task 8 product-truth guards.

---

## File Structure

### New core domain files

- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/SentinelIdentityKey.kt` — identity namespaces, aliases and versioned pseudonymous keys.
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceEvidence.kt` — sealed evidence families and bounded evidence fields.
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceEvent.kt` — immutable event envelope, source, provenance, freshness/privacy metadata.
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceSnapshot.kt` — immutable read model and risk assessment types.
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceAggregator.kt` — deterministic evidence aggregation.
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceEventSink.kt` — bounded publication contract and in-process implementation.
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceSnapshotStore.kt` — bounded schema-versioned app-private persistence/rebuild seam.
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/ScreeningProjection.kt` — enforcement-safe immutable projection contract.

### New adapters

- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/adapters/ContactIntelligenceAdapter.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/adapters/CallIntelligenceAdapter.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/adapters/SmsMmsIntelligenceAdapter.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/adapters/ReputationIntelligenceAdapter.kt`

### Existing files expected to change

- `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt` — post-response evidence publication only; no critical-path dependency.
- `native-android-app/app/src/main/java/com/sentinel/quantum/security/LocalContactLookup.kt` — expose/reuse bounded canonical identity input if needed; no provider behavior change.
- Existing SMS/MMS analysis/delivery producers — emit bounded metadata only after existing platform work.
- `native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt` — consume snapshot projection outside mandatory response path.
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt` — map snapshot risk into existing `PhoneRiskCard` presentation.
- `scripts/check-android-product-truth.js` — static invariants for privacy and critical-path separation.
- `scripts/check-phone-core-emulation-gate.js` and/or qualification scripts only if runtime evidence needs new markers; no gate weakening.

### Test files

Create focused JUnit files under:

- `native-android-app/app/src/test/java/com/sentinel/quantum/intelligence/`
- `native-android-app/app/src/test/java/com/sentinel/quantum/intelligence/adapters/`

Do not fold unrelated existing tests into a single large Intelligence Core test file.

---

### Task 1: Core Identity and Evidence Contracts

**Files:**
- Create: `.../intelligence/SentinelIdentityKey.kt`
- Create: `.../intelligence/IntelligenceEvidence.kt`
- Create: `.../intelligence/IntelligenceEvent.kt`
- Test: `.../intelligence/IntelligenceContractsTest.kt`

**Interfaces:**
- Consumes: existing Sentinel phone fingerprint output as an opaque value; no new hashing implementation.
- Produces: `SentinelIdentityKey`, `IdentityAlias`, `IntelligenceEvidence`, `IntelligenceEvent`, `EvidenceSource`, `EvidenceProvenance`, `PrivacyClass`, freshness/trust enums used by every later task.

- [ ] **Step 1: Write failing contract tests**

Test names/assertions:
- `phone_identity_key_is_versioned_and_never_raw_number_namespace()`
- `name_equality_cannot_create_identity_alias()`
- `event_requires_source_provenance_and_observed_time()`
- `privacy_class_rejects_raw_phone_message_url_sender_and_otp_payload_types()`
- `durable_evidence_requires_explicit_durable_classification()`

- [ ] **Step 2: Run focused tests and confirm RED**

Run:
`cd native-android-app && ./gradlew testDebugUnitTest --tests 'com.sentinel.quantum.intelligence.IntelligenceContractsTest'`

Expected: FAIL because contracts/types do not exist.

- [ ] **Step 3: Implement minimal immutable contract types**

Required signatures/names:
- `data class SentinelIdentityKey(val namespace: Namespace, val value: String, val version: Int)`
- `data class IdentityAlias(val key: SentinelIdentityKey, val relationship: Relationship, val source: EvidenceSource)`
- sealed `IntelligenceEvidence` families: contact, call decision, SMS, MMS, reputation.
- `data class IntelligenceEvent(...)` matching the spec fields.

Do not add persistence or aggregation in this task.

- [ ] **Step 4: Run focused tests and confirm GREEN**

- [ ] **Step 5: Run existing Phone Core unit-test subset to prove no regression**

- [ ] **Step 6: Commit**

Commit message: `feat(intelligence): add typed identity and evidence contracts`

---

### Task 2: Bounded Event Sink and Backpressure Semantics

**Files:**
- Create: `.../intelligence/IntelligenceEventSink.kt`
- Test: `.../intelligence/IntelligenceEventSinkTest.kt`

**Interfaces:**
- Consumes: `IntelligenceEvent` from Task 1.
- Produces: `interface IntelligenceEventSink { fun publish(event: IntelligenceEvent): PublishResult }`, bounded in-process implementation, queue-overflow counters/status.

- [ ] **Step 1: Write failing tests**

Required cases:
- publication returns promptly when consumer is slow;
- queue capacity is bounded;
- overflow returns explicit non-success result and increments metric;
- duplicate enqueue before processing does not create unbounded duplicates if the same `eventId` is already pending;
- sink failure never throws into producer callback by default contract.

- [ ] **Step 2: Run focused tests and confirm RED**

- [ ] **Step 3: Implement bounded sink**

Use an existing bounded executor/queue pattern from the repository where practical. No unbounded coroutine/channel buffers.

- [ ] **Step 4: Run focused tests and confirm GREEN**

- [ ] **Step 5: Commit**

Commit message: `feat(intelligence): add bounded event publication`

---

### Task 3: Deterministic Aggregator and Snapshot Model

**Files:**
- Create: `.../intelligence/IntelligenceSnapshot.kt`
- Create: `.../intelligence/IntelligenceAggregator.kt`
- Test: `.../intelligence/IntelligenceAggregatorTest.kt`

**Interfaces:**
- Consumes: Task 1 events/evidence.
- Produces: immutable `IntelligenceSnapshot`, `RiskAssessment`, active/stale evidence references, deterministic `aggregate(identityKey, events, nowMs)` API.

- [ ] **Step 1: Write failing tests**

Required cases:
- same evidence + same `nowMs` yields equal snapshot;
- duplicate `eventId` is a no-op;
- expired reputation is moved to stale and contributes no numeric risk;
- missing TTL/timestamp freshness is `UNKNOWN`, not `FRESH`;
- future timestamp outside allowed skew is rejected/unknown;
- community aggregate alone cannot produce enforcement/blocking authority;
- conflicting verified-contact and signed reputation evidence remains represented as separate reasons;
- events presented out of order produce same final snapshot when source sequence/time is equivalent;
- snapshot revision increases only when accepted evidence set changes.

- [ ] **Step 2: Run focused tests and confirm RED**

- [ ] **Step 3: Implement minimal deterministic aggregation**

No weighted ML/new risk arithmetic. Reuse validated reputation score semantics; attach other evidence as contextual reasons/provenance.

- [ ] **Step 4: Run focused tests and confirm GREEN**

- [ ] **Step 5: Run existing `PhoneRiskCardTest` to preserve current thresholds/UNKNOWN/STALE semantics**

- [ ] **Step 6: Commit**

Commit message: `feat(intelligence): aggregate deterministic local snapshots`

---

### Task 4: Contact Identity Adapter

**Files:**
- Create: `.../intelligence/adapters/ContactIntelligenceAdapter.kt`
- Test: `.../intelligence/adapters/ContactIntelligenceAdapterTest.kt`
- Modify only if required: `.../security/LocalContactLookup.kt`

**Interfaces:**
- Consumes: `LocalContactLookup.ContactListResult`, `Identity`, existing fingerprint/canonical-number service.
- Produces: contact identity events and aliases for `IntelligenceEventSink`.

- [ ] **Step 1: Write failing adapter tests**

Required cases:
- `READY` + matching provider contact emits contact evidence with Android-contact alias;
- `PERMISSION_REQUIRED` emits no “not in contacts” assertion;
- `PROVIDER_UNAVAILABLE` emits unavailable/unknown state only;
- display name/organisation remain presentation attributes, never primary identity keys;
- two equal names with different fingerprints are never merged;
- raw number is absent from emitted event serialization/debug representation used by tests.

- [ ] **Step 2: Run focused tests and confirm RED**

- [ ] **Step 3: Implement adapter using existing normalization/fingerprinting seam**

If `LocalContactLookup` needs a new bounded accessor, add only the smallest method required; do not change provider query semantics.

- [ ] **Step 4: Run adapter + existing `ContactDirectoryPolicyTest` and confirm GREEN**

- [ ] **Step 5: Commit**

Commit message: `feat(intelligence): adapt local contacts into identity evidence`

---

### Task 5: Bounded Persistence and Restart Rebuild

**Files:**
- Create: `.../intelligence/IntelligenceSnapshotStore.kt`
- Test: `.../intelligence/IntelligenceSnapshotStoreTest.kt`

**Interfaces:**
- Consumes: sanitized events/snapshots from Tasks 1–3.
- Produces: schema-versioned bounded persistence, `read`, `write/append`, `rebuild` seam, retention enforcement.

- [ ] **Step 1: Write failing persistence tests**

Required cases:
- serialized storage contains no raw phone number/message body/URL/sender/OTP fixtures;
- corrupted payload returns empty/UNKNOWN state rather than fabricated facts;
- unknown future schema fails safely;
- retention enforces both maximum age and count;
- restart rebuild from same retained evidence reproduces the same snapshot;
- app-private store is not used from `CallScreeningService.onScreenCall()`.

- [ ] **Step 2: Run focused tests and confirm RED**

- [ ] **Step 3: Implement minimal schema-versioned app-private storage**

Prefer existing lightweight repository persistence patterns unless benchmark evidence requires Room. Do not introduce a new database dependency in v1.

- [ ] **Step 4: Run focused tests and confirm GREEN**

- [ ] **Step 5: Commit**

Commit message: `feat(intelligence): persist bounded intelligence evidence`

---

### Task 6: Call, SMS/MMS and Reputation Producer Adapters

**Files:**
- Create: `.../intelligence/adapters/CallIntelligenceAdapter.kt`
- Create: `.../intelligence/adapters/SmsMmsIntelligenceAdapter.kt`
- Create: `.../intelligence/adapters/ReputationIntelligenceAdapter.kt`
- Tests: one focused test file per adapter.

**Interfaces:**
- Consumes: existing `CallRuleEngine.Decision`, SMS/MMS bounded transport/analysis outcomes, existing signed/local reputation result types.
- Produces: privacy-bounded Task 1 events into `IntelligenceEventSink`.

- [ ] **Step 1: Write failing call-adapter tests**

Assert action/source/reason/network verification map correctly; raw number never enters event payload.

- [ ] **Step 2: Write failing SMS/MMS-adapter tests**

Assert direction/state/category/outcome only; reject body/URL/OTP/sender literal retention.

- [ ] **Step 3: Write failing reputation-adapter tests**

Assert 0..100 validation, signed provenance preservation, community count clamping, TTL propagation, stale/future handling and unverified remote trust classification.

- [ ] **Step 4: Run all three adapter suites and confirm RED**

- [ ] **Step 5: Implement minimal adapters without modifying transport engines/verifier logic**

- [ ] **Step 6: Run adapter tests + relevant existing SMS/MMS/reputation tests and confirm GREEN**

- [ ] **Step 7: Commit**

Commit message: `feat(intelligence): adapt phone security producers`

---

### Task 7: Integrate Consumers Without Violating Critical Paths

**Files:**
- Create: `.../intelligence/ScreeningProjection.kt`
- Modify: `.../security/SentinelCallScreeningService.kt`
- Modify: `.../CallerIdActivity.kt`
- Modify: `.../ui/screens/PhoneSecurityScreen.kt`
- Tests: `.../intelligence/ScreeningProjectionTest.kt`, plus focused integration/unit tests near existing consumers.

**Interfaces:**
- Consumes: Task 3 snapshot, existing call-rule snapshots, Task 2 event sink.
- Produces: enforcement-safe `ScreeningProjection`; post-response call evidence; snapshot-backed Caller ID/risk presentation.

- [ ] **Step 1: Write failing screening-projection tests**

Required cases:
- projection includes only user-owned blocking rules and already-authorized validated signed blocking/silence rules;
- excludes community-only scores, contact names, UI bands, unverified remote evidence and message-derived speculative identity;
- immutable projection replacement is atomic from reader perspective.

- [ ] **Step 2: Write failing CallScreening integration/static contract tests**

Assert no contact query, no `IntelligenceSnapshotStore` read, no network and no aggregator invocation occurs before `respondToCall()`; evidence publication occurs only in post-response work.

- [ ] **Step 3: Implement `ScreeningProjection` and post-response call publication**

Do not replace existing `CallRuleEngine` evaluation semantics in this task.

- [ ] **Step 4: Add Caller ID and Phone Security snapshot consumption outside mandatory call-response path**

Map snapshot risk into existing `PhoneRiskCard`; do not change current risk thresholds.

- [ ] **Step 5: Run focused tests and confirm GREEN**

- [ ] **Step 6: Run existing CallScreening/Phone Core unit suites and latency-sensitive static guards**

- [ ] **Step 7: Commit**

Commit message: `feat(intelligence): connect safe consumer projections`

---

### Task 8: Product-Truth Guards, Runtime Qualification and Migration Gate

**Files:**
- Modify: `scripts/check-android-product-truth.js`
- Modify only as required: Phone Core emulation evidence scripts/workflow contracts.
- Add: targeted JS/static contract tests if the existing product-truth harness supports separate test files.
- Update: relevant architecture/capability docs only after runtime proof exists.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: repository-enforced architecture invariants and exact-head release evidence.

- [ ] **Step 1: Write RED static/product-truth guards**

Guards must fail if:
- `SentinelCallScreeningService` imports/uses general intelligence persistence or contact lookup before response;
- core persistence contains forbidden raw-PII field names/types;
- `ScreeningProjection` admits community score/UI risk/contact identity fields;
- adapters bypass provenance/freshness metadata;
- general snapshot is used as direct automatic-blocking authority.

- [ ] **Step 2: Run `npm run test:android-product-truth` and verify RED only for new contracts before guard implementation adjustments**

- [ ] **Step 3: Make minimal code/static-guard changes needed for GREEN without weakening existing checks**

- [ ] **Step 4: Run complete local validation**

Commands:
- `npm run test:android-product-truth`
- Android unit tests for intelligence package and affected Phone Core suites
- Android lint
- debug APK build

Expected: all GREEN.

- [ ] **Step 5: Run exact-head CI qualification**

Required successful workflows at minimum:
- Build Native Android APK
- Android Instrumentation
- Phone Core Android Emulation Qualification API 24/29/36/37
- Repository Pre-production Validation
- Product Capability Truth
- Security Governance Validation
- CodeQL Repository Analysis
- Production Merge Gate

No rerun may be interpreted as success until final exact-head conclusions are GREEN/explicitly accepted truthful limitation.

- [ ] **Step 6: Inspect API 24/29/36/37 artifacts for evidence integrity**

Confirm no fabricated PASS, no new callback-path I/O and no PII in evidence artifacts.

- [ ] **Step 7: Perform physical S24+ validation boundary**

Required manual/device evidence for production-readiness claim:
- incoming call screening remains responsive;
- caller ID snapshot enrichment appears after mandatory response boundary;
- contacts permission denial stays UNKNOWN/unavailable;
- SMS/MMS transport remains functional;
- no raw PII appears in exported diagnostic evidence.

Do not claim physical validation before this evidence exists.

- [ ] **Step 8: Update capability/architecture documentation with measured final state**

- [ ] **Step 9: Commit**

Commit message: `test(intelligence): lock integration and privacy invariants`

---

## Execution Order and Review Gates

1. Tasks 1–3 establish pure contracts and deterministic behavior; no Android integration should start before these are independently green.
2. Tasks 4–6 add producers one domain at a time; each may be reviewed/rejected independently.
3. Task 7 is the highest-risk integration task because it touches `CallScreeningService`; require a fresh review specifically for critical-path I/O and authorization boundaries.
4. Task 8 is release qualification and must not weaken existing static/runtime gates to obtain GREEN.

## Definition of Done

Intelligence Core v1 is complete only when:

- the typed local core exists and all producer adapters emit bounded evidence;
- deterministic snapshots survive restart/rebuild equivalently;
- Caller ID and Phone Security consume the shared snapshot safely;
- call screening remains independent from the general core on its mandatory response path;
- community evidence cannot independently block;
- PII exclusions are enforced by unit/static tests, not documentation alone;
- exact-head repository CI is green across required workflows;
- emulator API 24/29/36/37 evidence is truthful;
- physical S24+ validation is recorded before any production-readiness claim.
