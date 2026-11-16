# Sentinel — Feature Evidence Inventory

This inventory describes the evidence vocabulary and the broader repository surface. Current operational status for release-critical capabilities is canonical in `config/product-capabilities.json` and rendered into `docs/PRODUCT_CAPABILITY_STATUS.md`.

A capability is not operational merely because source code exists. Repository tests, deployment evidence, runtime evidence, physical validation and public release evidence are separate facts.

## Broader feature evidence

| Capability | Source state | Validation boundary | Public claim allowed |
|---|---|---|---|
| PWA/web interface | IMPLEMENTED | Frontend/static checks and build path exist; every deployment still needs revision-bound evidence | YES, current-scope wording only |
| Cloudflare Pages deployment path | IMPLEMENTED | Build/deploy path exists; a past deployment does not prove the current revision is deployed | YES, without security-certification wording |
| Local Android security checks | IMPLEMENTED | Manifest validation, unit tests, lint, APK build and emulator smoke checks exist | YES as tested source capability; NO signed-release implication |
| Public OSINT feed retrieval | IMPLEMENTED | HTTPS allowlisted public-feed parser and bounded retrieval exist; source availability is runtime-dependent | YES, read-only/public-feed wording |
| AI governance controls | IMPLEMENTED | Evaluation, approval, trust and audit controls are repository-tested | YES as governance/control code |
| Decision-plane validation | IMPLEMENTED | Validation/simulation/policy libraries are tested; privileged autonomous runtime is not implied | YES as validation logic only |
| Sentinel / A KI PRI SA YÉ isolation | IMPLEMENTED | Dedicated isolation scanner and negative fixtures exist | YES, strict separation requirement |
| Red Team simulation | IMPLEMENTED | Synthetic/deterministic simulation only | YES, simulation/training wording |
| Public threat intelligence | READ_ONLY | Public-source/advisory code exists; no live SOC implication | YES, read-only/public-source wording |
| Bounded local logging | IMPLEMENTED | Local bounds and redaction controls exist; callers remain responsible for not supplying secrets | YES, bounded local logging wording |
| Deterministic explainability | IMPLEMENTED | Local deterministic explanation support exists | YES, not as certainty or external verification |
| Local phone/SMS intelligence workspace | IMPLEMENTED | Local analyzers, lists and tests exist | YES, local-only wording |
| Android user-owned call blocking | IMPLEMENTED | Exact/prefix local rules exist and remain under user control | YES for local user-owned rules |
| Signed Android reputation-rule verification | IMPLEMENTED | Signature/verifier/store contract exists; live feed state is tracked in the canonical capability registry | YES as verification logic only |
| Moderated community phone reports | CORE_IMPLEMENTED | Reference moderation/appeal/anti-abuse logic exists; durable deployed service state is tracked canonically | NO live-service claim unless registry permits it |
| Licensed operator/source ingestion | ENFORCEMENT_CORE | Authorization policy exists; executed licences/authorizations are external evidence | NO without verified authorization |
| Daily signed community publication | CORE_IMPLEMENTED | Publisher contract exists; production signing/publication state is canonical in the registry | NO live-feed claim unless registry permits it |
| Secure Android community-feed synchronization | IMPLEMENTED | Bounded HTTPS, host/origin and signed-package controls exist | YES as synchronization logic; NO live-service implication |
| Collective Defense indicator reputation API | IMPLEMENTED | Backend state is tracked separately from Android release state | Follow canonical capability registry |
| Android Collective Defense center | IMPLEMENTED | Client/work/background-notification code exists; release/runtime/device evidence is separate | Follow canonical capability registry |
| Phone-intelligence web internationalization | IMPLEMENTED | Bounded French/English web workspace exists | YES for that workspace only |
| Repository-wide internationalization | PARTIAL | Android and other surfaces still contain hard-coded user-facing strings | NO complete-i18n claim |
| Legal publisher readiness gate | IMPLEMENTED | Structural fail-closed validator exists; it cannot authenticate legal identity | YES as validator only |
| Verified legal publisher identity | NOT_DEMONSTRATED | Requires independently verified publisher/director/contact/hosting evidence | NO |
| Precompiled public Android distribution | NOT_PUBLISHED | Build pipelines do not equal observed signed publication | NO until canonical release capability is available |
| Autonomous incident response | NOT_DEMONSTRATED | No operational autonomous privileged response plane is evidenced | NO |
| Continuous human-equivalent monitoring | NOT_DEMONSTRATED | No such operational service is evidenced | NO |
| Active protection of external infrastructure | NOT_DEMONSTRATED | No external enforcement channel is evidenced | NO |
| Security certification | NOT_CLAIMED | Requires independent audit/certification evidence | NO certification claim |

## Release-critical source of truth

Do not copy current availability status into this file. Use:

- `config/product-capabilities.json` — canonical machine-readable state;
- `scripts/check-product-capabilities.js` — consistency/evidence-path invariants;
- `scripts/render-product-capabilities.js` — generated human-readable status;
- `docs/PRODUCT_CAPABILITY_STATUS.md` — generated view;
- `.github/workflows/product-capability-truth.yml` — dedicated CI gate.

The release gate also validates the canonical registry and rejects a stale generated document.

## Evidence vocabulary

- `IMPLEMENTED`: relevant source code exists.
- `CORE_IMPLEMENTED`: bounded repository logic exists, without implying a durable/deployed service.
- `ENFORCEMENT_CORE`: policy/enforcement logic exists, while external authorization or production binding remains required.
- `READ_ONLY`: deliberately limited to observation/display.
- `PARTIAL`: a bounded subset exists; completion is not demonstrated.
- `TESTED`: automated tests executed successfully for the exact revision being discussed.
- `DEPLOYED`: the exact relevant revision/deployment was observed successfully.
- `RUNTIME_VERIFIED`: runtime behavior was observed on the exact deployed/released capability.
- `PHYSICALLY_VALIDATED`: required real-device/real-network evidence was completed for the declared scope.
- `RELEASE_SIGNED`: the release artifact, signature and provenance were verified.
- `NOT_PUBLISHED`: no public release publication has been demonstrated.
- `NOT_DEMONSTRATED`: source/history does not establish an operational capability.
- `NOT_CLAIMED`: no certification/legal conclusion is asserted.

## Minimum evidence chain

For a security-sensitive release, the relevant chain is:

`source → tests → static/fuzz/dependency checks → build → configuration → deployment (when required) → runtime evidence → physical validation (when required) → signing (when required) → checksum/provenance → observed release`

If a required link is missing, the canonical registry must keep `customer_available=false` and record the blocker rather than filling the gap with an assumption.

## Phone Core rule

Phone Core v5 requires exactly 14 physical criteria. Emulator or instrumentation tests may validate software behavior, but they must never manufacture or satisfy operator/device evidence that the physical protocol requires.

## Separation rule

Sentinel Quantum Vanguard AI Pro remains strictly independent from **A KI PRI SA YÉ**. Negative isolation fixtures may contain forbidden strings by design; those fixtures are test controls and must not be removed merely because they reference the other project.
