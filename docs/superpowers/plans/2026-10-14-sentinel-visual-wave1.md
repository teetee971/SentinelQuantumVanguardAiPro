# Sentinel Visual Wave 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce the reusable D.1 visual foundation and apply it to the Android Home and Protection/analysis surfaces without changing existing security-engine truth or CallScreening behavior.

**Architecture:** Existing Android engines remain authoritative. New presentation models and reusable Compose primitives translate current runtime truth into a premium D.1 interface with bounded motion, glass surfaces, fine particles and holographic depth. Home and security-analysis screens consume those primitives; no new enforcement or malware-detection engine is introduced in this wave.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android API 24+, existing Sentinel D.1 components, existing navigation/runtime services, JUnit 4, Android instrumentation/Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-10-14-sentinel-full-visual-refactor-design.md`

## Global Constraints

- Samsung Galaxy S24+ is the primary physical acceptance device.
- Android API 24 remains the compatibility floor; compileSdk 37 and targetSdk 36 remain unchanged.
- No UI work may add disk/network/remote reputation access before `CallScreeningService.respondToCall()`.
- Do not add permissions solely for visual effects.
- Do not invent threats, scan results, global counts, protection state or green status when evidence is absent.
- Status vocabulary is `OK`, `WARN`, `BAD`, `UNKNOWN`, `LIMITED`, `LOCKED`; color is never the sole indicator.
- Minimum user-facing text remains 12sp; critical actions remain reachable without scrolling.
- Motion must be short, bounded and non-blocking; reduced-motion/system animator scale must remain usable.
- Existing product-truth, Phone Core, security, lint and emulator qualification gates must remain green.

## Review Focus

- **Missing runtime evidence:** Home must render UNKNOWN/LIMITED rather than a fabricated protected state. Covered by Task 3 presentation-state tests.
- **Reduced motion / low-end path:** particle/orbit effects must degrade to static decoration without hiding content or actions. Covered by Task 2 motion-policy tests.
- **Large font / narrow viewport:** Home and Protection must retain readable cards and reachable primary actions. Covered by Task 3 and Task 5 instrumentation tests.
- **Partial scan data:** security-analysis UI must display unavailable/not-executed layers explicitly and never infer a threat count. Covered by Task 4 mapper tests.
- **Navigation/action continuity:** existing Home/Protection actions and test semantics must survive visual replacement. Covered by Task 3 and Task 5 contract tests.

---

## File Structure

### New design primitives

- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelVisualTokens.kt` — D.1 colors, gradients, spacing, radii, glow dimensions and status styling.
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelMotionPolicy.kt` — bounded motion/reduced-motion decisions.
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelVisualEffects.kt` — reusable Canvas-based fine particles, orbit/ring decoration and glow layers; decorative only.
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelGlassComponents.kt` — glass cards, status chips, section headers, action tiles and metric cards.
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelHeroVisual.kt` — reusable hero orb/device-shield visual fed by explicit status, never by invented metrics.

### New presentation state

- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentation.kt` — maps existing Phone Core/VPN/network/collective-defense facts to Home status cards.
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/DeviceAnalysisPresentation.kt` — maps existing audit/app-permission/security facts into scan layer states and findings.

### Existing screens modified

- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SecurityAuditScreen.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/AppPermissionAnalyzerScreen.kt`
- `native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt` only if bottom-nav visuals need shared D.1 tokens; navigation behavior must remain unchanged.

### Tests

- `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelVisualTokensTest.kt`
- `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelMotionPolicyTest.kt`
- `native-android-app/app/src/test/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentationTest.kt`
- `native-android-app/app/src/test/java/com/sentinel/quantum/ui/presentation/DeviceAnalysisPresentationTest.kt`
- `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/HomeVisualContractTest.kt`
- `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/ProtectionVisualContractTest.kt`

---

### Task 1: Lock the D.1 status and visual token contract

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelVisualTokens.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelD1.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelState.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelVisualTokensTest.kt`

**Interfaces:**
- Consumes: existing `SentinelD1` palette and `SentinelState` semantics.
- Produces: `SentinelVisualTokens`, `SentinelStatusVisual`, `SentinelStatus.visual()` used by later tasks.

- [ ] **Step 1: Write the failing token/status tests**

Assert that every `SentinelStatus` maps to a unique semantic label and a visual role; UNKNOWN/LIMITED/LOCKED must not use the confirmed-success role; BAD must use the danger role; token text sizes exposed for body/critical labels are >= 12sp.

- [ ] **Step 2: Run the focused unit test and verify RED**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.design.SentinelVisualTokensTest'`
Expected: FAIL because `SentinelVisualTokens` / `visual()` do not exist.

- [ ] **Step 3: Implement the minimal visual-token contract**

Create `object SentinelVisualTokens` and `data class SentinelStatusVisual(val label: String, val role: VisualRole)` with stable D.1 values. Extend rather than replace existing `SentinelD1`/`SentinelState` consumers.

- [ ] **Step 4: Run focused tests and existing UI-design unit tests**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.design.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "feat(ui): establish D1 visual status tokens"`

---

### Task 2: Add bounded particles, rings and glass primitives

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelMotionPolicy.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelVisualEffects.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelGlassComponents.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelHeroVisual.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelMotionPolicyTest.kt`

**Interfaces:**
- Consumes: `SentinelVisualTokens`, `SentinelStatusVisual` from Task 1.
- Produces: `SentinelHeroVisual(status, title, subtitle, modifier)`, `SentinelGlassCard`, `SentinelStatusChip`, `SentinelMetricCard`, `SentinelActionTile`, `SentinelSectionHeader`, `SentinelMotionPolicy.resolve(animatorScale: Float, powerSave: Boolean): MotionLevel`.

- [ ] **Step 1: Write RED tests for motion degradation**

Test `resolve(0f, false) == STATIC`, `resolve(1f, true) != FULL`, and `resolve(1f, false) == FULL`; assert no policy state hides content.

- [ ] **Step 2: Run the focused motion test and verify RED**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.design.SentinelMotionPolicyTest'`
Expected: FAIL because policy does not exist.

- [ ] **Step 3: Implement motion policy and decorative effects**

Use Compose `Canvas`/graphics primitives only. Particle positions are bounded to a fixed small count; orbit/ring and glow drawing is decorative with `contentDescription = null`. No RenderScript/OpenGL/native dependency and no network asset.

- [ ] **Step 4: Implement reusable glass/status/action components**

All components accept explicit content/status and expose stable semantics/test tags where they are actionable. They must remain usable in STATIC motion mode.

- [ ] **Step 5: Run unit tests and compile Compose sources**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

`git commit -m "feat(ui): add bounded holographic D1 primitives"`

---

### Task 3: Rebuild Home as a truthful premium command center

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentation.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt` only for shared bottom-nav tokens if required.
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentationTest.kt`
- Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/HomeVisualContractTest.kt`

**Interfaces:**
- Consumes: existing runtime facts already used by Home/PhoneSecurity plus Task 1/2 design primitives.
- Produces: `HomeSecurityPresentation.from(facts: HomeRuntimeFacts): HomeSecurityUiState` and redesigned `HomeScreen` with unchanged navigation callbacks.

- [ ] **Step 1: Write RED mapper tests**

Cover all-ready, missing evidence, backend unavailable, role revoked and mixed/degraded facts. Assert missing evidence maps to UNKNOWN/LIMITED, never OK; overall state is derived from component truth and is not a fabricated percentage.

- [ ] **Step 2: Run focused mapper test and verify RED**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.presentation.HomeSecurityPresentationTest'`
Expected: FAIL because mapper/state do not exist.

- [ ] **Step 3: Implement `HomeRuntimeFacts` and `HomeSecurityUiState` mapper**

Keep data acquisition in existing sources. The mapper only translates facts into hero state, four primary module cards, priority warning, recent-event summary and quick-action availability.

- [ ] **Step 4: Replace Home layout with the D.1 command-center hierarchy**

Implement hero protection orb/device visual, Phone Core/VPN/Network/Collective Defense cards, alerts, activity summary and quick actions. Use real states only; if an existing data source cannot supply a metric, omit it or render unavailable rather than displaying concept-art numbers.

- [ ] **Step 5: Write/extend instrumentation contract**

Assert the Home hero, four module cards and primary quick actions exist; test UNKNOWN copy for absent facts; set large font scale/narrow viewport and verify primary actions remain discoverable through semantics/scroll without clipping assertions.

- [ ] **Step 6: Run Home tests**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
Expected: PASS on supported emulator lane.

- [ ] **Step 7: Commit**

`git commit -m "feat(home): introduce D1 security command center"`

---

### Task 4: Build truthful device-analysis presentation state

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/DeviceAnalysisPresentation.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SecurityAuditScreen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/AppPermissionAnalyzerScreen.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/presentation/DeviceAnalysisPresentationTest.kt`

**Interfaces:**
- Consumes: existing security-audit/app-permission results only.
- Produces: `DeviceAnalysisPresentation.from(facts: DeviceAnalysisFacts): DeviceAnalysisUiState`, with `AnalysisLayerState` values `COMPLETE`, `RUNNING`, `NOT_EXECUTED`, `UNAVAILABLE`, `FAILED`.

- [ ] **Step 1: Write RED tests for partial/unknown scan states**

Assert a missing layer becomes `NOT_EXECUTED` or `UNAVAILABLE`; no threat is synthesized from permission count alone; overall `OK` requires explicit healthy evidence from every required implemented layer.

- [ ] **Step 2: Run mapper test and verify RED**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.presentation.DeviceAnalysisPresentationTest'`
Expected: FAIL because presentation mapper does not exist.

- [ ] **Step 3: Implement the minimal analysis mapper**

Map only data the current engines already expose. Concept-art features such as signature database counts, zero-day percentages, worldwide detections or active malware isolation remain absent unless a real existing source supplies them.

- [ ] **Step 4: Refactor SecurityAudit/AppPermissionAnalyzer to consume the state**

Expose layers such as applications/permissions/system checks using the existing evidence. Use holographic device layers as decoration, not as claims about scans that did not run.

- [ ] **Step 5: Run mapper and screen unit/compile checks**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

`git commit -m "feat(protection): add truthful device analysis presentation"`

---

### Task 5: Apply premium Protection visual hierarchy and preserve actions

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SecurityAuditScreen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/AppPermissionAnalyzerScreen.kt`
- Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/ProtectionVisualContractTest.kt`

**Interfaces:**
- Consumes: `DeviceAnalysisUiState`, Task 1/2 primitives and existing navigation callbacks.
- Produces: premium Protection landing/analysis surfaces with the same functional actions as before.

- [ ] **Step 1: Write/extend RED instrumentation contract**

Pin critical semantics: protection hero, analysis action, app-permission action, network/VPN navigation, unknown/limited state text, and no inaccessible primary action at large font scale.

- [ ] **Step 2: Run contract test and verify RED**

Run: `cd native-android-app && ./gradlew :app:connectedDebugAndroidTest`
Expected: FAIL on new D.1 tags/hierarchy before implementation.

- [ ] **Step 3: Implement Protection hero and analysis cards**

Use fine particles/rings in bounded decorative regions; preserve existing action callbacks and actual status truth. Avoid showing quarantine/remove buttons unless an existing implemented action genuinely supports them.

- [ ] **Step 4: Verify accessibility and motion fallback**

Run instrumentation with normal and increased font scale where the existing test harness permits; inspect semantics tree for primary actions and state labels.

- [ ] **Step 5: Run full Android unit/lint/assemble gate**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: PASS.

- [ ] **Step 6: Commit**

`git commit -m "feat(protection): deliver premium D1 protection surfaces"`

---

### Task 6: Exact-head regression and physical visual review package

**Files:**
- Modify only if required by existing test/qualification documentation; no feature code in this task.
- Evidence: existing GitHub Actions workflows and S24+ validation process.

**Interfaces:**
- Consumes: completed Wave 1 branch HEAD.
- Produces: exact-head CI evidence plus a physical visual-review checklist for S24+.

- [ ] **Step 1: Run repository Android product-truth/security checks**

Run repository-defined `test:android-product-truth`, security governance and Android manifest gates.
Expected: PASS.

- [ ] **Step 2: Run Android unit/lint/build checks on the exact HEAD**

Run: `cd native-android-app && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: PASS.

- [ ] **Step 3: Require existing emulator qualification matrix**

API 24/29/36/37 workflows must complete with their structured truth preserved; workflow green is not permission to rewrite a `LIMITED` qualification as `PASS`.

- [ ] **Step 4: Perform S24+ visual acceptance**

Check Home and Protection for clipping, status truth, motion smoothness, reduced-motion/static fallback, touch targets, dark contrast, thermal/battery regressions during the visual effects, and stable navigation. This is visual/product evidence only and does not replace the canonical Phone Core physical criteria.

- [ ] **Step 5: Final review/commit only if documentation evidence changed**

Do not add a cosmetic commit merely to mark completion.

---

## Wave 1 Exit Criteria

- D.1 visual primitives are reusable and do not encode domain truth themselves.
- Home is visibly transformed into the approved premium command-center direction.
- Protection/security-analysis surfaces use 3D-like layered Compose visuals, fine particles and holographic depth without pretending to perform scans the engines do not implement.
- Existing navigation/actions remain functional.
- UNKNOWN/LIMITED/LOCKED remain visually and semantically distinct from OK.
- Static/reduced-motion fallback preserves all content and actions.
- Unit, lint, assemble, product-truth and relevant emulator gates are exact-head green/qualified.
- S24+ visual acceptance is recorded before Wave 1 is called physically accepted.
