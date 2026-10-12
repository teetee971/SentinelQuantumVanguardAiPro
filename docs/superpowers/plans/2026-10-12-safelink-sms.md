# SafeLink SMS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SMS links safely clickable by analyzing messages on receipt and intercepting every link at click time with an explainable risk decision.

**Architecture:** Extend `SmsLinkAnalyzer` from aggregate message scoring to per-link findings, keep receive-time analysis local, and route every SMS URL through a dedicated SafeLink decision surface before external browser navigation. Optional reputation enrichment must be asynchronous, bounded, HTTPS-only, privacy-preserving, and never weaken local risk when unavailable.

**Tech Stack:** Kotlin, Jetpack Compose, Android SMS provider/default-role flows, URI parsing/IDN handling, JUnit, existing SMS emulator/unit qualification.

**Spec:** `docs/superpowers/specs/2026-10-12-phone-core-security-intelligence-design.md`

## Global Constraints

- Never open an SMS URL silently from Sentinel.
- No internal arbitrary WebView for remote links.
- Receive-time SMS handling must remain bounded and local.
- Raw SMS bodies are not uploaded for URL reputation scoring.
- `UNKNOWN` must never be rendered as safe/green.
- Existing SMS role/permission fail-closed behavior remains unchanged.

## Review Focus

- Unicode/punycode hosts must display both user-facing and normalized forms when they differ.
- A reputation timeout must not downgrade a locally HIGH risk URL.
- A shortened URL must remain marked as shortened if expansion is unavailable.
- A malformed URL must not crash rendering or bypass SafeLink.
- Multiple links in one message must retain independent findings and independent click decisions.

---

### Task 1: Per-link analysis model

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsLinkAnalyzer.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/SmsLinkAnalyzerTest.kt`

**Interfaces:**
- Produce: `data class LinkAnalysis(val original:String, val normalized:String?, val host:String?, val displayHost:String?, val riskLevel:RiskLevel, val score:Int, val findings:List<Finding>, val shortened:Boolean)`.
- Extend `Analysis` with `links: List<LinkAnalysis>` while preserving aggregate fields used by existing callers.

- [ ] **Step 1: Write failing tests** for independent multiple-link findings, malformed links, HTTP, IP literal, userinfo, shortener, suspicious TLD.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SmsLinkAnalyzerTest'`; verify RED.
- [ ] **Step 3: Implement** per-link analysis and derive aggregate risk from link/message findings without changing existing LOW/MEDIUM/HIGH thresholds unless a test/spec pins it.
- [ ] **Step 4: Run existing mapper/UI tests** to prove compatibility.
- [ ] **Step 5: Commit** `refactor(sms): expose per-link security analysis`.

### Task 2: Unicode, punycode, and host-deception signals

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsLinkAnalyzer.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/SmsLinkAnalyzerTest.kt`

**Interfaces:**
- Produce helper `fun canonicalHostForDisplay(host: String): HostPresentation` with `unicode`, `ascii`, `mixedScript`, `suspicious`.

- [ ] **Step 1: Write failing tests** for IDN host, mixed-script label, explicit port, unsupported scheme, visually deceptive userinfo/host constructs.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement** with `java.net.IDN` and deterministic mixed-script heuristics; do not claim brand impersonation as certainty.
- [ ] **Step 4: Run focused tests**; expect PASS.
- [ ] **Step 5: Commit** `feat(sms): detect deceptive link presentation`.

### Task 3: SafeLink decision policy

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SafeLinkPolicy.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/SafeLinkPolicyTest.kt`

**Interfaces:**
- Produce: `enum class SafeLinkAction { OPEN_DIRECT_CONFIRM, WARN_CONFIRM, BLOCK_PRIMARY, UNKNOWN_CONFIRM }`.
- Produce: `data class SafeLinkDecision(val action:SafeLinkAction, val title:String, val reasons:List<String>, val primaryLabel:String, val secondaryLabel:String?)`.
- Produce: `fun decide(local: LinkAnalysis, reputation: ReputationResult?): SafeLinkDecision`.

- [ ] **Step 1: Write failing tests** mapping LOW/MEDIUM/HIGH/UNKNOWN and reputation timeout/error combinations to actions.
- [ ] **Step 2: Run focused test** and verify RED.
- [ ] **Step 3: Implement** monotonic risk merge: network enrichment may raise confidence/risk but unavailable enrichment never lowers local risk.
- [ ] **Step 4: Run tests**; expect PASS.
- [ ] **Step 5: Commit** `feat(sms): add SafeLink decision policy`.

### Task 4: Click interception surface

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/SafeLinkActivity.kt`
- Modify: `native-android-app/app/src/main/AndroidManifest.xml`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/PhoneCoreWorkspace.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/SafeLinkIntentPolicyTest.kt`

**Interfaces:**
- Produce explicit internal intent extras: URL and originating message/thread identifiers only as bounded metadata.
- Produce external open helper `fun browserIntent(url:String): Intent?` limited to `http`/`https`.

- [ ] **Step 1: Write failing contract/tests** proving conversation-rendered links target `SafeLinkActivity`, not `ACTION_VIEW` directly.
- [ ] **Step 2: Run focused tests/contracts** and verify RED.
- [ ] **Step 3: Implement** link annotations in SMS body rendering and route taps to SafeLink.
- [ ] **Step 4: Implement SafeLink UI** showing full host, normalized host when different, risk level, concrete reasons, and reputation availability.
- [ ] **Step 5: Implement risk-specific confirmation**: LOW normal open action, MEDIUM explicit warning, HIGH safe action primary plus secondary override confirmation, UNKNOWN limitation notice.
- [ ] **Step 6: Run UI/unit tests**; expect PASS.
- [ ] **Step 7: Commit** `feat(sms): intercept links through SafeLink`.

### Task 5: Receive-time and notification safety integration

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt`
- Modify: SMS notification rendering files discovered during implementation.
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt`
- Test: relevant receiver/notification tests.

**Interfaces:**
- Consume: enhanced `SmsLinkAnalyzer.Analysis`.
- Persist only bounded risk metadata already permitted by timeline policy; no raw URL/message expansion into private timeline unless existing privacy policy explicitly allows it.

- [ ] **Step 1: Write failing tests** proving receive-time analysis records HIGH/MEDIUM metadata before conversation open and notification preview does not expose misleading safe state.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Wire per-link/aggregate metadata** into existing receive/timeline path without network calls.
- [ ] **Step 4: Run receiver/SMS tests**; expect PASS.
- [ ] **Step 5: Commit** `feat(sms): surface pre-open phishing risk`.

### Task 6: Optional reputation provider boundary

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SafeLinkReputationClient.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/SafeLinkReputationClientTest.kt`

**Interfaces:**
- Produce: `sealed interface ReputationResult { data class Known(...); data object Unavailable; data object Timeout; data class Error(val code:String) }`.
- Produce: `suspend fun lookup(url: String): ReputationResult` with strict timeout and HTTPS endpoint validation.

- [ ] **Step 1: Write failing tests** for HTTPS-only config, timeout, invalid response, cache hit, URL/domain-only request payload.
- [ ] **Step 2: Run tests** and verify RED.
- [ ] **Step 3: Implement client** behind explicit configuration; disabled/unconfigured returns `Unavailable`.
- [ ] **Step 4: Integrate into SafeLinkActivity** asynchronously after local decision is already visible.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** `feat(sms): add bounded SafeLink reputation enrichment`.

### Task 7: SafeLink qualification

**Files:**
- Add/modify tests under `security/phone-intelligence/`.
- Modify SMS-related emulator qualification scripts if SMS UI is currently exercised there.

- [ ] **Step 1: Add RED contracts** proving no direct external URL open from SMS UI.
- [ ] **Step 2: Add test corpus** for safe, medium, high, unknown, Unicode/punycode, malformed, multi-link messages.
- [ ] **Step 3: Run unit tests, lint, assemble and emulator/UI tests**.
- [ ] **Step 4: Verify no raw SMS/URL leakage in logs/evidence artifacts**.
- [ ] **Step 5: Commit** `test(sms): qualify SafeLink phishing protection`.
