# Unified Reputation SMS Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect caller reputation already fetched by Phone Core to incoming SMS risk processing through a privacy-preserving local cache, without making network access part of SMS delivery or CallScreening.

**Architecture:** Add a small app-private reputation cache keyed from the normalized phone number. The logical cache identifier is one-way, while the actual SharedPreferences key is re-derived with an install-local HMAC-SHA-256 secret so an extracted preference file does not expose a bare enumerable phone-number digest. Successful consented reputation producers write only bounded reputation metadata; the SMS receiver reads only fresh cached metadata when current consent/privacy policy still permits using remote-derived reputation and combines it with local SMS analysis through a pure evaluator that preserves independent reasons instead of inventing an opaque score. No raw number, SMS body, URL or contact data is persisted by the new bridge.

**Tech Stack:** Kotlin, Android SharedPreferences, HMAC-SHA-256, JUnit, existing `CallRuleEngine`, `CallerReputationClient`, `SmsLinkAnalyzer`, `PhoneRiskCard`, `PhonePrivateTimelineStore`.

**Spec:** User-supplied Unified Intelligence Core / Full Module Interconnection command in the active project conversation.

## Global Constraints

- Phone Core critical screening path must stay offline/local and within the existing 200–500 ms budget.
- No remote reputation lookup from `CallScreeningService` or SMS delivery.
- No raw phone number, SMS body, URL, email or contact record in the new cache.
- Persisted reputation keys must not be reversible through trivial enumeration of bare phone-number hashes.
- Expired or malformed reputation must resolve to UNKNOWN / absent, never a synthetic PASS.
- Current consent/privacy policy must be re-evaluated when cached remote-derived reputation is consumed.
- Existing module contracts are reused; no God object and no circular dependencies.

## Review Focus

- Expired cache entry must not influence SMS risk.
- Invalid phone numbers must not create cache keys.
- Future timestamps and non-positive TTL must be rejected.
- A HIGH local SMS analysis remains HIGH even when cached reputation is LOW.
- Cached HIGH reputation can raise an otherwise LOW SMS context without changing the original analyzer result.
- Disabling remote enrichment must make cached remote-derived reputation invisible immediately.
- Every production surface that successfully obtains a consented `CallerReputationClient.Result` must feed the shared cache instead of retaining it in a UI silo.

---

### Task 1: Pure cross-module risk context

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/UnifiedCommunicationRisk.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/UnifiedCommunicationRiskTest.kt`

**Interfaces:**
- Consumes: `SmsLinkAnalyzer.Analysis?`, `PhoneRiskCard.Card?`
- Produces: `UnifiedCommunicationRisk.Assessment` with `level`, `smsLevel`, `reputationBand`, `reasons`.

- [x] Write tests proving max-severity composition, UNKNOWN preservation, and independent reasons.
- [x] Implement the minimal pure evaluator.
- [x] Commit.

### Task 2: Privacy-preserving local reputation cache

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CommunicationReputationCache.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SharedPreferencesCommunicationReputationStorage.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CommunicationReputationCacheContractTest.kt`
- Instrumentation: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/CommunicationReputationStorageInstrumentationTest.kt`

**Interfaces:**
- Consumes: normalized phone input plus bounded `CallerReputationClient.Result` fields.
- Produces: fresh `Entry?` containing score, categories, signals, observedAtMs and ttlMs.

- [x] Cover expiry, malformed timestamps, bounded values and no raw-number persistence.
- [x] Reject future timestamps/non-positive TTL.
- [x] Derive actual persisted keys with install-local HMAC-SHA-256 rather than storing the bare phone digest.
- [x] Keep HMAC secret separate from the reputation-entry preferences file.
- [x] Verify Android backup/device-transfer policy excludes the app-private secret/cache.
- [x] Commit.

### Task 3: Caller ID writes shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/CallerIdReputationPersistenceContractTest.kt`

**Interfaces:**
- Consumes: successful consented `CallerReputationClient.Result`.
- Produces: local bounded cache entry after remote enrichment, off the main thread.

- [x] Add source contract.
- [x] Persist successful reputation only on the existing IO path.
- [x] Commit.

### Task 4: Incoming SMS reads shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingSmsRiskContext.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/IncomingSmsRiskContextTest.kt`

**Interfaces:**
- Consumes: local SMS analysis plus fresh cached reputation for sender.
- Produces: a unified local risk assessment and PII-free timeline signal `SMS_UNIFIED_RISK_HIGH` when meaningful.

- [x] Prove cached caller reputation affects SMS context without network access.
- [x] Read cache on the existing worker and build the pure assessment.
- [x] Re-evaluate current consent/privacy mode before consuming cached remote-derived reputation.
- [x] Append only bounded PII-free signal metadata.
- [x] Commit.

### Task 5: Dialer lookup writes shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/SentinelDialerReputationPersistenceContractTest.kt`

- [x] Observe RED on head `bde12ed312b8cc3e3ca066fedb09ff29b6de7454`: 1116 tests, exactly one failure in `SentinelDialerReputationPersistenceContractTest`.
- [x] Persist the successful consented dialer lookup through `CommunicationReputationWriter` inside its existing `Dispatchers.IO` path.
- [x] Observe unit-test GREEN on implementation head `8e3b6f7ab9a637e64fd5e1c9328a91ee228b6e23` before the workflow was superseded.
- [x] Commit.

### Task 6: Phone Security lookup writes shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/PhoneSecurityReputationPersistenceContractTest.kt`

- [x] Observe RED on head `2f94a455e836afbb56fc62f28f724a5ac982a30a`: 1117 tests, exactly one failure in `PhoneSecurityReputationPersistenceContractTest`.
- [x] Persist the successful consented Phone Security lookup through `CommunicationReputationWriter` inside its existing `Dispatchers.IO` path.
- [ ] Observe exact-head GREEN on the implementation head or a documentation-only descendant containing the same code.
- [ ] Run final lint/build/instrumentation/emulation and required gates.

## Producer Audit

Production uses of `CallerReputationClient` found by the repository audit are:

- `CallerIdActivity` — wired to shared cache;
- `SentinelDialerActivity` — wired to shared cache;
- `PhoneSecurityScreen` — wired to shared cache on current implementation head.

No remote reputation lookup is introduced into CallScreening or SMS delivery.

## Remaining Privacy Review

A stricter follow-up should verify post-response persistence when consent is revoked while an already-authorized HTTP request is in flight. The dynamic egress gate already prevents a request from starting/continuing past its pre-send gate, and SMS consumption rechecks current consent, but persistence after the HTTP response should also be explicitly fail-closed before this slice is considered complete.

## Final Validation

The branch remains fail-closed until the current exact head passes unit tests, lint, build/APK checks, Android instrumentation, Phone Core emulation and the repository/security gates required by the PR. Historical green checks on superseded heads are evidence for those exact heads only and are not promoted to a current-head readiness claim.
