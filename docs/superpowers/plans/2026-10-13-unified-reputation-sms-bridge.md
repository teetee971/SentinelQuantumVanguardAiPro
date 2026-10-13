# Unified Reputation SMS Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect caller reputation already fetched by Phone Core to incoming SMS risk processing through a privacy-preserving local cache, without making network access part of SMS delivery or CallScreening.

**Architecture:** Add a small app-private reputation cache keyed by a one-way digest of the normalized phone number. Caller ID writes only bounded reputation metadata after a consented remote enrichment succeeds; the SMS receiver reads only fresh cached metadata and combines it with local SMS analysis through a pure evaluator that preserves independent reasons instead of inventing an opaque score. No raw number, SMS body, URL or contact data is persisted by the new bridge.

**Tech Stack:** Kotlin, Android SharedPreferences, SHA-256, JUnit, existing `CallRuleEngine`, `CallerReputationClient`, `SmsLinkAnalyzer`, `PhoneRiskCard`, `PhonePrivateTimelineStore`.

**Spec:** User-supplied Unified Intelligence Core / Full Module Interconnection command in the active project conversation.

## Global Constraints

- Phone Core critical screening path must stay offline/local and within the existing 200–500 ms budget.
- No remote reputation lookup from `CallScreeningService` or SMS delivery.
- No raw phone number, SMS body, URL, email or contact record in the new cache.
- Expired or malformed reputation must resolve to UNKNOWN / absent, never a synthetic PASS.
- Existing module contracts are reused; no God object and no circular dependencies.

## Review Focus

- Expired cache entry must not influence SMS risk.
- Invalid phone numbers must not create cache keys.
- Future timestamps and non-positive TTL must be rejected.
- A HIGH local SMS analysis remains HIGH even when cached reputation is LOW.
- Cached HIGH reputation can raise an otherwise LOW SMS context without changing the original analyzer result.

---

### Task 1: Pure cross-module risk context

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/UnifiedCommunicationRisk.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/UnifiedCommunicationRiskTest.kt`

**Interfaces:**
- Consumes: `SmsLinkAnalyzer.Analysis?`, `PhoneRiskCard.Card?`
- Produces: `UnifiedCommunicationRisk.Assessment` with `level`, `smsLevel`, `reputationBand`, `reasons`.

- [ ] Write tests proving max-severity composition, UNKNOWN preservation, and independent reasons.
- [ ] Run focused test and observe RED because the class does not exist.
- [ ] Implement the minimal pure evaluator.
- [ ] Run focused test and observe GREEN.
- [ ] Commit.

### Task 2: Privacy-preserving local reputation cache

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CommunicationReputationCache.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CommunicationReputationCacheContractTest.kt`

**Interfaces:**
- Consumes: normalized phone input plus bounded `CallerReputationClient.Result` fields.
- Produces: fresh `Entry?` containing score, categories, signals, observedAtMs, ttlMs and source only.

- [ ] Write tests/contracts for SHA-256 keying, no raw-number persistence, expiry, malformed timestamps and bounded values.
- [ ] Observe RED.
- [ ] Implement minimal cache.
- [ ] Observe GREEN.
- [ ] Commit.

### Task 3: Caller ID writes shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt`
- Test: add a contract test under `native-android-app/app/src/test/java/com/sentinel/quantum/security/` that proves the cache mapping from `CallerReputationClient.Result`.

**Interfaces:**
- Consumes: successful consented `CallerReputationClient.Result`.
- Produces: local bounded cache entry after remote enrichment, off the main thread.

- [ ] Write RED contract.
- [ ] Persist successful reputation only.
- [ ] Verify GREEN.
- [ ] Commit.

### Task 4: Incoming SMS reads shared reputation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/UnifiedCommunicationRiskTest.kt` and/or receiver contract test.

**Interfaces:**
- Consumes: local SMS analysis plus fresh cached reputation for sender.
- Produces: a unified local risk assessment and a PII-free timeline signal such as `SMS_UNIFIED_RISK_HIGH` when meaningful.

- [ ] Write RED contract proving cached caller reputation affects SMS context without network access.
- [ ] Read cache on the existing worker, build the pure assessment, and append only bounded signal metadata.
- [ ] Verify focused tests and full app unit suite.
- [ ] Run lint/build/CI exact-head gates.
- [ ] Commit and update the PR with RED/GREEN evidence.
