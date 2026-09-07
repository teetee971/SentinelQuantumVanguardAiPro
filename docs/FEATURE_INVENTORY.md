# Sentinel — Feature Evidence Inventory

This inventory is the anti-drift control between source code, tests, CI evidence, deployment evidence and public claims.

A capability is not considered operational merely because a source file exists. Evidence is recorded separately so that a CI outage cannot be mistaken for a code failure, and a code path cannot be mistaken for a validated production capability.

| Capability | Source state | Validation state | Public claim allowed |
|---|---|---|---|
| PWA/web interface | IMPLEMENTED | Frontend, Lighthouse, integrity and pre-production checks passed on the applicable PR #342 head before merge | YES, with current-scope wording |
| Cloudflare Pages deployment path | IMPLEMENTED | Successful deployment check observed on an earlier security change; each release still requires fresh evidence | YES, without implying security certification |
| Local Android security checks | IMPLEMENTED | Manifest validation, unit tests, lint and debug APK build passed on the applicable PR #341 and #342 heads before merge | YES, as tested source capability; not as a signed public release |
| Public OSINT feed retrieval | IMPLEMENTED | Parser/source controls exist; live network execution is not treated as continuously verified | YES, read-only/public-feed wording |
| AI governance controls | IMPLEMENTED | AI-governance and security-governance checks passed on the applicable PR #342 head before merge | YES, as governance/control code |
| Decision-plane validation | IMPLEMENTED | Unit, security-governance, fuzzing and CodeQL checks passed on the applicable PR #342 head before merge | YES, as validation logic, not autonomous enforcement |
| Sentinel / A KI PRI SA YÉ isolation | IMPLEMENTED | Dedicated scanner and negative fixtures exist; fixtures are intentionally allowed to contain forbidden references | YES, strict separation requirement |
| Red Team simulation | IMPLEMENTED | Synthetic scenarios and simulation engine exist; no real attack execution is claimed | YES, simulation/training wording |
| Public threat intelligence | READ-ONLY | Public-feed implementation exists; not a live SOC | YES, read-only/public-source wording |
| Bounded local logging | IMPLEMENTED | Source limits, failure isolation and best-effort credential/signature redaction passed Android CI on PR #341 | YES, as local bounded and redacted logging; callers must still avoid secrets |
| Deterministic explainability | IMPLEMENTED | Local deterministic implementation exists | YES, as explainability support; not model certainty |
| Local phone/SMS intelligence workspace | IMPLEMENTED | Local deterministic analyzer, personal lists and tests exist | YES, explicitly local-only wording |
| Android user-owned call blocking | IMPLEMENTED | Exact/prefix rules, versioned device-bound HMAC fingerprints and legacy v1 matching passed Android CI on PR #341 | YES, only for local user-owned rules |
| Signed Android reputation rule verification | IMPLEMENTED | Bounded P-256 verifier/store and matching repository publisher contract are tested; no live signed feed is deployed | YES, as verification logic; not as a live feed |
| Moderated community phone reports | CORE IMPLEMENTED | Reference moderation/appeal/rate-limit/anti-abuse core, seven-day SLA, human/automation separation and hash-linked audit contract passed PR #342 CI; durable deployed backend is not demonstrated | NO live-service claim |
| Licensed operator/source ingestion | ENFORCEMENT CORE | Fail-closed authorization registry exists; no real operator/carrier licenses are configured or proven | NO |
| Daily signed community publication | CORE IMPLEMENTED | Publisher format matches Android verifier; production key custody, sequence allocator and scheduler are not deployed | NO |
| Secure Android community-feed synchronization | IMPLEMENTED / NOT DEPLOYED | Bounded HTTPS client, explicit host allowlist, redirect/origin rejection, time/size/content-type limits and signed-package installation path are implemented and tested; no production endpoint, key distribution or end-to-end runtime evidence exists | YES, only as repository synchronization logic; NO live-service claim |
| Phone-intelligence web internationalization | IMPLEMENTED | Bounded French/English layer and deterministic fallback are tested | YES, only for this web workspace |
| Repository-wide internationalization | PARTIAL | Android, legal copy and other surfaces still contain untranslated or hard-coded strings | NO complete-i18n claim |
| Legal publisher readiness gate | IMPLEMENTED | Fail-closed structural validator is tested; it does not authenticate real identity or legal authority | YES, as a validator only |
| Verified legal publisher identity | NOT DEMONSTRATED | Real publisher, publication-director and contact evidence must be supplied and independently verified outside source control | NO |
| Precompiled APK distribution | NOT PUBLISHED | No signed release artifact currently demonstrated | NO |
| Autonomous incident response | NOT DEMONSTRATED | No evidence of an operational autonomous response plane | NO |
| Continuous human-equivalent monitoring | NOT DEMONSTRATED | No evidence of such a service | NO |
| Active protection of external infrastructure | NOT DEMONSTRATED | No operational enforcement channel | NO |
| Security certification | NOT CLAIMED | Requires independent certification/audit evidence | NO |

## Current completion gaps

The repository is not feature-complete or production-complete. The following gaps
remain material and must not be represented as completed:

| Gap | Required completion evidence |
|---|---|
| Durable moderation backend | Shared transactional database, authenticated identities, access control, migrations, backups and observed deployment |
| Distributed rate limiting and anti-abuse | Atomic Redis or equivalent shared enforcement with endpoint/global/user policies and concurrency tests |
| Production audit durability | Append-only or independently anchored audit storage with access control, retention and recovery evidence |
| Daily signed publication | Protected signing key, atomic sequence allocator, scheduler, monitoring and observed authentic publication |
| Key operations | Real issuer mapping, KMS/HSM custody, provisioning, rotation, revocation and incident evidence |
| Licensed sources | Executed authorization/license evidence, verified scope/validity and operational expiry handling |
| Android production synchronization | Owned endpoint, DNS/TLS monitoring, production trust distribution and device end-to-end evidence |
| TLS pinning | Production endpoint and certificate/key rotation strategy before client pin configuration |
| Complete internationalization | Android resources, backend errors, legal content and repository-wide hard-coded-string gates |
| Legal publication readiness | Independently verified publisher identity, publication director, contacts, hosting and target-jurisdiction review |
| Public Android release | Release keystore custody, signed artifact, checksums/provenance and observed publication channel |
| Repository protection | Enforced `main` ruleset, required checks, review policy and administrator-verified evidence |
| Exposed-secret incident closure | External revocation/rotation and confirmation that affected credentials are no longer valid |
| Independent assurance | External security assessment and any claimed certification evidence |

## Evidence checkpoint

This inventory was reconciled after merges #341 and #342, with `main` at
`5a4505b283574d518246856699f1f8abc85cfd8c`. The applicable PR heads completed
their required workflows before SHA-locked squash merges. This is repository CI
evidence, not production runtime or certification evidence.

## Evidence vocabulary

- `IMPLEMENTED`: relevant source code exists.
- `CORE IMPLEMENTED`: bounded repository logic exists, but a durable/deployed service is not implied.
- `ENFORCEMENT CORE`: policy enforcement logic exists, but external authorization evidence is still required.
- `READ-ONLY`: deliberately limited to observation or display.
- `TESTED`: automated tests have actually executed successfully for the relevant revision.
- `DEPLOYED`: the relevant deployment has actually been observed successfully.
- `NOT VALIDATED`: source exists, but current execution evidence is missing or blocked.
- `NOT DEPLOYED`: source-side prerequisites exist but the production service/channel has not been observed.
- `NOT PUBLISHED`: deliberately unavailable until release evidence exists.
- `NOT DEMONSTRATED`: a concept or historical material must not be presented as a current operational capability.
- `NOT CLAIMED`: no certification or legal conclusion is asserted.
- `PARTIAL`: a bounded subset exists, but repository-wide or end-to-end completion is not demonstrated.

## Release rule

The minimum evidence chain for a security-sensitive release is:

`source → tests → fuzzing/static analysis → dependency checks → build → signing → checksum/provenance → artifact → observed release`

If one link is missing, the documentation must state that gap instead of filling it with an assumption.

For the community phone/SMS feed specifically, repository code is not enough. A public operational claim additionally requires real source authorization, managed signing keys, durable moderation/appeal state, an observed publication endpoint and successful Android end-to-end synchronization evidence.

## Separation rule

Sentinel Quantum Vanguard AI Pro remains strictly independent from **A KI PRI SA YÉ**. Negative isolation fixtures may contain forbidden strings by design; those fixtures are test controls and must not be removed merely because they reference the other project.

Any new public capability must be added here with its source state, validation state and permitted claim before it is advertised.
