# Phone Intelligence — trust core

## Status

This document describes repository-side security primitives for a future moderated phone/SMS community feed. It does **not** state that a production backend, licensed carrier feed, signing service, or production synchronization endpoint is deployed.

The current public web workspace remains local-only. Public wording must continue to say that the community daily list is not connected until end-to-end deployment evidence exists.

## 1. Moderation and appeals

`security/phone-intelligence/moderation-core.js` provides a bounded reference core for:

- report intake using normalized E.164 numbers;
- explicit moderation states (`pending`, `accepted`, `rejected`, `escalated`);
- appeal ownership and reasoned appeal decisions;
- a configurable appeal response SLA (seven days by default), explicit due times,
  overdue visibility and recorded met/breached outcomes;
- explicit human versus automated moderation provenance: automation may escalate
  for human review but cannot accept/reject a report or decide an appeal;
- terminal moderation and appeal decisions that cannot be silently rewritten;
- a bounded, SHA-256-linked audit sequence recording who, what, when and a bounded
  reason code for each state-changing operation without copying phone numbers,
  evidence or free-text report notes into the audit event;
- sliding-window rate limiting;
- an explicit abuse-score gate;
- duplicate-report suppression;
- bounded in-memory report, appeal, rate-limit and abuse state.

A report never becomes publishable merely because it was submitted. Only an explicit `accepted` moderation decision can appear in `acceptedReports()`.

The in-memory stores and hash-linked audit sequence are suitable for deterministic
tests and contract definition only. Hash linking makes in-process tampering
detectable; it is not immutable storage. Production deployment requires durable,
append-only or independently anchored audit storage, shared transaction/concurrency
semantics, access control, retention enforcement and operational backups.
Multi-instance deployment must not rely on process-local counters for rate limiting,
anti-abuse state or audit ordering. State-changing operations fail closed if the
bounded reference audit store cannot accept their event.

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

Android now includes repository-side synchronization primitives in `CallRuleSyncClient.kt`:

- synchronization remains separate from `CallScreeningService`, so incoming-call screening does not wait on network access;
- the transport accepts only an explicitly configured HTTPS endpoint whose host is present in an explicit allowlist;
- redirects and origin changes are rejected;
- connect, read and overall call timeouts are bounded;
- response size and accepted content types are bounded;
- the envelope must decode as strict UTF-8;
- downloaded content is never trusted directly and is passed to `SignedCallRulePackageVerifier` through the existing signed-rule installation path;
- verifier rejection, including rollback/replay rejection through the persisted highest sequence, prevents installation;
- network failure does not replace the last valid installed package.

These properties are implemented and covered by repository tests, and PR #335 was CI-validated before merge. They do **not** mean a production synchronization service is operational.

Production synchronization still requires all of the following external or deployment evidence:

- a production endpoint and explicit host configuration;
- TLS/DNS ownership and monitoring;
- production signing-key custody, issuer/key distribution, rotation and revocation;
- a deployed publication service backed by authorized sources and durable moderation state;
- an explicit maintenance/background scheduling policy validated on real devices;
- end-to-end evidence that Android fetched, verified and installed an authentic production package without weakening user-owned rules.

Certificate pinning cannot be safely finalized before the production endpoint and certificate/key rotation strategy exist.

## 6. Internationalization and legal identity

The phone-intelligence web workspace now has a bounded French/English locale layer with deterministic French fallback, localized visible UI/runtime messages and repository tests. This establishes the reusable phone-intelligence i18n foundation, but it does **not** mean repository-wide internationalization is complete; other web surfaces, Android resources, legal copy and operational messages still require incremental locale extraction and audit.

The repository also now contains a fail-closed legal publisher manifest readiness validator in `scripts/check-legal-readiness.js`. It requires supplied values for publisher legal name, publication director, public contact email/address/country, hosting provider plus HTTPS information URL, reviewer identity and review timestamp; it rejects missing values, obvious placeholders and malformed fields.

The validator deliberately stores no real publisher identity or personal/legal data in source control. It validates structure and obvious placeholder/format errors only; it does not independently authenticate the legal accuracy, ownership or authority of supplied values. Final release still requires independently verified real-world publisher/contact/publication-director information supplied outside the repository and passed through this gate. The existence or success of the validator is not proof that those real values are genuine.

## 7. Evidence vocabulary

Use these states when reporting progress:

- **implemented**: source code exists;
- **tested**: deterministic repository tests pass on the exact commit;
- **CI-validated**: applicable GitHub checks pass on that exact commit;
- **deployed**: a production/preview deployment bound to the exact commit is observed;
- **operationally verified**: the deployed chain has real authorized sources, managed signing keys, durable state and end-to-end evidence.

Do not collapse these states into a generic "ready" or "production" claim.
