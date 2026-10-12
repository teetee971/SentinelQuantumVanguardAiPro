# Phone Core Journey Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the dialer and in-call user journeys so every core telephone action is reachable, truthful, and testable end to end.

**Architecture:** Keep `SentinelDialerActivity` and `SentinelInCallService` as platform-facing owners, but move small policies/state machines into focused security/UI helpers. In-call secondary modes become mutually exclusive central modes rather than appended scroll content. Contact creation uses Android-supported intents; multi-call uses existing Telecom capabilities and fails closed when absent.

**Tech Stack:** Kotlin, Jetpack Compose, Android Telecom/InCallService, ContactsContract intents, JUnit, existing Node contract tests, emulator API 24/29/36/37.

**Spec:** `docs/superpowers/specs/2026-10-12-phone-core-security-intelligence-design.md`

## Global Constraints

- No hidden/unsupported Telecom APIs.
- Emergency handoff safeguards remain unchanged.
- No control may render as functional if the matching platform capability is absent.
- Physical-only behavior remains `PHYSICAL_REQUIRED`.
- TDD for every behavior change; preserve exact-head CI provenance.

## Review Focus

- A new call must never inherit keypad/audio UI state from the previous call.
- `showDialpad=true` from Android must open the keypad immediately without scrolling.
- Rapid DTMF presses must not allow an earlier stop to cut off a newer tone.
- Contact actions must never write contacts silently or place calls as a side effect.
- Add-call/call-waiting controls must disappear or fail closed when Telecom capability is absent.

---

### Task 1: In-call central mode state

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/InCallUiMode.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/InCallUiModeTest.kt`

**Interfaces:**
- Produces: `enum class InCallUiMode { NONE, KEYPAD, AUDIO, ADD_CALL }`
- Produces: `fun reconcileInCallUiMode(current: InCallUiMode, callId: String?, previousCallId: String?, requestedDialpad: Boolean): InCallUiMode`

- [ ] **Step 1: Write failing tests** for call-id change resetting mode, `requestedDialpad=true` forcing `KEYPAD`, and mutual exclusion of keypad/audio/add-call.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*InCallUiModeTest'` and verify RED.
- [ ] **Step 3: Implement** `InCallUiMode.kt` and replace `showDialpad/showAudioRoutes` booleans with one mode keyed to `snapshot.id`.
- [ ] **Step 4: Refactor layout** so `KEYPAD` and `AUDIO` replace the central secondary content instead of appending below `CallerHero`; keep hangup/actions pinned.
- [ ] **Step 5: Run** focused unit tests and `node scripts/check-android-ui-consistency.js`; expect PASS.
- [ ] **Step 6: Commit** `fix(phone-core): make in-call secondary controls immediately visible`.

### Task 2: Honor Android dialpad foreground requests

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/InCallForegroundRequestTest.kt`

**Interfaces:**
- Produces: `data class ForegroundRequest(val showDialpad: Boolean, val sequence: Long)` exposed via a bounded flow/state holder.

- [ ] **Step 1: Write failing test** proving `onBringToForeground(true)` produces a consumable dialpad request and `false` does not force it open.
- [ ] **Step 2: Run focused test** and verify RED.
- [ ] **Step 3: Implement** foreground request publication in `SentinelInCallService` and one-shot consumption in `SentinelInCallActivity`.
- [ ] **Step 4: Run tests** plus app compile; expect PASS.
- [ ] **Step 5: Commit** `fix(phone-core): honor Telecom dialpad foreground requests`.

### Task 3: Robust DTMF press/release lifecycle

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/DtmfSessionController.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/DtmfSessionControllerTest.kt`

**Interfaces:**
- Consumes: `SentinelInCallService.startDtmf(id, digit)` and `stopDtmf(id)`.
- Produces: `class DtmfSessionController(start: (Char)->Boolean, stop: ()->Boolean)` with `press(digit: Char)`, `release(digit: Char)`, `cancelAll()`.

- [ ] **Step 1: Write failing tests** for start-on-press, guaranteed stop-on-release/cancel, stale-release isolation, invalid digit rejection.
- [ ] **Step 2: Run focused test** and verify RED.
- [ ] **Step 3: Implement** controller with a monotonically increasing press token and guaranteed cleanup.
- [ ] **Step 4: Replace fixed 150 ms click coroutine** in `DtmfPad` with pointer press/release semantics while preserving accessibility click fallback.
- [ ] **Step 5: Run** focused tests, Compose compile, and relevant emulator test contract; expect PASS.
- [ ] **Step 6: Commit** `fix(phone-core): make DTMF press lifecycle robust`.

### Task 4: Contact actions from typed/unknown numbers

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/ContactActionIntentFactory.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/ContactActionIntentFactoryTest.kt`

**Interfaces:**
- Produces: `fun createContactIntent(number: String): Intent?`
- Produces: `fun addToExistingContactIntent(number: String): Intent?`

- [ ] **Step 1: Write failing tests** proving invalid/blank numbers yield null, valid numbers prefill phone data, and no intent has call-placement action.
- [ ] **Step 2: Run focused test** and verify RED.
- [ ] **Step 3: Implement** Android Contacts intents with canonicalized number only.
- [ ] **Step 4: Add UI actions** to dialer number actions and unknown/post-call surfaces: Create contact / Add to existing contact.
- [ ] **Step 5: Run tests** and UI contract checks; expect PASS.
- [ ] **Step 6: Commit** `feat(phone-core): add contact actions for unknown numbers`.

### Task 5: Paste/edit dialer input and T9 suggestions

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/T9ContactMatcher.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/PhoneCoreWorkspace.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/T9ContactMatcherTest.kt`

**Interfaces:**
- Produces: `fun normalizeT9Name(value: String): String`
- Produces: `fun matches(queryDigits: String, displayName: String, numbers: List<String>): Boolean`

- [ ] **Step 1: Write failing tests** for names, accents, numbers, empty query, no-permission behavior delegated by caller.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement** deterministic T9 mapping and use it only when contacts are available.
- [ ] **Step 4: Add a first-class editable number field** above keypad supporting selection/paste; pasted input must use the same sanitize/canonicalize policy and never auto-call.
- [ ] **Step 5: Render contact suggestions** under number input, capped and permission-aware.
- [ ] **Step 6: Run tests/lint/UI contracts**; expect PASS.
- [ ] **Step 7: Commit** `feat(phone-core): add paste editing and T9 contact matching`.

### Task 6: Add-call, call waiting, and conference journey

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/MultiCallUiPolicy.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/MultiCallUiPolicyTest.kt`

**Interfaces:**
- Produces: `data class MultiCallActions(val canAddCall:Boolean, val canSwap:Boolean, val canMerge:Boolean, val canEndIndividually:Boolean)`
- Produces: `fun actions(primary: CallSnapshot, calls: List<CallSnapshot>): MultiCallActions`

- [ ] **Step 1: Write failing policy tests** for single active call, active+holding, ringing second call, generic conference, missing capabilities.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement** policy without inventing capabilities.
- [ ] **Step 4: Add `Ajouter un appel` mode** that holds the active call only after explicit user action and launches a bounded contact/dialer picker path.
- [ ] **Step 5: Add explicit second-call choices** for reject / answer-and-hold where platform state allows; preserve existing merge/swap controls.
- [ ] **Step 6: Extend emulator evidence** to verify controls appear/disappear according to state, without claiming carrier-level multi-call success.
- [ ] **Step 7: Run module tests, lint, assemble, emulator gates**; expect automated gates PASS and physical carrier behavior still `PHYSICAL_REQUIRED`.
- [ ] **Step 8: Commit** `feat(phone-core): complete multi-call user journey`.

### Task 7: Unified unknown-number and post-call action model

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneNumberActionModel.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/security/PhoneNumberActionModelTest.kt`

**Interfaces:**
- Produces: `data class PhoneNumberActions(...)` covering call, SMS, contact create/update, copy, block/unblock, report, details.

- [ ] **Step 1: Write failing tests** for emergency numbers, known contacts, unknown numbers, blocked numbers, missing SMS capability.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement** shared action model and reuse it in dialer/recents/post-call/unknown caller surfaces.
- [ ] **Step 4: Run tests/UI consistency checks**; expect PASS.
- [ ] **Step 5: Commit** `refactor(phone-core): unify number actions across journeys`.

### Task 8: Whole-journey qualification

**Files:**
- Modify: `scripts/phone-core-emulator-flow.sh`
- Modify: `scripts/check-phone-core-emulation-gate.js`
- Add/modify relevant security contract tests under `security/phone-intelligence/`.

- [ ] **Step 1: Add RED contracts** for immediate keypad visibility, state reset across call IDs, contact action intent presence, T9/paste path markers, and capability-gated multi-call controls.
- [ ] **Step 2: Run contract tests** and verify expected failures before wiring each missing assertion.
- [ ] **Step 3: Extend emulator flow** to collect screenshots/UI dumps for keypad/audio modes and ensure hangup remains reachable.
- [ ] **Step 4: Run** app unit tests, lint, assemble, Android Instrumentation, Phone Core API24/29/36/37.
- [ ] **Step 5: Verify exact-head provenance** and keep physical multi-SIM/carrier/Bluetooth items outside automated PASS.
- [ ] **Step 6: Commit** `test(phone-core): qualify completed in-call journeys`.
