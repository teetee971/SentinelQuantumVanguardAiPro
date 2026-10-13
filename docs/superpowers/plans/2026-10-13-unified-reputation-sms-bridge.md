# Unified Reputation SMS Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect caller reputation already fetched by Phone Core to incoming SMS risk processing through a privacy-preserving local cache, without making network access part of SMS delivery or CallScreening.

**Architecture:** Add a small app-private reputation cache keyed by a one-way digest of the normalized phone number, then secret-derive the actual persisted Android storage key with an install-local HMAC secret so the stored key is not a bare enumerable phone digest. Caller ID writes only bounded reputation metadata after a consented remote enrichment succeeds; the SMS receiver reads only fresh cached metadata when current consent/privacy policy still permits it and combines it with local SMS analysis through a pure evaluator that preserves independent reasons instead of inventing an opaque score. No raw number, SMS body, URL or contact data is persisted by the new bridge.

**Tech Stack:** Kotlin, Android SharedPreferences, SHA-256, HMAC-SHA-256, JUnit, existing `CallRuleEngine`, `CallerReputationClient`, `SmsLinkAnalyzer`, `PhoneRiskCard`, `PhonePrivateTimelineStore`.

**Spec:** User-supplied Unified Intelligence Core / Full Module Interconnection command in the active project conversation.

## Global Constraints

- Phone Core critical screening path must stay offline/local and within the existing 200–500 ms budget.
- No remote reputation lookup from `CallScreeningService` or SMS delivery.
- No raw phone number, SMS body, URL, email or contact record in the new cache.
- Persisted reputation keys must not expose a bare enumerable phone-number digest.
- Expired or malformed reputation must resolve to UNKNOWN / absent, never a synthetic PASS.
- Revoked consent or a protection mode that no longer permits caller-number enrichment must make cached remote-derived reputation invisible to SMS risk processing immediately.
- Existing module contracts are reused; no God object and no circular dependencies.

## Review Focus

- Expired cache entry must not influence SMS risk.
- Invalid phone numbers must not create cache keys.
- Future timestamps and non-positive TTL must be rejected.
- A HIGH local SMS analysis remains HIGH even when cached reputation is LOW.
- Cached HIGH reputation can raise an otherwise LOW SMS context without changing the original analyzer result.
- Revoked consent must not reuse cached remote-derived reputation.
- SharedPreferences must persist a secret-derived HMAC storage key, not the bare SHA-256 phone digest.

---

### Task 1: Pure cross-module risk context

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/UnifiedCommunicationRisk.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/UnifiedCommunicationRiskTest.kt`

**Interfaces:**
- Consumes: `SmsLinkAnalyzer.Analysis?`, `PhoneRiskCard.Card?`
- Produces: `UnifiedCommunicationRisk.Assessment` with `level`, `smsLevel`, `reputationBand`, `reasons`.

- [x] Write tests proving max-severity composition, UNKNOWN preservation, and independent reasons.
- [x] Observe RED because the class did not exist.
- [x] Implement the minimal pure evaluator.
- [x] Verify GREEN on Android JVM unit tests.

### Task 2: Privacy-preserving local reputation cache

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CommunicationReputationCache.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SharedPreferencesCommunicationReputationStorage.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CommunicationReputationCacheContractTest.kt`
- Instrumentation: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/CommunicationReputationStorageInstrumentationTest.kt`

**Interfaces:**
- Consumes: normalized phone input plus bounded `CallerReputationClient.Result` fields.
- Produces: fresh `Entry?` containing score, categories, signals, observedAtMs and ttlMs only.

- [x] Write contracts for digest keying, no raw-number persistence, expiry, malformed timestamps and bounded values.
- [x] Implement bounded cache and SharedPreferences adapter.
- [x] Add RED instrumentation contract rejecting the bare phone digest as the actual persisted storage key.
- [x] Secret-derive actual persisted keys with install-local HMAC-SHA-256 while preserving the cache API.
- [ ] Complete exact-head Android instrumentation on the final branch head.

### Task 3: Caller ID writes shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CommunicationReputationWriterTest.kt`

**Interfaces:**
- Consumes: successful consented `CallerReputationClient.Result`.
- Produces: local bounded cache entry after remote enrichment, off the main thread.

- [x] Write RED mapping/writer contracts.
- [x] Persist successful reputation only.
- [x] Keep persistence on the existing IO path.

### Task 4: Incoming SMS reads shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingSmsRiskContext.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/IncomingSmsRiskContextTest.kt`

**Interfaces:**
- Consumes: local SMS analysis plus fresh cached reputation for sender, gated again by current consent/privacy mode.
- Produces: a unified local risk assessment and a PII-free timeline signal `SMS_UNIFIED_RISK_HIGH` when meaningful.

- [x] Write RED contract proving cached caller reputation affects SMS context without network access.
- [x] Read cache on the existing SMS worker and build the pure assessment.
- [x] Append only bounded PII-free high-risk signal metadata.
- [x] Add consent-revocation RED/GREEN coverage and fail closed when current policy no longer permits cached remote-derived reputation.
- [ ] Complete final exact-head unit/lint/build/instrumentation/emulation qualification.

### Follow-up boundary

The dialer also performs consented `CallerReputationClient.evaluate(...)` lookups. Its result is currently UI-local and is not yet written through `CommunicationReputationWriter`. That is a real remaining silo, but `SentinelDialerActivity.kt` is a large high-risk surface; wire it only with a focused RED contract and a narrow edit, not by blind whole-file rewriting.
