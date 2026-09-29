# Digital Exposure Monitoring — target architecture

## Status

`PLANNED`. Sentinel currently contains a bounded local email analyser. It does not yet query a breach provider, continuously monitor email addresses or domains, or process stealer-log intelligence.

## Useful product capabilities

### Free personal tier

- password exposure check using a k-anonymity range protocol;
- one-off email exposure check only after explicit user action;
- breach timeline, exposed data categories and practical remediation;
- no storage of unmatched hash-range results;
- local reminder to enable MFA and rotate reused credentials.

### Paid Professional add-on

- continuous monitoring of explicitly enrolled addresses;
- verified-domain inventory and DNS ownership proof;
- new-breach and paste alerts, deduplicated by event;
- exposure dashboard by domain, address, provider and severity;
- ticket/export integrations with minimum necessary data;
- audit log, retention policy, access control and deletion workflow.

### Enterprise add-on

- monitored customer domains only when the subscription and authorisation permit it;
- stealer-log alerts for verified domains;
- account-takeover response workflows;
- SSO, RBAC, evidence export and API quotas.

## Security and privacy invariants

1. Sentinel must use an authorised provider API or a separately licensed dataset; it must not scrape or clone another service.
2. Provider API keys belong in a server-side secret store, never in the Android APK or public JavaScript.
3. Direct email searches disclose the address to the provider; the UI must say so before consent. Prefer k-anonymity where the provider supports it.
4. Organisation-wide searches require proof of domain control and an auditable administrator.
5. Never display or return passwords. Store event metadata, not leaked secrets.
6. Results unrelated to the requested hash/address must be discarded immediately.
7. False negatives remain possible; “not found” never means “safe”.
8. Production activation requires a DPA/legal review, retention schedule, incident process and provider quota handling.

## Delivery gates

- provider and commercial terms selected;
- threat model and DPIA completed;
- domain-verification flow implemented;
- mocked contract tests and provider sandbox tests green;
- notification, deletion and rate-limit failure paths tested;
- user-facing claims aligned with observed behaviour.


## Identity exposure verification foundation

Sentinel's Digital Exposure pipeline may accept the following explicitly user-initiated subjects:

- an email address;
- a first name plus family name;
- a first name plus family name combined with an email address;
- for professional use, a first name plus family name combined with a verified organisation domain.

### Evidence model

Every provider result must be normalised into an evidence record with source provenance, observation time, breach/event identifier when available, exposed-data categories, and a match classification. The supported match classifications are:

- `EXACT`: the authorised source explicitly associates the queried unique identifier with the event;
- `CORROBORATED`: multiple independent attributes support the same identity, without reaching an exact unique-identifier match;
- `AMBIGUOUS`: name-only or otherwise non-unique evidence that may concern a homonym;
- `NOT_FOUND`: the queried authorised sources returned no matching event;
- `UNVERIFIED`: evidence is incomplete, stale, malformed, or cannot be safely attributed.

`NOT_FOUND` must never be rendered as "safe", "not breached", or equivalent.

### Name-search safety

A first-name/family-name match alone is never proof that a breach belongs to that person. Name-only results must remain `AMBIGUOUS` until corroborated by an authorised additional attribute. Sentinel must not automatically enrich a name search into addresses, relatives, phone numbers, private accounts, precise location, or other unrelated personal data.

The product must not expose leaked passwords, authentication tokens, session cookies, recovery secrets, payment credentials, private message contents, or raw stealer-log secrets. Where a licensed provider reports such exposure, Sentinel stores and displays only the minimum category metadata required for remediation.

### Pipeline boundary

The local Email Security analyser and Digital Exposure are distinct trust domains:

1. **Email Security** analyses a message supplied by the user: headers, authentication results, domains, links and bounded indicators.
2. **Digital Exposure** asks an authorised/licensed source whether an explicitly submitted identity attribute appears in a documented exposure event.
3. Correlation between the two requires explicit user action and must preserve each source's provenance and confidence independently.

No provider call is permitted from public JavaScript or directly from the Android APK. A future provider adapter must execute server-side, use secrets from a managed secret store, apply rate limits, minimise query data, and produce a redacted audit event.

### Required CI gates before provider integration

- deterministic tests for email normalisation and invalid-address rejection;
- Unicode-aware but conservative first/family-name normalisation tests;
- tests proving that name-only evidence cannot become `EXACT`;
- tests proving that `NOT_FOUND` cannot become a safety claim;
- tests rejecting secret-bearing provider payload fields from persisted/output models;
- contract tests for provider timeout, 429, malformed response and partial outage;
- tests for consent, deletion and retention boundaries;
- dependency/security scanning and existing repository governance gates.

This foundation does not activate external breach lookup. Provider selection, licensing, DPIA/legal review and production credentials remain separate release gates.
