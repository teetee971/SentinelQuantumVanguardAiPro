# Product lifecycle audit follow-up — 2026-10-03

Baseline reviewed: `f5fc8ba8f6e872f7fd2b9ef2acc48e8792175719`.

This first correction batch addresses four lifecycle paths. It does not close the complete product audit.

- **F01, Android cached call rules:** exact-number expiry metadata and signed-prefix expiry remain in the memory snapshot. Every screening read filters expired rules without reading preferences. Manual rules remain effective. Unit regressions cover the exact deadline and snapshot immutability.
- **F03, Mesh durable mutations:** server mutations and SPIFFE stream ingestion share a serialized candidate-state boundary. Successful persistence precedes publication; failed persistence returns HTTP 503 and leaves the prior state active. Subsequent mutations recover. Direct synchronous library mutations must not be mixed with this runtime boundary. A failed revocation must be retried: the prior committed authorization state remains active.
- **F04, Mesh/VPN concurrent saves:** each store instance captures submitted snapshots, serializes writes, assigns distinct sequences and uses unique temporary files with cleanup. This is a single-writer contract; it does not coordinate multiple processes or multiple store instances targeting one file. Restart anti-rollback still needs an external monotonic authority.
- **F08, session capacity:** creation reclaims expired negotiation and relay registry entries. Relay grant broker cleanup and authorization revalidation remain open.

SPIFFE `PermissionDenied` remains a fail-closed exception: live trust is removed even if redaction persistence fails. No privileged executor, VPN host action, or new deployment integration is introduced.

## Audit correction

F05 was initially classified as a VPN revocation defect. The existing test `revocation invalidates a lease and allows reprovisioning` explicitly defines the intended behavior. Lease revocation permits reprovisioning; it is not durable identity revocation. F05 is therefore a product-contract limitation, not a demonstrated implementation defect. The corrected audit counts 13 defects (5 P1, 8 P2) and this additional contract limitation.

## Outstanding product debt

- Revalidate Mesh target/policy at negotiation finalization and grant claims, and propagate revocation to existing relay sessions (F02).
- Enforce VPN peer expiry in the actual data plane and make lease exports restorable after address reuse (F06–F07).
- Repair SMS provider failures after callback persistence (F09), preserve watch reputation expiry in the UI (F10), and fail closed on public-report rate-limiter outages (F11).
- Resolve national-number region assumptions, allowlist product claims, and reputation confidence/age semantics (F12–F14).

## Validation

Local Mesh/VPN suite: 258 passing tests, including concurrency, persistence failure, pending-state visibility, session reclamation and SPIFFE redaction regressions.

Local execution suite: 54 passing tests with PostgreSQL 17 in an isolated disposable container. Syntax, module continuity/interface/cycle/usage/inventory, security governance, security fuzz and isolation gates passed.

Android SDK is unavailable in the local workspace. Android unit tests, `lintDebug`, APK build and instrumentation must be checked through the PR workflows on the exact current HEAD before merging. Local checks do not prove production deployment controls or physical-device behavior.
