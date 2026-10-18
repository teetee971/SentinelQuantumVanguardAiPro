# Sentinel D.1 Phase 3 Official Visuals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reconstruct the 13 official Sentinel D.1 Android surfaces with specialized native 2.5D scenes, faithful portrait composition, and truthful runtime states.

**Architecture:** Preserve the existing Compose/navigation/business boundaries. Add one reusable Canvas-based holographic scene engine with specialized scene kinds and small screen adapters; keep all evidence and action enablement in existing runtime/presentation layers.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Canvas, Android lifecycle/settings/policies, existing Sentinel runtime and unit-test stack.

**Spec:** `docs/superpowers/specs/2026-10-18-sentinel-d1-phase3-official-visuals-design.md`

## Global Constraints

- The 13 original references remain byte-for-byte preserved under `docs/design/sentinel-d1/reference-source/`.
- Use the official references for composition and depth; do not copy their illustrative metrics as runtime facts.
- Keep `UNKNOWN`, `LIMITED`, `LOCKED`, `TEST` and `READY` semantics stable and evidence-backed.
- Prefer Compose Canvas and do not add a heavy 3D engine.
- Respect reduced motion, portrait layout, TalkBack, touch targets and large text.
- Preserve Android Telecom, SMS/MMS, VPN, permissions and emergency-call boundaries.

## Review Focus

- Missing evidence must not render a green protected state: covered by scene-model tests and truth-state adapters.
- Reduced motion/power-save must not schedule animation frames: covered by motion-policy tests.
- Long French labels must not overlap or be clipped: covered by fixed-size/semantics source contracts and device capture when available.
- New routes must not strand existing actions/back navigation: covered by navigation source tests and Android-test compilation.
- Reference scenes must remain distinct instead of collapsing into one generic hero: covered by scene-kind contract tests.

### Task 1: Preserve references and design audit

**Files:**
- Create: `docs/design/sentinel-d1/reference-source/**`
- Create: `docs/design/sentinel-d1/reference-index.md`
- Create: `docs/design/sentinel-d1/screen-mapping.md`
- Create: `docs/design/sentinel-d1/visual-audit.md`

- [x] Import the archive without changing the 13 original names.
- [x] Record archive/image hashes, dimensions and the initial screen mapping.
- [x] Commit the immutable reference baseline.

### Task 2: Specialized holographic scene engine

**Files:**
- Create/modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelHolographicScene.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelVisualTokens.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelHolographicSceneSourceContractTest.kt`

- [x] Add scene models and the `SentinelSceneKind` enum.
- [x] Implement globe, sphere, layers, cube, chamber, threat and portrait projections in Canvas.
- [x] Drive colors and labels from status/proof data; keep decorative layers semantics-free.
- [x] Add reduced-motion and static rendering paths.
- [x] Compile and run focused source contracts.
- [x] Commit the scene engine.

### Task 3: Rebuild official screen compositions

**Files:**
- Modify: `HomeScreen.kt`, `PhoneSecurityScreen.kt`, `VpnScreen.kt`, `AppPermissionAnalyzerScreen.kt`, `SecurityAuditScreen.kt`, `SystemDoctorScreen.kt`, `SentinelDialerActivity.kt`, `CallerIdActivity.kt`, `SmsComposeActivity.kt`
- Create: specialized screen adapters for threat map, threat detail and quarantine where no route exists
- Test: screen source contracts and navigation tests

- [x] Replace generic hero calls with the matching official scene and compact evidence panels.
- [x] Keep real runtime adapters and disable unsupported actions.
- [x] Preserve dialer/SMS layout priority and emergency-call behavior.
- [x] Add new routes only for the unmapped official surfaces and wire back navigation.
- [x] Commit each independently testable screen lot.

### Task 4: Visual and accessibility qualification

**Files:**
- Modify: `docs/design/sentinel-d1/screen-mapping.md`
- Create: `docs/design/sentinel-d1/validation-matrix.md`
- Test: Compose/source contracts and available Android tests

- [x] Verify no legacy generic hero remains on the 13 mapped surfaces.
- [ ] Run build, unit tests, Android-test compilation and lint.
- [x] Capture only from a real Android runtime; otherwise mark capture blocked.
- [x] Record overlaps, truncation, touch targets, TalkBack and reduced-motion status.
- [ ] Commit the validation evidence.

### Task 5: APK qualification and delivery

- [ ] Assemble a new APK from the final corrective branch commit.
- [ ] Verify package, signature, size and SHA-256.
- [ ] Publish through an HTTPS release/tag mechanism without replacing the prior APK.
- [ ] Download and recalculate the published SHA-256.
- [ ] Report `D1_BUILT_NOT_VISUALLY_VALIDATED` until a S24+ or emulator capture exists.
