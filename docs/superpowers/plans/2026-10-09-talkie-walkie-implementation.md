# Sentinel Talkie-Walkie Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade Android push-to-talk module for Sentinel with fail-closed microphone control, LiveKit transport, truthful futuristic UI, deterministic qualification, and the approved V1 differentiators.

**Architecture:** Add a dedicated `com.sentinel.quantum.talkiewalkie` subsystem. UI intent flows through `TalkieWalkieViewModel` into a single `TalkieWalkieSessionController`; only the controller/guard path may request microphone activation. LiveKit is reused through a new PTT-specific adapter that connects muted and remains semantically separate from `SentinelLiveKitCallTransport`.

**Tech Stack:** Kotlin/JVM 17, Android minSdk 24 / targetSdk 36 / compileSdk 37, Jetpack Compose, coroutines, LiveKit Android 2.29.0, JUnit 4, AndroidX instrumentation / Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-10-09-talkie-walkie-design.md` plus `docs/superpowers/specs/2026-10-09-talkie-walkie-addendum.md`

## Global Constraints

- Microphone must be OFF outside authoritative `TRANSMITTING` state.
- Local release/failure mute must not wait for backend acknowledgement.
- Reconnect must return to `LISTENING`; never auto-resume transmission.
- LiveKit credentials must be short-lived, channel-scoped, `wss://`, and never persisted/logged.
- Existing classic VoIP semantics must remain unchanged.
- Visual effects consume derived state only and cannot control microphone state.
- Emulator qualification targets API 24/29/36/37 where platform support permits.
- Physical validation remains mandatory before `READY`.
- Approved V1 additions: whisper boundary, temporary channels, real network quality, Bluetooth/physical PTT trigger, and <=20 s volatile receive replay.

## Review Focus

- Release event arriving after transport/floor failure: local mic must already be muted and state must converge safely.
- Bluetooth trigger disconnect during transmit: must force the same release path as UI release.
- Expired temporary channel while listening/requesting floor: no new transmit, credentials/floor rejected.
- Reconnect after prior transmit: no stale press/floor state may reactivate mic.
- Replay buffer under secure-policy/channel switch: memory must be cleared and no local mic audio retained.

---

### Task 1: Pure PTT state machine

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieState.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieStateMachine.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieStateMachineTest.kt`

**Interfaces:**
- Produces: `enum class TalkieWalkieState { DISCONNECTED, CONNECTING, LISTENING, REQUESTING_FLOOR, TRANSMITTING, RECEIVING, RECONNECTING, FAILED }`
- Produces: `fun transition(current: TalkieWalkieState, event: TalkieWalkieEvent): TalkieWalkieState`

- [ ] Write failing tests for legal path `DISCONNECTED -> CONNECTING -> LISTENING -> REQUESTING_FLOOR -> TRANSMITTING -> LISTENING` and illegal direct transitions such as `LISTENING -> TRANSMITTING`.
- [ ] Run `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests '*TalkieWalkieStateMachineTest'`; expect RED because production types do not exist.
- [ ] Implement the minimal enum/events/state machine; ensure reconnect/failure from transmit never returns directly to transmit.
- [ ] Re-run the targeted unit test; expect PASS.
- [ ] Commit: `feat(ptt): add fail-closed state machine`.

### Task 2: Floor lease, channel policy, and transmit guard

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/FloorLease.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/ChannelPolicy.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/PushToTalkGuard.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/PushToTalkGuardTest.kt`

**Interfaces:**
- Produces: `data class FloorLease(val id: String, val holderSessionId: String, val expiresAtMs: Long)` with `fun isValid(nowMs: Long, sessionId: String): Boolean`.
- Produces: `data class ChannelPolicy(val expiresAtMs: Long?, val canTalk: Boolean, val secureReplayAllowed: Boolean)`.
- Produces: `data class GuardSnapshot(...)` and `fun canTransmit(snapshot: GuardSnapshot): GuardDecision`.

- [ ] Write failing tests proving expired/wrong-holder floor, expired channel, missing permission, disconnected transport, reconnect state, or invalid audio focus all deny transmit.
- [ ] Add review-focus test: a temporary channel expiring between listening and floor request must deny transmit.
- [ ] Run targeted test; expect RED.
- [ ] Implement the minimal domain models and guard; no Android framework dependency in this task.
- [ ] Re-run targeted tests; expect PASS.
- [ ] Commit: `feat(ptt): add floor and transmit guard`.

### Task 3: PTT transport abstraction and LiveKit adapter

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieTransport.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/LiveKitTalkieWalkieTransport.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/LiveKitTalkieWalkieTransportContractTest.kt`
- Reference only: `native-android-app/app/src/main/java/com/sentinel/quantum/voice/SentinelLiveKitCallTransport.kt`

**Interfaces:**
- Produces: `suspend fun connect(credentials: Credentials): Result<Unit>` where success leaves microphone muted.
- Produces: `suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit>`.
- Produces: `suspend fun disconnect()` and observable connection/participant/active-speaker/network-metric state.

- [ ] Write failing contract tests asserting connect never unmutes, invalid non-`wss://` credentials fail, disconnect mutes before disposal, and reconnect callbacks cannot re-enable mic.
- [ ] Run targeted test; expect RED.
- [ ] Implement the interface and LiveKit adapter using existing LiveKit 2.29.0 dependency; factor credential validation only if it does not alter classic VoIP behavior.
- [ ] Re-run transport tests plus `SentinelLiveKitCredentialsTest` and existing voice unit tests; expect PASS and no VoIP regression.
- [ ] Commit: `feat(ptt): add muted LiveKit transport`.

### Task 4: Session controller, watchdog, and ordered release

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieSessionController.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/PttWatchdog.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/FloorController.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieSessionControllerTest.kt`

**Interfaces:**
- Consumes Tasks 1-3.
- Produces: `suspend fun pressToTalk()`, `suspend fun releaseToTalk()`, `suspend fun onTransportLost()`, `suspend fun onTriggerLost()`, `fun snapshot(): TalkieWalkieSessionSnapshot`.

- [ ] Write failing tests using fakes that record call order. Assert successful press requests floor before unmute and enters `TRANSMITTING` only after both succeed.
- [ ] Add failing test proving `releaseToTalk()` calls local mute before floor-renewal stop/release and before UI state returns to `LISTENING`.
- [ ] Add review-focus tests: transport failure and trigger loss while transmitting force mute exactly once; reconnect never auto-transmits.
- [ ] Run targeted tests; expect RED.
- [ ] Implement controller/watchdog/floor coordination with a mutex/serialized coroutine boundary so press/release/failure races are deterministic.
- [ ] Re-run targeted tests; expect PASS.
- [ ] Commit: `feat(ptt): enforce ordered session control`.

### Task 5: Network quality, Bluetooth trigger, and volatile replay

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/NetworkQualityPolicy.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/PttTrigger.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/RecentReceiveBuffer.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/NetworkQualityPolicyTest.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/RecentReceiveBufferTest.kt`
- Android Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/talkiewalkie/PttTriggerInstrumentationTest.kt`

**Interfaces:**
- Produces: `enum class NetworkQuality { EXCELLENT, GOOD, DEGRADED, UNUSABLE }` from latency/jitter/loss/reconnect metrics.
- Produces: trigger events `PRESS`, `RELEASE`, `DISCONNECTED` routed into the existing session controller.
- Produces: memory-only receive buffer capped at 20 seconds; `clear()` on leave/policy change/teardown.

- [ ] Write RED unit tests for deterministic quality thresholds and anti-flap behavior.
- [ ] Write RED replay tests proving >20 s evicts oldest receive frames, local mic frames are not accepted, and secure-policy/channel changes clear memory.
- [ ] Write RED instrumentation contract for physical/Bluetooth trigger disconnect routing to `onTriggerLost()` rather than direct mic control.
- [ ] Implement minimal policies/adapters and volatile buffer; keep replay disabled by default at UI level.
- [ ] Run unit tests and API-supported instrumentation; expect PASS.
- [ ] Commit: `feat(ptt): add quality trigger and replay primitives`.

### Task 6: ViewModel and truthful functional Compose screen

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieUiState.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieViewModel.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieScreen.kt`
- Android Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieScreenTest.kt`

**Interfaces:**
- Consumes session snapshot, network quality, participant/active-speaker data.
- Produces immutable `TalkieWalkieUiState`; UI callbacks only call ViewModel intent methods.

- [ ] Write RED Compose tests asserting `LISTENING` shows `Maintenir pour parler`, `REQUESTING_FLOOR` does not display transmit truth, and only authoritative `TRANSMITTING` shows `Vous parlez`.
- [ ] Add RED test proving press/release semantics reach ViewModel/controller intents and no composable owns a transport reference.
- [ ] Implement minimal functional cockpit: top status, central PTT core, active-speaker area, network-quality indicator, audio-route control placeholder.
- [ ] Run Compose instrumentation tests; expect PASS.
- [ ] Commit: `feat(ptt): add truthful functional cockpit`.

### Task 7: Android lifecycle, foreground session, audio route, and physical-trigger safety

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieSessionService.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/AudioRouteController.kt`
- Modify: `native-android-app/app/src/main/AndroidManifest.xml`
- Android Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieLifecycleInstrumentationTest.kt`

**Interfaces:**
- Service may preserve explicit listening session but never preserve transmission independently of the controller.
- Audio-route loss/focus loss events feed controller failure/release APIs.

- [ ] Write RED instrumentation tests for backgrounding/teardown/focus loss while transmitting: controller must force mute; service restart/reconnect must return to listening.
- [ ] Add RED test for Bluetooth route/trigger disappearance while transmitting.
- [ ] Implement foreground-service and audio-route integration with visible notification and no silent mic startup.
- [ ] Run API 24/29/36/37 instrumentation where supported; document platform skips explicitly rather than false-green them.
- [ ] Commit: `feat(ptt): harden Android lifecycle and audio route`.

### Task 8: Futuristic visual engine and adaptive performance

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieVisualEngine.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieScreen.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieVisualEngineTest.kt`
- Android Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieVisualStateTest.kt`

**Interfaces:**
- Input: authoritative PTT state, local/remote amplitude, network quality, active speaker, performance tier.
- Output: render-only values for halo intensity, particle density/velocity, ring state, waveform mode, parallax depth.

- [ ] Write RED tests proving render state is a pure mapping and exposes no microphone/transport action.
- [ ] Implement `FULL`, `REDUCED`, `MINIMAL` visual tiers; map thermal/frame-budget degradation without changing PTT state.
- [ ] Add Compose visual-state assertions for idle/listening/requesting/transmitting/receiving/reconnecting/failed semantics.
- [ ] Verify effects remain decorative: disable the visual engine and prove all PTT controller tests still pass.
- [ ] Commit: `feat(ptt): add adaptive futuristic visual engine`.

### Task 9: Whisper and temporary-channel application boundaries

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TransmitAudience.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieSessionController.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/talkiewalkie/TalkieWalkieUiState.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/talkiewalkie/TransmitAudienceTest.kt`

**Interfaces:**
- Produces: `sealed interface TransmitAudience { data object Channel; data class Whisper(val memberIds: Set<String>) }`.
- Server/backend remains authoritative for membership and target authorization.

- [ ] Write RED tests proving empty whisper target is rejected, whisper never silently broadens to channel, and expired temporary channel prevents floor acquisition.
- [ ] Implement domain/controller boundary without inventing server authorization success locally.
- [ ] Add UI truth: explicit visual distinction when whisper is selected.
- [ ] Run targeted unit/Compose tests; expect PASS.
- [ ] Commit: `feat(ptt): add whisper and temporary channel boundaries`.

### Task 10: Qualification gates and anti-false-green evidence

**Files:**
- Create: `scripts/talkie-walkie-runtime-contract.test.js`
- Create: `.github/workflows/talkie-walkie-qualification.yml`
- Modify: `scripts/check-android-product-truth.js`
- Modify if production policy requires: `scripts/production-merge-gate.js`

**Interfaces:**
- CI must prove exact-head unit tests, compile/lint, supported emulator instrumentation, contract checks, and artifacts with SHA/API provenance.

- [ ] Write RED runtime contract that requires state machine, muted-on-connect transport, ordered release, reconnect fail-closed behavior, no UI transport ownership, and tests for approved differentiators.
- [ ] Run the Node contract locally/CI; expect RED before wiring.
- [ ] Add dedicated workflow using existing API 24/29/36/37 emulator/evidence conventions; do not claim physical audio proof.
- [ ] Run host truth + dedicated PTT gate + Android instrumentation on exact HEAD; require all deterministic lanes green.
- [ ] Produce a physical validation checklist for two devices including S24+, Wi-Fi/mobile handoff, Bluetooth PTT/audio, screen lock, real speech-start latency, release latency, whisper, temporary expiry, and replay clearing.
- [ ] Commit: `ci(ptt): add qualification and truth gates`.

## Self-review results

- Spec coverage: core state/floor/transport/controller/UI/lifecycle/security/visuals/qualification are assigned; accepted differentiators are assigned to Tasks 5 and 9.
- Type consistency: controller is the sole intent/mic authority throughout; UI/trigger/service feed it rather than bypass it.
- Review Focus coverage: each listed race/failure has an owning task and explicit test.
- Scope: backend token issuance/floor service implementation is intentionally not fabricated inside Android; Android contracts/interfaces are built first and real multi-client backend work follows once the existing backend deployment boundary is inspected.
- Production truth: emulator green cannot set `READY`; physical two-device validation remains mandatory.
