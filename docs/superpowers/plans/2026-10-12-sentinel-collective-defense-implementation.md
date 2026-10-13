# Sentinel Collective Defense Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

## Goal

Implement the approved Sentinel Collective Defense design as an incremental, privacy-preserving community threat-intelligence layer that reuses the existing Wangiri/Collective Defense backend and Android Phone Core. The first production-safe milestone must improve warning/silencing and community detection without introducing network I/O into `CallScreeningService`, without storing raw phone numbers in community telemetry, and without enabling community-driven `BLOCK` until explicit local safety exemptions are implemented and verified.

## Architecture

Extend, do not replace, the current stack. Backend work remains inside `backend/wangiri-api` and reuses Redis, HMAC pseudonymization, moderation and rate-limit patterns. Android work extends `CollectiveDefenseClient`, background WorkManager flows, and the existing call-rule/cache path. Signed offline `ThreatBundle` artifacts reuse the existing P-256/SHA256withECDSA trust pattern used by signed phone rules. The screening critical path stays process-memory-only; all tokenization, upload, enrichment and bundle synchronization happen outside the callback.

## Tech Stack

- Android/Kotlin, Jetpack Compose, WorkManager, Room/SharedPreferences, Android Keystore
- FastAPI/Python 3.12, Redis, phonenumbers
- Node.js crypto for offline publication/signing tooling
- GitHub Actions and Render validation

## Spec

`docs/superpowers/specs/2026-10-12-sentinel-collective-defense-design.md`

## Global Constraints

- No raw phone number, contacts, call history, SMS/MMS content, notes, audio or precise location in community event storage.
- No network, Room, SharedPreferences, AndroidKeyStore, DNS or disk I/O inside `CallScreeningService.onScreenCall()` before `respondToCall()`.
- Existing emergency fail-open behavior is preserved.
- Community intelligence is advisory. Initial local enforcement is `WARN`/`SILENCE`; `BLOCK` is gated behind explicit allowlist/trusted-contact protection and a separate acceptance gate.
- No production signing key, API secret or Redis credential is committed.
- No product-capability flag may claim deployed/runtime-verified/physical/customer-available without exact evidence.
- Each implementation task follows RED -> minimal implementation -> GREEN -> focused commit.

## Review Focus

Privacy boundary, spoofing attribution, rollback/expiry, screening latency, false-positive containment, anti-Sybil behavior, fail-open behavior, DROM normalization, and truthful production-readiness claims.

---

## Task 1 — Add the Android participation model and Privacy Gateway

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/background/CollectiveDefensePreferences.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/PhonePrivacyFirewall.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CommunityPrivacyGateway.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CommunityPrivacyGatewayTest.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/background/CollectiveDefensePreferencesContractTest.kt`

- [ ] RED: add tests for `NO_SHARING`, `MINIMAL_ANONYMOUS`, `ADVANCED_ANONYMOUS`; invalid persisted values must fail to `NO_SHARING`.
- [ ] RED: prove contacts, call-history detail, message bodies, OTP, notes, audio and precise coordinates cannot be emitted by `CommunityPrivacyGateway`.
- [ ] RED: prove timestamps are bucketed, territories are coarse codes, and advanced-only behavioral fields are stripped in minimal mode.
- [ ] Implement the enum, persistence and pure gateway DTO transformation. Keep this module network-free.
- [ ] GREEN: run `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests '*CommunityPrivacyGatewayTest' --tests '*CollectiveDefensePreferencesContractTest'`.
- [ ] Commit: `feat(collective): add privacy-gated participation tiers`.

## Task 2 — Add server-side epoch phone tokenization

**Files:**
- Create: `backend/wangiri-api/collective_phone.py`
- Create: `backend/wangiri-api/tests/test_collective_phone.py`
- Modify: `backend/wangiri-api/app_redis.py`
- Modify: `backend/wangiri-api/Dockerfile`
- Modify: `.github/workflows/wangiri-api.yml`
- Modify: `render.yaml`
- Modify: `backend/wangiri-api/tests/test_render_contract.py`

Design: Android may send normalized E.164 only in an explicit post-screening/opt-in tokenization request. The backend immediately derives `epoch_id + phone_token` using a dedicated server-only HMAC secret and must not persist/log the raw number. No global secret is returned to the client.

- [ ] RED: test normalization for France and DROM (`+33`, `+590`, `+594`, `+596`, `+262`) and reject malformed numbers.
- [ ] RED: test deterministic token equality inside one epoch, token rotation across epochs, no raw number in token/Redis keys/response, and 503 fail-closed when `COMMUNITY_PHONE_TOKEN_SECRET` is absent.
- [ ] RED: add Render contract requiring `COMMUNITY_PHONE_TOKEN_SECRET` as generated/server-only and explicit rate-limit configuration.
- [ ] Implement `POST /community/phone-token` with bounded JSON, no redirects, rate limiting and epoch derivation.
- [ ] Wire router into `app_redis.py`; copy module in Dockerfile; include it in `compileall` and packaged-route CI assertions.
- [ ] GREEN: run `cd backend/wangiri-api && python -m pytest -q tests/test_collective_phone.py tests/test_render_contract.py` and `python -m compileall -q app_redis.py collective_phone.py`.
- [ ] Commit: `feat(collective): add epoch phone tokenization`.

## Task 3 — Implement community event/correction schemas and bounded ingestion

**Files:**
- Modify: `backend/wangiri-api/collective_phone.py`
- Modify: `backend/wangiri-api/tests/test_collective_phone.py`

- [ ] RED: schemas accept only opaque token, epoch, approved event category, rounded time bucket, coarse territory, risk category, model/rule version and tier-appropriate behavior fields.
- [ ] RED: reject unknown fields, raw-number-shaped fields, free-text evidence, precise coordinates, oversized batches and stale/future buckets.
- [ ] RED: prove replay nonce and contributor dedupe use only pseudonymous material.
- [ ] Implement `POST /community/events` and `POST /community/corrections` using Redis TTL queues/aggregates with bounded batch size and anti-abuse rate limits.
- [ ] Keep public/community observations separate from trusted/official intelligence; no event directly updates a blocking rule.
- [ ] GREEN: run the focused pytest module plus the full `backend/wangiri-api` suite.
- [ ] Commit: `feat(collective): ingest bounded anonymous phone signals`.

## Task 4 — Build the pure campaign scoring and lifecycle engine

**Files:**
- Create: `backend/wangiri-api/community_campaigns.py`
- Create: `backend/wangiri-api/tests/test_community_campaigns.py`
- Modify: `backend/wangiri-api/collective_phone.py`
- Modify: `backend/wangiri-api/Dockerfile`
- Modify: `.github/workflows/wangiri-api.yml`

- [ ] RED: test sub-scores `volume`, `diversity`, `velocity`, `pattern`, `reputation`, `freshness`, `correctionPenalty` independently.
- [ ] RED: test lifecycle `OBSERVED -> EMERGING -> CONFIRMED -> DECLINING -> EXPIRED`, plus `DISPUTED` and `UNDER_REVIEW` conservative branches.
- [ ] RED: test that one contributor with 100 reports weighs less than independent contributors; fresh evidence outranks stale evidence; legitimate corrections reduce confidence.
- [ ] RED: test spoofing attribution does not poison the legitimate number/organization reputation.
- [ ] Implement pure deterministic scoring first; Redis adapter only supplies bounded aggregates.
- [ ] Add `GET /community/campaigns/{id}` returning aggregate data only, never raw events or contributor IDs.
- [ ] GREEN: run `python -m pytest -q tests/test_community_campaigns.py tests/test_collective_phone.py`.
- [ ] Commit: `feat(collective): add campaign scoring lifecycle`.

## Task 5 — Add temporary aggregate persistence and an explicit moderation-ledger boundary

**Files:**
- Modify: `backend/wangiri-api/collective_phone.py`
- Create: `backend/wangiri-api/community_store.py`
- Create: `backend/wangiri-api/tests/test_community_store.py`
- Modify: `backend/wangiri-api/Dockerfile`
- Modify: `.github/workflows/wangiri-api.yml`

- [ ] RED: verify event TTL cleanup, bounded campaign capacity, monotonic timestamps, dedupe and Redis-unavailable failure behavior.
- [ ] RED: define a `ModerationLedger` interface and prove the Redis implementation is explicitly marked temporary/non-durable; never call it durable append-only evidence.
- [ ] Implement short-retention Redis event/aggregate storage and campaign snapshots.
- [ ] Preserve a separate interface for future PostgreSQL durable audit/moderation wiring; do not retrofit the existing E.164 moderation tables to store raw community numbers.
- [ ] GREEN: focused pytest then full backend suite.
- [ ] Commit: `feat(collective): persist bounded campaign aggregates`.

## Task 6 — Create signed ThreatBundle and EmergencyRevoke publication tooling

**Files:**
- Create: `security/phone-intelligence/collective-threat-bundle-publication.js`
- Create: `security/phone-intelligence/collective-threat-bundle-publication.test.js`
- Modify: `config/module-continuity-inventory.json`
- Modify: `package.json` only if an existing test script must include the new test.

- [ ] RED: test canonical payload ordering, global/regional scope, campaign state, confidence, explanation codes, expiry, minimum engine version, previous hash and monotonic sequence.
- [ ] RED: test P-256/SHA256withECDSA verification, tamper rejection, unknown key rejection, rollback rejection and EmergencyRevoke bounded lifetime.
- [ ] Implement publisher/verifier by reusing the trust shape of `call-rule-publication.js`; signing material must be injected, never embedded.
- [ ] Keep continuity status `DORMANT_LIBRARY` until production key custody, durable sequence allocation, scheduler and publication storage exist.
- [ ] GREEN: run `node --test security/phone-intelligence/collective-threat-bundle-publication.test.js`.
- [ ] Commit: `feat(collective): add signed threat bundle format`.

## Task 7 — Serve pre-signed bundles without putting signing keys in FastAPI

**Files:**
- Modify: `backend/wangiri-api/collective_phone.py`
- Modify: `backend/wangiri-api/community_store.py`
- Modify: `backend/wangiri-api/tests/test_collective_phone.py`

- [ ] RED: `GET /community/bundles/latest` returns only a bounded pre-signed artifact for requested coarse scope and engine version.
- [ ] RED: no bundle, expired artifact, bad storage state or wrong scope returns conservative unavailable/not-found behavior rather than fabricated intelligence.
- [ ] RED: EmergencyRevoke endpoint/artifact retrieval remains separate and signed.
- [ ] Implement publication-store read path only. FastAPI must not hold a private signing key.
- [ ] GREEN: focused and full backend tests.
- [ ] Commit: `feat(collective): serve signed threat artifacts`.

## Task 8 — Verify/store ThreatBundles on Android and preload screening cache

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SignedThreatBundleVerifier.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/ThreatBundleStore.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/ThreatBundleSyncClient.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/SignedThreatBundleVerifierTest.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/ThreatBundleStoreContractTest.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt`

- [ ] RED: verify signature, issuer/key, sequence rollback, previous hash, expiry, scope and engine-version rejection.
- [ ] RED: prove corrupted/expired/revoked bundle never enters the process-memory screening snapshot and previous valid bundle is retained where allowed.
- [ ] Implement store with persistent verified envelope + volatile immutable screening snapshot.
- [ ] Preload the already-verified snapshot from `SentinelApplication`; no verification/disk/network inside `onScreenCall()`.
- [ ] Add sync client with exact-origin HTTPS/no redirects/bounded response, but keep production endpoint/key mapping disabled until provisioned.
- [ ] GREEN: focused Android unit tests.
- [ ] Commit: `feat(android): verify and cache collective threat bundles`.

## Task 9 — Integrate community decisions into Phone Core as WARN/SILENCE only

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CallRuleEngine.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt`
- Modify: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CallRuleEngineTest.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CommunityCallDecisionPolicyTest.kt`

- [ ] RED: Standard/Reinforced/Maximum threshold tests where unknown alone never blocks.
- [ ] RED: confirmed high-risk community campaign may map to `SILENCE`; warning maps to Android allow + reason metadata.
- [ ] RED: prove no community input can produce `BLOCK` in this milestone.
- [ ] RED: corruption/missing cache returns ALLOW; emergency classifier behavior remains unchanged.
- [ ] Implement pure `CommunityCallDecisionPolicy`; combine with existing manual block and signed-reputation rules without weakening explicit user rules.
- [ ] Ensure the callback reads only immutable memory snapshots and calls `respondToCall()` before logging/background work.
- [ ] GREEN: run unit tests and the existing Android Phone Core contract suite.
- [ ] Commit: `feat(phone): apply offline community warn and silence rules`.

## Task 10 — Add post-response opt-in contribution queue and WorkManager flush

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CommunityPhoneClient.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CommunityContributionQueue.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/background/CommunityContributionWorker.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/background/CollectiveDefenseWorkScheduler.kt`
- Create: corresponding JVM tests under `app/src/test/...`

- [ ] RED: `NO_SHARING` schedules/sends nothing.
- [ ] RED: queue persists only server-issued token + permitted metadata; test serialized queue contains no E.164/string phone value.
- [ ] RED: tokenization failure must drop/defer without persisting the raw number for retry.
- [ ] RED: bounded queue, backoff, duplicate suppression and consent revocation clear/stop outbound work.
- [ ] Implement post-response tokenization then token-only queueing. Never call from pre-response screening path.
- [ ] Implement WorkManager batch flush with `NetworkType.CONNECTED`; failures must not affect calls.
- [ ] GREEN: focused tests and `:app:testDebugUnitTest`.
- [ ] Commit: `feat(android): queue anonymous collective contributions`.

## Task 11 — Upgrade the existing Collective Defense UI without duplicating it

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CollectiveDefenseScreen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/background/CollectiveDefensePreferences.kt`
- Modify: `native-android-app/app/src/main/res/values/strings.xml`
- Create small composables/models only if `CollectiveDefenseScreen.kt` becomes harder to reason about.

- [ ] Add participation selector with explicit privacy explanation and default `Aucun partage`.
- [ ] Show bundle freshness/status, active campaigns, state/confidence/trend/explanation codes, coarse territory, and current local action.
- [ ] Add correction actions: legitimate, telemarketing, scam/fraud, with clear effect language.
- [ ] Never display server confidence as identity certainty; preserve current statement that community signals are not proof.
- [ ] Move newly touched hard-coded French strings into resources where practical; do not attempt unrelated repository-wide i18n in this task.
- [ ] Run Compose compile/unit tests and `./gradlew :app:lintDebug`.
- [ ] Commit: `feat(ui): expose collective campaign controls`.

## Task 12 — Add Shadow Decision, staged rollout and Campaign Replay Lab

**Files:**
- Modify: `backend/wangiri-api/community_campaigns.py`
- Create: `backend/wangiri-api/community_replay.py`
- Create: `backend/wangiri-api/tests/test_community_replay.py`
- Modify: `backend/wangiri-api/Dockerfile`
- Modify: `.github/workflows/wangiri-api.yml`

- [ ] RED: candidate scoring version can run in shadow without mutating live campaign state.
- [ ] RED: staged rollout assignment is deterministic from pseudonymous cohort material and never uses raw identifiers.
- [ ] RED: replay produces bounded aggregate metrics for 7/30-day fixtures: detection lead time, missed campaigns, correction/dispute rate and regional stability.
- [ ] Implement pure replay/scoring comparison first; no autonomous promotion to production enforcement.
- [ ] GREEN: focused tests and full backend suite.
- [ ] Commit: `feat(collective): add shadow scoring and replay lab`.

## Task 13 — Add the safety-exemption gate required before any community BLOCK

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CallerAllowlistStore.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CallerAllowlistStoreTest.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CallRuleEngine.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt`

- [ ] RED: allowlisted/trusted number suppresses only community/signed-reputation enforcement, while explicit user manual block remains authoritative.
- [ ] RED: allowlist lookup is process-memory-only during screening; unavailable cache fails open for community enforcement.
- [ ] RED: emergency/unknown emergency classification continues to bypass automated community blocking.
- [ ] Implement hashed local allowlist with preload/cached candidates; no raw number persistence.
- [ ] Add a feature gate `COMMUNITY_BLOCK_ENABLED=false` initially. Enabling it requires all safety tests, emulator evidence and an explicit product decision; do not turn it on in the same commit.
- [ ] Commit: `feat(phone): add community blocking safety gate`.

## Task 14 — CI, contract, privacy and latency qualification

**Files:**
- Modify: `.github/workflows/wangiri-api.yml`
- Modify: `.github/workflows/build-native-android.yml`
- Add/modify focused contract tests under backend and Android test trees.

- [ ] Backend CI asserts all community routes are packaged and new modules compile.
- [ ] Add tests that grep/inspect persisted event fixtures for absence of raw E.164 and precise coordinates.
- [ ] Add screening contract that fails if new network/storage/client creation appears before `respondToCall()`.
- [ ] Add deterministic benchmark/contract target: local decision p95 <150 ms in qualification harness and hard gate <500 ms; retain actual measured evidence rather than hard-coded success.
- [ ] Emulator scenarios: no bundle, valid bundle, expired, revoked, corrupt, Standard/Reinforced/Maximum, offline, process restart, consent revoke, worker retry.
- [ ] Run `cd backend/wangiri-api && python -m pytest -q`.
- [ ] Run `cd native-android-app && ./gradlew :app:testDebugUnitTest :app:lintDebug`.
- [ ] Run the repository Android emulation/Phone Core workflow; record exact run IDs and failures.
- [ ] Commit: `ci(collective): gate privacy and phone-core integration`.

## Task 15 — Preserve product truth and perform deployment/physical acceptance separately

**Files:**
- Modify only after evidence exists: `config/product-capabilities.json`
- Modify: `docs/PHONE_CORE_PHYSICAL_VALIDATION.md` only with actual executed observations.
- Update Collective Defense operational docs as evidence becomes available.

- [ ] Before deployment, leave `collective_defense_backend.deployed/runtime_verified=false`, Android physical validation false, signed release false, and customer availability false.
- [ ] Provision secrets/configuration through Render environment, never repository values. Validate Redis, tokenization, event ingestion, campaign read, and signed bundle retrieval against exact deployed commit.
- [ ] Do not call temporary Redis moderation data a durable moderation ledger; `phone_moderation_backend` remains blocked until real durable PostgreSQL/identity/rate-limit deployment exists.
- [ ] Execute S24+ physical tests for lookup/report/watch/background notification plus new bundle sync, offline screening, consent revoke and actual call-screening latency.
- [ ] Community `BLOCK` remains disabled until allowlist/trusted-contact, false-positive, emergency and rollback evidence pass on emulator and S24+.
- [ ] Only then update capability evidence with exact SHA, timestamps/run IDs and physical observations.
- [ ] Commit evidence changes separately: `docs(collective): record verified deployment evidence`.

## Final verification before implementation completion

- [ ] `git diff --check`
- [ ] Backend full tests + dependency audit + Docker route contract green.
- [ ] Android unit/lint/build/emulation gates green for exact head.
- [ ] No raw phone number or prohibited PII in community persistent stores/log fixtures.
- [ ] No pre-response network/storage regression in `CallScreeningService`.
- [ ] Signed bundle tamper/rollback/expiry/revoke tests green.
- [ ] `COMMUNITY_BLOCK_ENABLED` remains false unless the dedicated acceptance gate has passed.
- [ ] Product capability document reflects evidence, not intention.
- [ ] Request code review before merge; merge only after required branch protections and approvals.