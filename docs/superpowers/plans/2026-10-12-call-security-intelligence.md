# Call Security Intelligence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add truthful, capability-gated call security intelligence: post-response enrichment, optional conversational screening, transcription, fraud-risk signals, synthetic-voice assessment, summaries, and voicemail capability handling.

**Architecture:** Keep `CallScreeningService` as a strict local fast path and move all heavier intelligence behind a post-response capability layer. Conversation features are exposed only when a runtime capability probe proves supported input on the current Android/OEM/device. Visual voicemail is a separate carrier/platform capability, not assumed from ROLE_DIALER.

**Tech Stack:** Kotlin, Android Telecom/CallScreeningService/InCallService, Compose, coroutines, existing backend only where explicitly configured, JUnit, emulator contracts, physical S24+ validation for OEM/audio claims.

**Spec:** `docs/superpowers/specs/2026-10-12-phone-core-security-intelligence-design.md`

## Global Constraints

- `CallScreeningService.respondToCall()` remains local, synchronous and bounded; no LLM/network/disk-heavy work before response.
- Emergency calls remain fail-open and are never blocked by heuristic conversational intelligence.
- Runtime capability states are explicit: `AVAILABLE`, `LIMITED`, `UNSUPPORTED`, `PERMISSION_REQUIRED`.
- No call-audio, transcript, deepfake, or visual-voicemail claim without runtime/platform proof.
- Fraud/deepfake results are probabilistic and explanatory, never identity/fraud proof.
- Raw call audio/transcripts are not persisted by default.

## Review Focus

- A device with no supported remote-call audio source must render intelligence as `UNSUPPORTED`, not a disabled green feature.
- A capability loss during a call must stop analysis cleanly without affecting the call.
- Partial transcripts must be labeled partial in both warnings and summaries.
- A detector timeout/error must yield `Unable to assess`, never a negative/clean verdict.
- Post-response enrichment must never delay or retroactively change the already-issued Android screening response.

---

### Task 1: Capability model and runtime probe

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/ConversationCapability.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/ConversationCapabilityProbe.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/ConversationCapabilityProbeTest.kt`

**Interfaces:**
- Produce: `enum class CapabilityState { AVAILABLE, LIMITED, UNSUPPORTED, PERMISSION_REQUIRED }`.
- Produce: `data class ConversationCapabilities(val conversationalScreening:CapabilityState, val transcription:CapabilityState, val fraudAnalysis:CapabilityState, val syntheticVoiceDetection:CapabilityState, val summary:CapabilityState, val reasonCodes:Set<String>)`.
- Produce: `fun probe(): ConversationCapabilities` using only documented platform/device capability checks.

- [ ] **Step 1: Write failing tests** for unsupported audio source, permission-required state, partial capability, and fully available synthetic test provider.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement probe** with injectable platform adapter; default production adapter must fail closed to unsupported when capability cannot be proven.
- [ ] **Step 4: Run tests**; expect PASS.
- [ ] **Step 5: Commit** `feat(call-security): add conversation capability gate`.

### Task 2: Post-screening caller risk enrichment

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CallRiskEnricher.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt`
- Modify: caller-ID UI file(s) discovered during implementation.
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CallRiskEnricherTest.kt`

**Interfaces:**
- Produce: `data class CallRiskProfile(val level:RiskLevel, val reasons:List<String>, val sources:Set<String>, val assessedAtMs:Long)`.
- Produce: `suspend fun enrich(input: CallRiskInput): CallRiskProfile` executed only after `respondToCall()`.

- [ ] **Step 1: Write failing tests** proving enrichment cannot influence the synchronous screening response and explaining combined local/network-verification/report signals.
- [ ] **Step 2: Run tests** and verify RED.
- [ ] **Step 3: Implement** post-response enrichment using existing local stores/signed feeds and bounded optional remote sources.
- [ ] **Step 4: Render reasons** on caller/in-call surfaces without asserting certainty from country prefix or unknown contact status.
- [ ] **Step 5: Run screening latency/contract tests**; verify existing budget remains intact.
- [ ] **Step 6: Commit** `feat(call-security): enrich caller risk after screening response`.

### Task 3: Conversational screening state machine

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/ConversationalScreeningController.kt`
- Modify: in-call/caller UI only where capability is `AVAILABLE`/`LIMITED`.
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/ConversationalScreeningControllerTest.kt`

**Interfaces:**
- Produce states: `IDLE`, `REQUESTED`, `ACTIVE`, `CALLER_RESPONSE_AVAILABLE`, `FAILED`, `UNSUPPORTED`, `CANCELLED`.
- Produce controller methods: `request(callId:String)`, `cancel()`, `onCapabilityLost(reason:String)`.

- [ ] **Step 1: Write failing tests** for unsupported/no-button state, activation, cancellation, capability loss, and emergency exclusion.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement state machine** against an abstract `ConversationalScreeningTransport`; do not assume implementation transport is available yet.
- [ ] **Step 4: Add UI** only when probe supports it; otherwise show capability explanation in diagnostics/settings, not a fake active control.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** `feat(call-security): add capability-gated conversational screening flow`.

### Task 4: Transcript session boundary

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CallTranscriptSession.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CallTranscriptStore.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CallTranscriptSessionTest.kt`

**Interfaces:**
- Produce: `data class TranscriptSegment(val text:String, val startedAtMs:Long, val endedAtMs:Long?, val speaker:String?, val confidence:Float?)`.
- Produce session lifecycle `start(callId)`, `append(segment)`, `markPartial(reason)`, `finish()`, `discard()`.
- Store defaults to ephemeral memory; persistence requires explicit opt-in flag.

- [ ] **Step 1: Write failing tests** for ephemeral default, explicit persistence opt-in, partial labeling, redaction hook, and discard-on-call-end when not opted in.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement** bounded transcript session/store with no audio capture assumptions.
- [ ] **Step 4: Wire only behind `transcription == AVAILABLE/LIMITED`** and an injected transcription provider.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** `feat(call-security): add privacy-bounded transcript sessions`.

### Task 5: Conversational fraud signal engine

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/ConversationFraudAnalyzer.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/ConversationFraudAnalyzerTest.kt`

**Interfaces:**
- Produce: `data class FraudSignal(val code:String, val severity:Severity, val explanation:String, val evidenceRange:IntRange?)`.
- Produce: `data class FraudAssessment(val level:RiskLevel, val signals:List<FraudSignal>, val transcriptCompleteness:Completeness)`.
- Produce: `fun assess(segments: List<TranscriptSegment>, callerRisk: CallRiskProfile?): FraudAssessment`.

- [ ] **Step 1: Write failing tests** for OTP/password requests, urgent transfer/payment, remote-access installation, account-lock threats, claimed-organization mismatch, benign phrases, and partial transcript.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement deterministic local rules first**; any ML/LLM provider remains optional enrichment behind the same result contract.
- [ ] **Step 4: Add in-call warning UI** that says `Several scam indicators detected` plus reasons, never absolute fraud identity claims.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** `feat(call-security): add explainable conversational fraud signals`.

### Task 6: Synthetic/deepfake voice assessment boundary

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SyntheticVoiceAssessment.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/SyntheticVoiceAssessmentTest.kt`

**Interfaces:**
- Produce: `sealed interface SyntheticVoiceResult { data class Indicators(val score:Float, val explanation:String); data object NoStrongSignal; data object UnableToAssess }`.
- Produce provider interface accepting only validated audio features/input made available by the capability layer.

- [ ] **Step 1: Write failing tests** for unsupported input, detector timeout/error, calibrated positive, calibrated no-strong-signal.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement boundary and result mapping**; production defaults to `UnableToAssess` until a validated detector/provider is configured and physically qualified.
- [ ] **Step 4: Render uncertainty-aware UI copy**.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** `feat(call-security): add synthetic voice assessment boundary`.

### Task 7: Call summary

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/CallSummaryService.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/CallSummaryServiceTest.kt`

**Interfaces:**
- Produce: `data class CallSummary(val text:String, val actionItems:List<String>, val riskSignals:List<String>, val transcriptCompleteness:Completeness)`.
- Produce: `suspend fun summarize(segments:List<TranscriptSegment>, fraud:FraudAssessment?): CallSummary?`.

- [ ] **Step 1: Write failing tests** for no-transcript -> null, partial label propagation, action-item extraction contract, and risk-signal carry-through.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement local/basic summary fallback** or configured provider behind strict privacy policy; do not require cloud for Phone Core operation.
- [ ] **Step 4: Add post-call summary surface** only when a summary exists; preserve ordinary post-call actions otherwise.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** `feat(call-security): add optional post-call summaries`.

### Task 8: Voicemail capability layer

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/VoicemailCapability.kt`
- Modify: dialer UI/settings files discovered during implementation.
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/VoicemailCapabilityTest.kt`

**Interfaces:**
- Produce: `data class VoicemailCapability(val carrierDialAvailable:Boolean, val visualState:CapabilityState, val reasonCodes:Set<String>)`.
- Produce: `fun probe(subscriptionId:Int?): VoicemailCapability`.

- [ ] **Step 1: Write failing tests** for carrier voicemail available, no active SIM, visual voicemail unsupported, permission required, dual-SIM ambiguity.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement documented platform/carrier probe** and baseline voicemail dial action; long-press `1` only when mapping is unambiguous.
- [ ] **Step 4: Add visual voicemail UI only for proven supported state**; otherwise show normal carrier voicemail path.
- [ ] **Step 5: Run tests**; expect PASS.
- [ ] **Step 6: Commit** `feat(phone-core): add truthful voicemail capability handling`.

### Task 9: Security-intelligence qualification

**Files:**
- Add/modify contracts under `security/phone-intelligence/`.
- Modify diagnostics/product-truth scripts and UI capability reporting.
- Extend physical validation documentation/checklist for S24+ audio/OEM capabilities.

- [ ] **Step 1: Add RED contract tests** ensuring no intelligence feature is advertised `AVAILABLE` without capability evidence.
- [ ] **Step 2: Add latency regression test** proving `CallScreeningService` fast path remains independent of intelligence providers.
- [ ] **Step 3: Add tests** for unsupported, limited, permission-required and provider-failure states.
- [ ] **Step 4: Run full Android unit tests, lint, assemble, Instrumentation and Phone Core emulator gates**.
- [ ] **Step 5: Perform S24+ physical probes** for actual call-audio/transcription/voicemail availability; mark anything unproven `PHYSICAL_REQUIRED`/`UNSUPPORTED` rather than PASS.
- [ ] **Step 6: Commit** `test(call-security): qualify capability-gated intelligence`.
