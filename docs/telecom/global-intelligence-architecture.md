# Sentinel Global Telecom Intelligence — phase foundation

## Current contract

The Android module lives under `com.sentinel.quantum.telecom` and has four
separate boundaries:

| Boundary | Responsibility | Current qualification |
|---|---|---|
| `GlobalTelecomSourceRegistry` | Official publisher metadata and jurisdiction | 10 reference sources, HTTPS metadata, no enforcement authority |
| `OfficialSourceProbeAdapter` | Bounded read-only HTTPS reachability/provenance probe | Implemented; records reachability only, not a numbering dataset |
| `TelecomSourceSyncCoordinator` | Validation, TTL, bounded cache and failure containment | Implemented and unit-tested with transport doubles |
| `InternationalNumberResolver` | E.164/`00` normalization and shared-code ambiguity | Implemented and unit-tested; national input requires explicit context |

`OkHttpOfficialSourceTransport` is the production transport. It follows no
redirects, has a bounded response size and a ten-second call timeout. The
probe must run from a foreground action or worker. It is never constructed or
called by `SentinelCallScreeningService`.

## What the first connector proves

The explicit ARCEP action in the native Global Telecom screen can perform a
real HTTPS reachability probe. A successful response is stored as
`REACHABLE_METADATA_ONLY` with retrieval time, content type, byte count and a
SHA-256. HTML or arbitrary JSON is deliberately not interpreted as a plan of
numbering. A source-specific parser, license review and contract fixture are
required before `VALIDATED_DATASET` can be emitted.

The repository registry remains `REFERENCE_ONLY`, with `0` active connectors
until an actual source-specific data adapter has been observed and qualified.
The Kotlin registry and the JSON registry both keep `enforcementEligibility =
NONE`.

## Phone Core boundary

The resolver can enrich a number with calling-code context, but this evidence
is projected into `IntelligenceEvidence.TelecomNumberEnrichment` with
`ANNOTATION_ONLY` authority. It cannot create a block, silence, identity or
fraud verdict. `CallScreeningEnforcementPolicy` still accepts only explicit
user block rules and maps every uncertain/remote action to `ALLOW`.

National numbers are not silently converted to `+33`. The caller must supply a
calling-code context. Shared prefixes (`+1`, `+7`, `+262`, and similar) remain
ambiguous, while a more specific catalog prefix may resolve to a territory.

## Qualification still open

- No source-specific official numbering dataset parser has been qualified.
- No license has been marked verified.
- No live synchronization is claimed by the repository until the explicit
  probe action actually returns evidence.
- No regulator output authorizes Telecom enforcement.
- No physical Galaxy S24+ call, SMS or MMS qualification is available in the
  current environment.
