# Phone Intelligence — trust core

## Status

This document describes repository-side security primitives for a future moderated phone/SMS community feed. It does **not** state that a production backend, licensed carrier feed, signing service, or Android synchronization endpoint is deployed.

The current public web workspace remains local-only. Public wording must continue to say that the community daily list is not connected until end-to-end deployment evidence exists.

## 1. Moderation and appeals

`security/phone-intelligence/moderation-core.js` provides a bounded reference core for:

- report intake using normalized E.164 numbers;
- explicit moderation states (`pending`, `accepted`, `rejected`, `escalated`);
- appeal ownership and reasoned appeal decisions;
- sliding-window rate limiting;
- an explicit abuse-score gate;
- duplicate-report suppression;
- bounded in-memory report, appeal, rate-limit and abuse state.

A report never becomes publishable merely because it was submitted. Only an explicit `accepted` moderation decision can appear in `acceptedReports()`.

The in-memory stores are suitable for deterministic tests and contract definition only. Production deployment requires durable, shared storage with transaction/concurrency semantics, access control, retention enforcement, auditability and operational backups. Multi-instance deployment must not rely on process-local counters for rate limiting or anti-abuse state.

## 2. Authorized source registry

`security/phone-intelligence/source-registry.js` fails closed unless a source has all of the following:

- a stable source identifier;
- an `authorized` status;
- authorization type and reference;
- HTTPS evidence URL;
- reviewer identity and review timestamp;
- an explicit validity window;
- an explicit authorized scope.

This registry is a policy enforcement primitive. A syntactically valid record is **not** proof that a real operator, carrier, regulator or data provider granted a license. Real contractual/licensing evidence must be reviewed and retained outside or alongside the runtime configuration before a production source is enabled.

ARCEP numbering resources may be used only for the scope actually permitted by their terms and data semantics. Number allocation/reference data must not be presented as evidence that a number is fraudulent.

## 3. Signed daily rule package

`security/phone-intelligence/call-rule-publication.js` generates and verifies the same bounded package format already consumed by Android `SignedCallRulePackageVerifier.kt`.

The format deliberately remains P-256 ECDSA with SHA-256 because that is the existing Android verifier contract. Changing the algorithm independently on the publisher would break the client trust chain.

Signed fields include:

- domain `sentinel-call-rules-v1`;
- package id `fr-vigilance`;
- monotonically increasing `sequence`;
- `issued_at_ms` and `expires_at_ms`;
- issuer id;
- key id;
- sorted, canonical `silence_prefix` entries.

The verifier rejects unknown keys, invalid signatures, issuer/key mismatch, rollback/replay (`sequence <= highestAcceptedSequence`), future-issued packages outside the bounded skew, expiry, excessive lifetime and malformed/over-broad prefixes.

Remote reputation rules remain advisory: the Android rule engine may silence matched calls, but remote reputation data does not automatically block calls. User-owned exact/prefix rules retain the blocking authority.

## 4. Key custody and publication schedule

No signing private key belongs in this repository, APK, static site or GitHub artifact.

Before enabling a daily publication job, production operations must provide and verify:

1. a protected signing-key custody mechanism;
2. an issuer/key registry distributed to Android through a controlled release path;
3. rotation and revocation procedures;
4. a durable source/moderation database;
5. an atomic sequence allocator preventing duplicate or decreasing sequences;
6. publication storage and TLS/DNS ownership;
7. monitoring that detects failed, stale or unexpectedly replaced publications.

A scheduled workflow must not be enabled with placeholder keys or fabricated source authorization.

## 5. Android synchronization boundary

Android already has the important offline trust primitives:

- `SignedCallRulePackageVerifier`;
- `CallBlocklistStore.installSignedSilenceRules()`;
- persistent highest accepted sequence;
- expiry handling;
- local exact-number fingerprints;
- no cleartext HTTP in the Android manifest.

The production synchronization client remains separate work. It must:

- fetch only from an explicitly configured HTTPS origin;
- apply connect/read timeouts and a strict response-size bound;
- avoid any network operation in the incoming-call screening path;
- pass the received envelope unchanged to the signed-package verifier;
- install only a verifier-accepted package;
- preserve the last valid package when the network is unavailable;
- reject rollback/replay through the persisted highest sequence;
- expose failures without silently replacing user-owned rules.

Certificate pinning cannot be safely finalized before the production endpoint and certificate/key rotation strategy exist.

## 6. Internationalization and legal identity

Repository-wide internationalization is not complete. The web phone workspace is currently French-first and Android has a default resource catalogue, but a complete locale extraction/audit remains required.

Likewise, the repository must not invent the legal publisher identity, postal/contact information or publication director. Those fields require verified public data supplied for the actual publisher. A release-readiness check can enforce completeness once the real values are available.

## 7. Evidence vocabulary

Use these states when reporting progress:

- **implemented**: source code exists;
- **tested**: deterministic repository tests pass on the exact commit;
- **CI-validated**: applicable GitHub checks pass on that exact commit;
- **deployed**: a production/preview deployment bound to the exact commit is observed;
- **operationally verified**: the deployed chain has real authorized sources, managed signing keys, durable state and end-to-end evidence.

Do not collapse these states into a generic "ready" or "production" claim.
