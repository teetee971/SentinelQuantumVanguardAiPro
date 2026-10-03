# Product remediation — 2026-10-03

This branch consolidates the OSINT fixes from #1510 and lifecycle fixes from #1513, then addresses the remaining reproducible repository defects. Repository checks do not certify a deployed or physically validated product.

## Corrected behavior

- Undated RSS entries remain unknown; partial refresh preserves the complete cache and source coverage. An instrumentation regression checks the unknown-date label.
- Mesh session creation, finalization, keepalive and relay packets recheck current policy/revocation. Expired negotiation grants and leases are pruned; state writes remain serialized and atomic.
- VPN allocation reuses expired addresses without accumulating historical owners. Android propagates the lease deadline and attempts tunnel shutdown at its wall-clock or monotonic deadline; an unconfirmed manual shutdown preserves the deadline and reservation.
- SMS provider writes have a persisted acknowledgment, idempotent recovery, bounded retry scheduling and startup recovery. Replayed/repaired callbacks cannot manufacture physical-validation signals.
- Collective-defense watch entries expire their displayed risk and distinguish freshness from fingerprint continuity. Historical entries without freshness evidence display UNKNOWN.
- Public community reports fail closed when the anti-abuse limiter is unavailable. Quantity alone cannot produce HIGH_CONFIDENCE. New contributions preserve the existing aggregate expiry. Real Redis integration tests exercise both report and moderation Lua implementations.
- Public documentation identifies the French national-number normalization scope and the web-only local allowlist, rather than promising Android synchronization.

## Product contract and evidence

`config/product-capabilities.json` schema 2 is the source for generated documentation, a public status page/JSON, and Android maturity labels. Generation and `--check` reject an invalid contract. Operational promotions require Ed25519-signed observations authorized by `config/product-evidence-trust.json`, bound to the capability, stage, environment, artifact hash, observation revision and current hashes of the declared source scope. Proofs expire, revoked issuers fail closed, capability dependencies are checked, and physical observations enumerate all required criteria. Phone Core requires the exact 14 canonical criteria.

The production trust configuration intentionally contains no issuer: no production keys, signatures or deployments were invented. Source references describe implementation and do not establish deployment. Availability displays expire; the status catalog is not an execution-authorization mechanism. Trust-file changes require repository review and branch protection; compromise of that review boundary cannot be prevented by a repository file alone.

## Operational blockers still requiring evidence or separate integration

- Real device execution of all Phone Core scenarios; signed APK/AAB installation and publication evidence.
- Provisioned Sentinel VPN gateway, independently deployed PostgreSQL sequence authority, and reviewed server-side WireGuard peer retirement. The PostgreSQL adapter is implemented and database-tested; provisioning and runtime wiring remain absent. Client expiry does not prove server expiry enforcement or behavior after process death.
- Mesh direct WireGuard peer-policy deployment/revocation; relay authorization alone does not revoke a previously installed direct peer.
- LiveKit/token issuer/VoIP-PSTN infrastructure and real transformed-call latency, echo, Bluetooth and reconnection validation.
- Additional GeoIntel sources, SaaS identity/authorization, production moderation/publishing keys/storage/schedulers, full translated resources and device journeys.
- Deployment-side producer inventory, key custody/rotation/revocation and branch-protection hardening remain outside repository-test evidence (issues #215 and #473).

No claim of complete production readiness or absence of unknown defects follows from this remediation. Historical deployment notes remain dated observations; they do not promote the current artifact.
