# Sentinel Collective Defense — Design Specification

Date: 2026-10-12
Status: Design approved in conversation; pending written-spec review
Scope: Sentinel Quantum Vanguard AI Pro — community threat intelligence and collaborative call-defense subsystem

## 1. Objective

Sentinel Collective Defense turns Sentinel from a purely local call-protection application into a privacy-preserving community defense network. Participating devices contribute minimal pseudonymized threat signals so Sentinel can detect emerging telemarketing, robocall, wangiri, spoofing, impersonation and fraud campaigns earlier than a static blocklist could.

The system must remain defensive. It detects, classifies, explains and distributes protection intelligence. It must not enable harassment, retaliation, automated callbacks, offensive actions or cross-user tracking.

Success means:

- Phone Core remains fast and usable offline.
- No network call is required in the CallScreeningService critical path.
- Community intelligence can accelerate detection of new campaigns.
- False positives are reversible and measurable.
- Participation is opt-in and privacy-preserving.
- A compromised or malicious contributor cannot unilaterally force global blocking.
- Distributed rules are signed, versioned, expiring and rollback-safe.

## 2. Participation model

Sentinel exposes three explicit participation levels:

1. `NO_SHARING`
   - No community contribution.
   - Device may still download public threat bundles.

2. `MINIMAL_ANONYMOUS`
   - Sends only the minimum pseudonymized threat telemetry required for aggregate reputation.
   - No raw contact data, message content, notes, audio or precise location.

3. `ADVANCED_ANONYMOUS`
   - Adds behavioral campaign signals such as cadence, number rotation patterns, coarse temporal windows and correction feedback.
   - Still excludes contact contents, SMS/MMS contents, audio, notes and precise location.

Participation can be changed or disabled by the user at any time.

## 3. Data classification

The architecture enforces four distinct data classes:

### 3.1 Local-only data

Never submitted as community telemetry:

- contact records and address book contents;
- detailed call history beyond what is needed for local scoring;
- SMS/MMS contents;
- notes and private contact metadata;
- recordings and transformed voice content;
- biometric state;
- precise GPS location;
- protected-contact contents.

### 3.2 Community telemetry

Permitted only after the local Privacy Gateway validates the user's participation level:

- pseudonymized phone-number token;
- event category;
- rounded time bucket;
- coarse territory/country classification;
- local risk category;
- campaign characteristics allowed by policy;
- explicit user correction such as spam or legitimate;
- rule/model version identifiers.

### 3.3 Public intelligence

Public or official information acquired independently of community telemetry, such as numbering rules, official business data and published fraud indicators.

### 3.4 Account data

Optional cloud/account information used only when the user explicitly connects or enables such services.

## 4. Privacy Gateway

Every outbound community event passes through an on-device `PrivacyGateway` before transmission.

Responsibilities:

- enforce the current participation tier;
- reject prohibited fields;
- generalize location to an approved coarse region;
- round timestamps;
- strip address-book and message data;
- apply batching and delayed submission;
- produce only schema-approved event structures;
- prevent any producer from bypassing the gateway.

Community submission must be delayed and batched where practical. The system must not transmit an event synchronously at the moment an incoming call is screened.

## 5. Pseudonymization

A raw unsalted hash of a telephone number is prohibited because telephone-number spaces are enumerable.

The system must use a server-coordinated keyed pseudonymization design, such as HMAC-derived identifiers, without distributing the global secret to devices. The implementation must support controlled rotation of pseudonymization epochs so long-term correlation is limited.

The exact protocol is an implementation detail to be selected in the implementation plan, but these invariants are mandatory:

- no plaintext phone number in community event storage;
- no simple SHA-style public hash used as a stable global identifier;
- support for epoch rotation;
- aggregation remains possible inside an epoch;
- migration between epochs does not expose raw identifiers to clients.

## 6. Threat campaign model

A community campaign is represented as `ThreatCampaign`.

Required campaign fields include:

- opaque campaign ID;
- campaign type;
- state;
- confidence score;
- first and last observation windows;
- coarse territories affected;
- behavioral fingerprint;
- associated pseudonymized number clusters;
- explanation codes;
- current ruleset version;
- expiration time;
- lineage metadata when related to a prior campaign.

Supported initial campaign categories:

- `TELEMARKETING`
- `ROBOCALL`
- `SCAM`
- `IMPERSONATION`
- `PREMIUM_RATE`
- `WANGIRI`
- `SPOOFING`
- `UNKNOWN_CAMPAIGN`

## 7. Campaign lifecycle

Campaign state is not a simple good/bad flag.

Required states:

- `OBSERVED`: isolated or low-confidence activity; no community-driven automatic blocking.
- `EMERGING`: multiple independent signals show a coherent campaign pattern.
- `CONFIRMED`: sufficient independent evidence and low contradiction rate justify distribution as strong threat intelligence.
- `DISPUTED`: credible contradictory or legitimate reports require conservative handling.
- `UNDER_REVIEW`: campaign or associated reputation is being formally reassessed.
- `DECLINING`: activity or confidence is dropping.
- `EXPIRED`: no longer used for new blocking decisions.

Transitions must be based on measurable thresholds and must be auditable.

## 8. Community scoring

A campaign confidence score is derived from independent sub-scores rather than raw report count.

Initial scoring dimensions:

- `volumeScore`
- `diversityScore`
- `velocityScore`
- `patternScore`
- `reputationScore`
- `freshnessScore`
- `correctionPenalty`

Important rules:

- repeated reports from one contributor do not count like reports from many independent contributors;
- newer observations weigh more heavily than old observations;
- explicit legitimate corrections reduce confidence;
- a single correction does not erase large coherent evidence;
- campaign type influences action thresholds;
- community confidence is advisory input, not a direct device command.

Server scoring and local action thresholds remain separate.

## 9. Contributor trust and anti-abuse

Each pseudonymous installation may build a non-user-facing `ContributorTrustScore`.

Trust must depend on quality and historical consistency, not volume of submissions.

Anti-abuse controls include:

- per-installation and per-window rate limits;
- duplicate suppression;
- contribution caps;
- correlation detection for suspiciously synchronized contributors;
- weight reduction for contributors with frequent contradicted reports;
- reduced initial weight for newly created pseudonymous installations;
- burst detection;
- server-side circuit breakers;
- global caps preventing a small contributor group from dominating a campaign.

No single contributor or small colluding group may directly force a globally distributed block verdict.

## 10. Spoofing and attribution safety

Number reputation, organization reputation and campaign reputation are separate entities.

If caller-ID spoofing is probable, Sentinel must classify the event as `SPOOFED_IDENTITY` or equivalent rather than automatically damaging the reputation of the legitimate owner of the displayed number.

A negative campaign association must not automatically contaminate every number of an organization.

## 11. Campaign fingerprinting

`CampaignFingerprint` represents behavioral patterns beyond a single number.

Possible signals include:

- high-rate rotation across nearby number ranges;
- recurring time windows;
- repeated short-call behavior;
- similar call cadence across independent devices;
- common threat category;
- regional spread patterns;
- similar correction patterns;
- repeated lineage from older campaigns.

The fingerprint enables Sentinel to recognize new number variants of a known campaign without requiring each individual number to accumulate its own reputation first.

The feature must use conservative thresholds and must never block an entire number range merely because two or three adjacent numbers were reported.

## 12. Threat bundles

Devices consume signed `ThreatBundle` artifacts for offline protection.

Required bundle properties:

- monotonic version;
- creation timestamp;
- expiration timestamp;
- compatible minimum engine version;
- global or regional scope;
- active campaign rules;
- campaign confidence summaries;
- compact explanation codes;
- previous-bundle hash or equivalent chain metadata;
- cryptographic signature;
- rollback protection.

Invalid, unsigned, expired or rollbacked bundles are rejected.

The device always retains the last valid usable bundle until it expires or is explicitly revoked.

## 13. Emergency revocation

The backend can distribute a small signed `EmergencyRevoke` artifact to neutralize a bad campaign or rule before the next full bundle release.

Emergency revocation must:

- be cryptographically signed;
- identify affected campaign/rule versions;
- have bounded lifetime;
- be locally auditable;
- never require enabling arbitrary remote code execution or policy injection.

## 14. Local Phone Core integration

The community network must not perform network I/O in `CallScreeningService`.

Incoming-call critical path:

`CallScreeningService`
→ normalize number
→ read local risk snapshot
→ consult personal allow/block policy
→ consult current valid ThreatBundle
→ run local risk engine
→ apply local mode thresholds
→ `respondToCall()`
→ schedule post-call/background enrichment

Community intelligence is only one input into the local decision.

The user's local policy remains authoritative.

Initial local outcomes:

- `ALLOW`
- `WARN`
- `SILENCE`
- `BLOCK`

A future `CHALLENGE` outcome is allowed only when a technically supported telephony or VoIP/PSTN flow exists; it must not be faked through CallScreeningService alone.

## 15. Protection profiles

Community intelligence is interpreted differently by local protection mode:

### Standard

Conservative. Community intelligence primarily warns unless confidence is very high and the threat category is severe.

### Reinforced

Uses confirmed campaigns for silence/block decisions with moderate thresholds.

### Maximum

Aggressive against high-confidence telemarketing and fraud campaigns, while preserving explicit allowlists, trusted contacts and safety exemptions.

Unknown numbers are not automatically blocked solely because they are unknown.

## 16. Safety exemptions

Local policy must protect against catastrophic false positives.

At minimum, the decision engine must preserve explicit allowlists and contacts designated as trusted. Emergency and legally protected call behavior must follow Android/platform requirements and the existing Phone Core compliance rules.

The community backend must never be able to remotely bypass these local safeguards.

## 17. Corrections and Collective Immunity

User corrections become an explicit negative signal in the community model.

Supported local correction actions include:

- allow only this number;
- allow this organization;
- mark call legitimate;
- mark call as telemarketing;
- mark call as scam/fraud;
- correct a campaign classification.

When sufficient independent legitimate corrections converge, campaign confidence decreases and subsequent bundles reflect the correction. This behavior is called `Collective Immunity`.

## 18. Appeals and review

A legitimate organization or user may request review of a classification.

A credible appeal moves the relevant entity to `UNDER_REVIEW`, unless independent severe fraud indicators require continued protection.

Enterprise review may use public identity evidence such as SIREN/SIRET, verified domain control or other lawful proof of organizational control.

An appeal never directly deletes community history; it triggers reassessment.

## 19. Moderation ledger

Important campaign and moderation transitions are written to an internal append-only audit trail.

The ledger records:

- campaign creation;
- state transitions;
- scoring-engine version;
- bundle publication;
- revocation;
- dispute/review events;
- final moderation outcome.

The ledger must avoid unnecessary PII.

## 20. Regional intelligence

Sentinel supports both global and regional threat intelligence.

Regional granularity is coarse, such as country, department or territory. Precise GPS coordinates are prohibited from community scoring.

This supports DROM-specific patterns without exposing a user's exact location.

Example user-facing output may state that multiple campaigns are active in Guadeloupe, but not identify the user's neighborhood, street or device location.

## 21. Product experience

A dedicated `Défense collective` area should expose:

- overall community protection status;
- active campaign count;
- campaign trends;
- recently blocked/silenced calls;
- correction center;
- participation tier;
- threat-bundle freshness.

Campaign cards should show concise explanations such as:

- campaign type;
- state;
- coarse territory;
- confidence;
- trend;
- explanation signals;
- current local protection action.

The interface must not imply certainty where only probability exists.

## 22. Notifications

Community notifications are intentionally sparse.

Permitted high-value notifications include:

- severe new campaign rapidly increasing in the user's coarse region;
- major correction/revocation affecting prior decisions;
- community protection becoming stale or unavailable.

Routine campaign changes remain inside the application.

## 23. Adaptive protection

Sentinel may propose temporary increases in protection sensitivity when multiple severe campaigns are active.

Automatic silent escalation to a more aggressive user mode is not allowed unless the user has explicitly enabled that behavior.

Time-of-day campaign information may influence scoring but must not alone justify blocking.

## 24. Backend architecture

The community backend is divided into independent components:

### 24.1 Ingestion API

Validates and accepts pseudonymized events after schema, quota and anti-abuse checks.

### 24.2 Event store

Stores raw community events temporarily with short retention.

### 24.3 Campaign Analysis Engine

Aggregates events into campaign candidates and computes campaign lifecycle state and confidence.

### 24.4 Reputation store

Stores derived contributor, number-cluster, organization and campaign reputation data.

### 24.5 Campaign store

Stores current and historical campaign aggregates.

### 24.6 Bundle Builder

Builds global/regional signed ThreatBundles and EmergencyRevoke artifacts.

### 24.7 Moderation store

Tracks appeals, reviews and moderation outcomes.

The raw event store is never directly queryable by client devices.

## 25. Initial API surface

Logical API surface:

- `POST /community/events`
- `POST /community/corrections`
- `GET /community/bundles/latest`
- `GET /community/campaigns/{id}`

Exact route names may follow existing backend conventions, but semantics must remain separated between event submission, correction submission, bundle distribution and aggregate campaign readout.

## 26. Reliability and failure behavior

Failure rules:

- community API unavailable: local Phone Core continues using last valid data;
- bundle download fails: retain last valid unexpired bundle;
- bundle validation fails: reject new bundle and preserve old valid bundle;
- scoring backend degraded: stop promoting campaigns rather than emit low-confidence block rules;
- contribution upload fails: retain bounded local queue with backoff; never block calls while retrying;
- corrupted local community cache: discard corrupted cache, fall back to personal/local rules and fail-open where required by Phone Core policy.

No community outage may prevent the user from making or receiving otherwise allowed calls.

## 27. Shadow decisions and staged rollout

New campaign scoring versions and blocking rules must support `Shadow Decision` mode.

In shadow mode the system records what a new rule would have done without affecting actual call behavior.

New high-impact community rules should support staged rollout before global enforcement.

Promotion criteria should include:

- false-positive rate;
- correction rate;
- detection lead time;
- stability across territories;
- unexpected impact on trusted/known-call categories.

## 28. Campaign Replay Lab

Before promoting a new scoring version, the backend should replay historical aggregate/event data over controlled windows such as 7 and 30 days.

Metrics include:

- campaigns detected;
- missed campaigns;
- mean time to `EMERGING`;
- mean time to `CONFIRMED`;
- false-positive rate;
- dispute rate;
- correction rate;
- regional stability.

Replay results are release evidence, not user-facing metrics.

## 29. Observability

Operational metrics include:

- event ingestion rate;
- validation reject rate;
- rate-limit events;
- campaign creation/promotion rates;
- bundle generation latency;
- bundle validation failures;
- correction and dispute rates;
- emergency revocations;
- backend availability;
- client bundle freshness distribution.

Telemetry must exclude raw telephone numbers and other prohibited PII.

## 30. Testing strategy

### 30.1 Unit tests

Cover:

- PrivacyGateway field stripping;
- pseudonymization interface contracts;
- scoring dimensions;
- campaign state transitions;
- contributor weighting;
- correction penalties;
- spoofing attribution safety;
- bundle signature/expiration/rollback checks;
- local mode threshold behavior;
- fail-open and fail-safe boundaries.

### 30.2 Property/fuzz tests

Cover malformed events, replayed events, duplicate events, pathological scoring inputs, malformed bundles, invalid signatures and attacker-controlled fields.

### 30.3 Backend integration tests

Exercise ingestion → aggregation → campaign promotion → bundle generation → client verification.

### 30.4 Emulator tests

Exercise local bundle loading and CallScreening decision behavior with deterministic synthetic campaigns, including offline mode, corrupted cache and expiration.

### 30.5 Physical S24+ qualification

Verify that community intelligence does not violate the Phone Core latency budget, does not break role behavior, survives reboot/offline state and does not generate false-green physical proof.

Community validation supplements but does not replace the canonical physical Phone Core evidence.

## 31. Security boundaries

The design explicitly forbids:

- raw community access to other users' event streams;
- plaintext global phone-number indexing exposed to clients;
- remote execution of arbitrary blocking logic;
- direct server bypass of personal allowlists and safety exemptions;
- offensive actions against suspected callers;
- automatic publication of contact-book contents;
- precise location sharing for community detection;
- representing probabilistic identity as verified fact.

## 32. Relationship to existing Sentinel systems

Collective Defense integrates with but does not replace:

- Phone Core and CallScreeningService;
- local reputation and allow/block policy;
- future Sentinel Contact Hub / Trust Graph;
- existing public-source intelligence;
- product capability truth and production-readiness gates.

The Contact Hub and Collective Defense remain separate subsystems with explicit interfaces. Contact details never become community telemetry merely because a contact participates in local trust scoring.

## 33. Delivery sequence

Implementation should be decomposed into bounded phases:

1. community domain model and local interfaces;
2. PrivacyGateway and contribution tiers;
3. ingestion API and bounded event store;
4. campaign aggregation and state machine;
5. signed ThreatBundle pipeline;
6. local Phone Core bundle integration;
7. correction/Collective Immunity flow;
8. contributor anti-abuse model;
9. shadow/staged rollout and replay lab;
10. regional/community UI;
11. emulator qualification;
12. physical S24+ qualification and production readiness.

No phase may claim full customer availability until its configuration, deployment, runtime verification and required physical evidence are complete.

## 34. Non-goals for first implementation

To control scope, the initial implementation does not require:

- peer-to-peer device networking;
- precise geospatial threat maps;
- machine-learning-only blocking decisions;
- automatic call-answering challenge infrastructure;
- cross-platform iOS parity;
- monetized contributor reputation;
- blockchain or public reputation ledger;
- user-visible raw contributor scores;
- arbitrary third-party access to community telemetry.

## 35. Acceptance criteria

The first production-capable Collective Defense release is acceptable only when:

- contribution is explicit opt-in;
- PrivacyGateway tests prove prohibited fields cannot leave the device through the community pipeline;
- community backend operates without storing raw telephone numbers in event records;
- campaign promotion resists duplicate and single-contributor flooding;
- signed bundles validate locally and reject tampering/rollback;
- Phone Core uses community data without network access in the screening callback;
- offline behavior remains functional;
- correction and dispute flows demonstrably reduce bad campaign confidence;
- emulator tests cover failure and corruption scenarios;
- S24+ physical tests show no regression in Phone Core call-screening behavior or latency;
- capability status remains truthful until deployment and physical evidence exist.
