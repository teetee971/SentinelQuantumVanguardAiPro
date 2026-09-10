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
