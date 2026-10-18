# Sentinel Quantum Vanguard AI Pro — D.1 Total Android Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing native Android presentation layer into a coherent, truthful, premium D.1 security command center while preserving every existing Android, Telecom, SMS/MMS, VPN, voice and intelligence boundary.

**Architecture:** Keep existing domain engines and platform services authoritative. Add a small Compose presentation layer that maps runtime facts into stable Sentinel states, then render every destination and standalone activity through shared D.1 primitives. The 3D effect is a lightweight Compose Canvas/overlay component with a deterministic 2D/reduced-motion path; it is decorative only and never manufactures a security result.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Canvas, Android Navigation Compose, existing Android Telecom/SMS/MMS/VPN/LiveKit boundaries, JUnit 4, Compose UI tests, Android instrumentation tests, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-10-14-sentinel-full-visual-refactor-design.md`, extended by the user's D.1 total-redesign command in the task brief.

## Global Constraints

- The Android native application is the product being changed; web/PWA files are out of scope.
- D.1 uses a near-black blue background, translucent graphite surfaces, restrained cyan/blue accents, and explicit green/amber/red/gray/purple semantics.
- `OK`, `WARN`, `BAD`, `UNKNOWN`, `LIMITED`, `LOCKED`, `TEST`, and `PURP` must keep stable meanings across screens; color is never the only status signal.
- Green is reserved for confirmed healthy evidence; no screen may claim total protection, antivirus certification, active VPN, connected device or READY from a click alone.
- Existing domain engines remain authoritative; UI work must not change CallScreening ordering, emergency-call behavior, SMS/MMS transport, VPN activation truth or voice capability boundaries.
- The Phone Core first-run lifecycle remains `NOT_STARTED → OFFERED → IN_PROGRESS → DEFERRED → COMPLETED`, and `COMPLETED` is only promoted from current Android facts.
- Android API 24 compatibility, compile/target settings, Samsung Galaxy S24+ portrait usability, system navigation, large font scaling, TalkBack and reduced-motion preferences remain required.
- No new permission or network lookup may be added only for visual polish.
- Every major lot must run the focused unit/UI tests and a build/lint check before its commit.
- This environment has no `adb`, emulator or Android SDK discovery; no screenshot may be presented as device evidence until a real Android runtime is available.

## Review Focus

- **Unknown or stale evidence:** a missing provider/backend/permission must render `UNKNOWN`, `LIMITED` or `LOCKED`, never a green success; cover with presentation mapper tests for each module.
- **Reduced motion and low capability:** core rings and particles must become static 2D decoration without hiding labels/actions; cover with Compose tests for `STATIC`, `REDUCED` and large font.
- **Critical communication actions:** answer/reject/hang-up/dial/send controls must remain reachable and never wait for animation; cover standalone activity smoke/semantics tests.
- **False topology or VPN claims:** detected devices and gateway metadata must be labelled observed/unknown and tunnel state must come from runtime truth; cover Network/VPN mapper tests.
- **Partial backend/errors:** `VOICE_SESSION_HTTP_503`, permission denial and provider failures must remain actionable and comprehensible; cover voice/error state tests.

## File Map and Boundaries

### Shared presentation and visual system

- Modify `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelD1.kt`, `SentinelVisualTokens.kt`, `SentinelState.kt`, `SentinelGlassComponents.kt`, `SentinelChrome.kt`, `SentinelVisualEffects.kt` and `ui/theme/Color.kt`/`Type.kt` to establish one token source and stable status semantics.
- Create `ui/design/SentinelQuantumCore.kt` for the reusable ring/convergence/data-node renderer and its accessible 2D fallback.
- Create `ui/design/SentinelEvidenceGraph.kt` for evidence nodes, provenance/freshness labels and proof-linked edges.
- Create `ui/design/SentinelStates.kt` or extend the existing component file for loading, empty, error, status, threat and action states.

### Application shell

- Modify `MainActivity.kt`, `navigation/Screen.kt`, `navigation/NavGraph.kt` and `ui/design/SentinelChrome.kt` to use the shared command-center backdrop, top bar and bottom navigation while preserving all routes and deep links.

### In-app destinations

- Refactor `ui/screens/HomeScreen.kt`, `PhoneSecurityScreen.kt`, `CommunicationsHubScreen.kt`, `NetworkSurveillanceScreen.kt`, `VpnScreen.kt`, `DigitalExposureScreen.kt`, `SecurityAuditScreen.kt`, `SystemDoctorScreen.kt`, `CollectiveDefenseScreen.kt`, `SettingsScreen.kt`, `SmsScannerScreen.kt`, `AppPermissionAnalyzerScreen.kt`, `SmartHomeScreen.kt`, `OsintFeedScreen.kt`, `OsintDetailScreen.kt`, `EmailSecurityScreen.kt`, `CallBlockingScreen.kt`, `CallFilterHistoryScreen.kt`, `PhoneProtectionListsScreen.kt`, `NumberSearchScreen.kt`, `LocalLogsScreen.kt`, `AboutScreen.kt` and `ComplianceScreen.kt` in focused groups. Each screen retains its current domain calls and receives only presentation models/components.

### Standalone Android activities

- Refactor the Compose UI portions of `SentinelDialerActivity.kt`, `SentinelInCallActivity.kt`, `CallerIdActivity.kt`, `SmsComposeActivity.kt`, `PhoneCoreActivationActivity.kt`, `PhoneCoreDiagnosticActivity.kt` and `VoiceStudioActivity.kt` without changing their platform contracts or service callbacks.

### Verification artifacts

- Add/extend focused unit tests under `native-android-app/app/src/test/java/com/sentinel/quantum/ui/` for state mappers, visual tokens, evidence freshness and voice errors.
- Add/extend Compose/instrumentation tests under `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/` for all static destinations, core semantics, large-font layout, navigation, standalone activity smoke and critical actions.
- Save real screenshots and a per-screen evidence manifest under `docs/evidence/sentinel-d1/` only when an Android runtime can produce them; otherwise record named blockers.

### Task 1: Lock D.1 tokens, state vocabulary and shared primitives

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelD1.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelVisualTokens.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelState.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/theme/Color.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/theme/Type.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelGlassComponents.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelQuantumCore.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelEvidenceGraph.kt`
- Create/modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelStates.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelVisualTokensTest.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelQuantumCoreModelTest.kt`

**Interfaces:**
- Produce `SentinelCoreModule(id: String, label: String, status: SentinelStatus, icon: ImageVector, enabled: Boolean)`, `SentinelQuantumCore(modules, modifier, motionLevel, onModuleClick)`, and `SentinelEvidenceGraph(nodes, edges, modifier, onNodeClick)`.
- Produce shared `SentinelLoadingState`, `SentinelEmptyState`, `SentinelErrorState`, `SentinelThreatIndicator` and `SentinelActionButton` variants with semantics and minimum 48dp touch targets.
- Keep existing call sites source-compatible where possible; map legacy `SentinelState` into the expanded visual vocabulary rather than duplicating state enums.

- [ ] **Step 1: Write failing unit tests** for every status role/label, token palette, `UNKNOWN`/`LIMITED`/`LOCKED` non-success mapping, core module ordering and evidence edges that require a proof reference.
- [ ] **Step 2: Run focused tests and verify RED** with `cd native-android-app && ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.design.*'`.
- [ ] **Step 3: Implement the token contract and primitives**. Draw rings, convergence lines, status-tinted data points and central protection nucleus with Compose Canvas; place accessible textual module controls over/under the decorative canvas. Use `MotionLevel.STATIC` for reduced motion/power-save and never read security truth from animation progress.
- [ ] **Step 4: Run focused tests and verify PASS**, then run `./gradlew :app:lintDebug`.
- [ ] **Step 5: Commit** `feat(android): establish sentinel d1 quantum core primitives`.

### Task 2: Unify application shell and navigation

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/navigation/Screen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/navigation/NavGraph.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelChrome.kt`
- Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/AllStaticNavigationSurfacesInstrumentationTest.kt`
- Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/SentinelNavigationSemanticsTest.kt`

**Interfaces:**
- Preserve every existing `Screen` route and standalone intent. Add a route only when an existing standalone capability needs an in-app entry point, and route it to the same activity/service boundary.
- Produce `SentinelBottomNavigation` with four stable destinations: Accueil, Échanges, Protection and Plus.

- [ ] **Step 1: Add failing shell tests** asserting all existing routes compose, four primary destinations expose French labels and back navigation remains available on child destinations.
- [ ] **Step 2: Run the focused instrumentation/Compose tests to establish the current failure or baseline**.
- [ ] **Step 3: Replace the raw Material navigation bar/top bar with D.1 chrome** using centralized colors, selected-state semantics, safe-area padding, overflow labels and a non-blocking backdrop.
- [ ] **Step 4: Run all static navigation smoke tests and lint**; verify no route or deep-link is removed.
- [ ] **Step 5: Commit** `feat(android): unify sentinel command center shell`.

### Task 3: Rebuild Home as the truthful 3D Command Center

**Files:**
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentation.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelHeroVisual.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentationTest.kt`
- Test: `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/HomeVisualContractTest.kt`

**Interfaces:**
- `HomeSecurityPresentation.from(HomeRuntimeFacts)` remains the only source of Home status; extend its module model with evidence freshness, next action and `SentinelCoreModule` mapping.
- Keep existing test tags `home_primary_action`, `home_security_hero`, `home_activity_summary` and `home_module_*` or provide compatibility aliases.

- [ ] **Step 1: Add mapper tests** for all combinations of Phone Core, network, VPN and community facts, including unknown/limited/locked and no fabricated threat counts.
- [ ] **Step 2: Run mapper and Home contract tests to verify the new assertions fail or expose the old hero gap**.
- [ ] **Step 3: Replace the text-heavy hero with `SentinelQuantumCore`** plus concise summary, recent real events, recommended action, module status chips and configuration-incomplete indicators. Keep search/access shortcuts, actual navigation and Phone Core activation launch wired.
- [ ] **Step 4: Run Home tests at 320dp width and 2x font scale, reduced motion and default motion; run lint**.
- [ ] **Step 5: Commit** `feat(android): build truthful sentinel command center`.

### Task 4: Harmonize Protection, Network Center, VPN, Exposure and Audit

**Files:**
- Modify: `PhoneSecurityScreen.kt`, `NetworkSurveillanceScreen.kt`, `VpnScreen.kt`, `DigitalExposureScreen.kt`, `SecurityAuditScreen.kt`, `SystemDoctorScreen.kt`, `AppPermissionAnalyzerScreen.kt`, `SmartHomeScreen.kt`, `CollectiveDefenseScreen.kt`
- Modify: `NetworkVpnPresentation.kt`, `ui/presentation/DeviceAnalysisPresentation.kt`, `ProtectionOverviewPresentation.kt`, `SecurityAuditPermissionPresentation.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/NetworkTopologyPresentation.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/VpnTruthPresentation.kt`
- Test: matching `ui/presentation/*Test.kt` and `ui/*VisualContractTest.kt`

**Interfaces:**
- Map observed devices to `OBSERVÉ`, `PROBABLE` or `INCONNU`; never map detection to connected.
- Map VPN to disconnected/consent required/connecting/protected/degraded/failed/gateway unavailable, using `SentinelVpnController` runtime facts only.
- Map digital exposure and audit links to evidence nodes with source/freshness/provenance labels; unavailable checks are explicit.

- [ ] **Step 1: Write failing mapper tests** for stale Wi-Fi, permission-blocked Bluetooth, unknown topology, disconnected VPN, unavailable gateway, missing audit layers and no-proof exposure edges.
- [ ] **Step 2: Run focused mapper tests to verify RED**.
- [ ] **Step 3: Render the screens with the shared glass cards, Quantum Core/Network graph, evidence graph, threat indicators, loading/empty/error states and action buttons; keep all existing runtime calls and system-settings handoffs.
- [ ] **Step 4: Run static navigation, protection visual contracts, unit tests and lint; inspect for horizontal overflow at large font**.
- [ ] **Step 5: Commit** `feat(android): unify protection truth surfaces with d1 visualizations`.

### Task 5: Refactor Communications and Phone Core activities

**Files:**
- Modify: `SentinelDialerActivity.kt`
- Modify: `SentinelInCallActivity.kt`
- Modify: `CallerIdActivity.kt`
- Modify: `SmsComposeActivity.kt`
- Modify: `CommunicationsHubScreen.kt`, `NumberSearchScreen.kt`, `SmsScannerScreen.kt`, `CallBlockingScreen.kt`, `CallFilterHistoryScreen.kt`, `PhoneProtectionListsScreen.kt`
- Create if needed: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/phone/PhoneCorePresentation.kt` and focused D.1 phone components
- Test: existing phone/Telecom/SMS contracts plus new Compose semantics tests

**Interfaces:**
- Keep `placeCallIfReady`, emergency guards, call screening, `SmsSubmitReadiness`, MMS callbacks, role/permission launchers and existing contact lookup authoritative.
- Expose a `PhoneCorePresentation` mapping normalized number, identity, country/operator evidence, reputation, freshness and allowed actions without asserting uncertain facts.

- [ ] **Step 1: Add failing tests** for call button gating, emergency call bypass, contact/number provenance, unknown reputation, SMS send readiness, delivery failure and large-font dialpad semantics.
- [ ] **Step 2: Run existing Phone Core/Telecom/SMS unit and instrumentation contracts to record baseline failures and preserve their boundaries**.
- [ ] **Step 3: Replace legacy Card/Material-only surfaces with the D.1 Phone shell**: premium dialpad with moderate haptics, searchable contacts/recent history, risk evidence card, caller identity hero, fixed in-call controls, message bubbles/composer and explicit provider/role states. Do not move any remote enrichment before `respondToCall()` or expose emergency-blocking affordances.
- [ ] **Step 4: Run phone-core, Telecom, SMS/MMS and UI contract suites, lint and a debug build**.
- [ ] **Step 5: Commit** `feat(android): redesign communications and phone core surfaces`.

### Task 6: Refactor Voice Studio, onboarding and diagnostics

**Files:**
- Modify: `VoiceStudioActivity.kt`
- Modify: `PhoneCoreActivationActivity.kt`
- Modify: `PhoneCoreDiagnosticActivity.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/presentation/VoiceStudioPresentation.kt`
- Create/modify: tests for voice lifecycle, first-run lifecycle and diagnostic semantics

**Interfaces:**
- Preserve `VoiceSessionCoordinator`, Auth0, LiveKit, local recording/preview and explicit no-PSTN policy.
- Map HTTP 503 and session errors to a retryable `SentinelStatus.BAD`/`LIMITED` state with user copy, not a fake connected/ready state.
- Keep lifecycle persistence and Android facts authoritative; a click never marks a prerequisite complete.

- [ ] **Step 1: Add failing tests** for `VOICE_SESSION_HTTP_503`, microphone denial, session disconnect, no PSTN path, seven setup steps, defer/resume and false READY prevention.
- [ ] **Step 2: Run voice/setup/diagnostic contract tests to verify RED or identify exact existing baseline**.
- [ ] **Step 3: Render voice recording, preview, effects, waveform, PTT test session, network/server indicators, retry action and explicit PSTN unavailable notice through D.1 components. Render activation as a seven-step progress rail with actual Android fact states. Render diagnostics as evidence cards, not a green certification shield.
- [ ] **Step 4: Run standalone activity smoke tests, voice/setup/diagnostic unit tests and lint**.
- [ ] **Step 5: Commit** `feat(android): redesign voice setup and diagnostics truth surfaces`.

### Task 7: Complete secondary surfaces, accessibility and French copy

**Files:**
- Modify remaining `ui/screens/*.kt`, `SettingsScreen.kt`, `MainActivity.kt`, `res/values/strings.xml` and accessibility semantics where required.
- Add/extend tests for TalkBack labels, click roles, text scaling, no clipping and back behavior.

**Interfaces:**
- Every retained destination uses `SentinelTopBar`, shared status/card/action states and a non-fabricated loading/empty/error state.
- All essential French copy is centralized in resources when it is user-facing and repeated.

- [ ] **Step 1: Add/extend Compose tests** for status text not relying on color, content descriptions, 48dp touch targets, 320dp/2x font, navigation back stack and no dead click handlers.
- [ ] **Step 2: Migrate the remaining old cards/buttons and direct color literals** screen by screen, preserving all existing actions and provider/system-setting handoffs.
- [ ] **Step 3: Run all unit/Compose/instrumentation tests available, lint and accessibility-oriented semantic assertions**.
- [ ] **Step 4: Commit** `feat(android): finish d1 secondary surfaces and accessibility pass`.

### Task 8: Build, visual evidence, performance and release handoff

**Files:**
- Modify/add: `docs/evidence/sentinel-d1/manifest.md`, `docs/evidence/sentinel-d1/limitations.md`, `docs/superpowers/plans/2026-10-18-sentinel-d1-total-redesign.md` only for status.
- Optional: benchmark/profile test files if the project already supports them.

- [ ] **Step 1: Run the complete project checks** from `native-android-app`: debug build, unit tests, available Android UI tests and lint; capture exact commands/results.
- [ ] **Step 2: If an Android runtime is available, install the newly built APK, launch it, exercise every major surface, collect real before/after screenshots and record API/device/orientation/font/motion settings. If not available, record each required capture as `BLOCKED_NO_ANDROID_RUNTIME` and do not claim device validation.
- [ ] **Step 3: Measure core render cost and inspect reduced-motion/power-save behavior; keep the core Canvas lightweight and avoid a 3D engine dependency unless measured evidence proves it necessary.
- [ ] **Step 4: Compute APK name, byte size, SHA-256, package, signature state, exact commit and any HTTPS artifact URL actually available. Mark missing URL/signature/device evidence as unqualified.
- [ ] **Step 5: Run the independent counter-expertise checklist: old-screen inventory, no dead controls, no simulated status, no removed routes, no emergency regression, no legacy screens accidentally left in the APK.
- [ ] **Step 6: Commit** `chore(android): qualify sentinel d1 redesign evidence`.

## Verification and Handoff

At the end, the report must list the exact commit(s), APK metadata, checks, visual evidence per major screen, limitations, remaining unqualified capabilities and one verdict from:

- `REDESIGN_NOT_STARTED`
- `REDESIGN_IN_PROGRESS`
- `REDESIGN_BUILT_NOT_VALIDATED`
- `REDESIGN_VALIDATED_ON_EMULATOR`
- `REDESIGN_VALIDATED_ON_DEVICE`

With the current environment constraints, the default verdict before a real Android install is `REDESIGN_BUILT_NOT_VALIDATED`, even if compilation succeeds.
