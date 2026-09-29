# Social Intelligence & Foreign Interference Defense — evidence model

## Status

`PLANNED / FOUNDATION`. This document defines the evidence boundary before any automated ingestion is enabled.

## Canonical chain

`Source -> Observation -> Evidence -> Relationship -> DISARM mapping -> Hypothesis -> Human review -> Assessment`

Each transition preserves provenance. No earlier stage is allowed to silently become an attribution.

## Canonical entities

### Source
Fields: `sourceId`, `name`, `sourceType`, `reference`, `licenseId`, `licenseReviewStatus`, `retrievedAt`, `provenanceHash`.

Only explicitly reviewed source/license combinations are ingestible. Unknown or unreviewed licensing fails closed.

### Observation
Fields: `observationId`, `sourceId`, `observedAt`, `rawDataHash`, `status`.

Allowed statuses: `OBSERVED`, `CORRELATED`, `REJECTED`. Attribution is deliberately not an Observation status.

### Evidence
Fields: `evidenceId`, `observationId`, `evidenceType`, `artifactRef`, `extractedAt`, `integrityHash`.

Evidence references bounded derived artifacts. Raw private Phone Core material is not an accepted evidence source.

### ContentArtifact
Fields: `artifactId`, `observationId`, `artifactType`, `normalizedValue`, `extractedAt`, `contentHash`.

Initial artifact types: `TEXT_HASH`, `URL`, `IMAGE_HASH`, `DOMAIN`. The model favours hashes and bounded normalized values over unnecessary raw content retention.

### Relationship
Fields: `relationshipId`, `sourceArtifactId`, `targetArtifactId`, `relationshipType`, `firstObservedAt`, `evidenceRefs`, `confidence`.

Relationship types describe observable propagation such as `REUSED`, `SHARED`, `AMPLIFIED`, or `TEMPORALLY_CORRELATED`. They do not encode intent, nationality or actor identity.

### DisarmMapping
Fields: `mappingId`, `subjectRef`, `techniqueId`, `taxonomyVersion`, `sourceReference`, `observedAt`, `evidenceRefs`, `confidence`, `analystStatus`, `licenseAttribution`.

There are intentionally **no actor, country, sponsor, origin-state or intent fields** in this entity. DISARM describes techniques; it is not an attribution mechanism or IoC feed.

### Hypothesis
Fields: `hypothesisId`, `statement`, `evidenceRefs`, `createdBy`, `createdAt`, `status`.

A hypothesis is explicitly labelled and may be rejected. It cannot mutate observations or mappings.

### AnalystAssessment
Fields: `assessmentId`, `hypothesisRefs`, `evidenceRefs`, `analystId`, `reviewStatus`, `assessmentDate`, `rationale`.

Review states: `HYPOTHESIS`, `REVIEWED`, `REJECTED`, `CONFIRMED_BY_ANALYST`. `CONFIRMED_BY_ANALYST` means a reviewed analytical conclusion, not mathematical certainty or state attribution proved by Sentinel.

## Structural anti-attribution invariants

Anti-attribution is enforced by schema and data flow, not keyword blacklists. Keyword gates are unsuitable because legitimate source material may contain country or actor names and because attribution can be expressed without those words.

- Observation, Evidence, ContentArtifact, Relationship and DisarmMapping expose no attribution field.
- DISARM mappings cannot carry actor/country/sponsor/origin-state/intent properties.
- Automated components may produce observations, artifacts, relationships and candidate technique mappings only.
- Attribution claims, if an authorised analyst needs to discuss them, belong only to the human assessment layer and must cite evidence and preserve uncertainty.
- A taxonomy match can never raise an assessment state automatically.
- No autonomous offensive or disruptive action can be triggered by this model.

## Phone Core isolation invariant

Social Intelligence must not import, query or depend on Phone Core contacts, SMS/MMS stores, call history, call-screening state, Caller ID data, phone-number search history or certification evidence.

Any future bridge between modules requires a separately reviewed explicit user export/import contract. There is no implicit cross-module join.

## CI gates

A dedicated `social-intelligence-ci` gate must verify:

1. schema tests reject attribution fields in automated-stage models;
2. DISARM mapping requires taxonomy version, source provenance, evidence references, confidence and licence attribution;
3. unreviewed/unknown licences fail closed;
4. dependency/import checks reject Phone Core packages and sensitive Android providers from the Social Intelligence module;
5. automated state transitions cannot create `CONFIRMED_BY_ANALYST`;
6. evidence and relationship confidence values are bounded;
7. provenance/integrity hashes are mandatory where defined;
8. fixtures contain no real private Phone Core data;
9. existing repository security, isolation and CodeQL gates continue to apply.

## Ingestion release gates

No live source ingestion is enabled by this foundation. Production ingestion additionally requires source terms/licence review, minimisation/retention rules, rate-limit and outage behaviour, schema/contract tests, provenance preservation and a reviewed user-facing description of limitations.
