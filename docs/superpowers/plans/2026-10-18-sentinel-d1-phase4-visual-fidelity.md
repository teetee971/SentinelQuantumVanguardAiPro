# Sentinel D.1 Phase 4 Visual Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 3 generic holographic wireframes and unsafe narrow evidence layouts with scene-specific, bounded, truthful 2.5D Compose visuals faithful to the 13 official D.1 references.

**Architecture:** Keep `SentinelHolographicScene` as the single native Compose renderer, but split its drawing into reusable material/depth primitives and specialized scene functions. Fix shared metric/action layout centrally, then harmonize Network Center and activation without changing business adapters.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, Material 3, JUnit source contracts, Gradle Android plugin.

**Spec:** `docs/superpowers/specs/2026-10-18-sentinel-d1-phase4-visual-fidelity-design.md`

## Global Constraints

- Preserve all existing Android, Telecom, SMS/MMS, VPN, identity, reputation, and Wear OS contracts.
- Use the 13 originals under `docs/design/sentinel-d1/reference-source/` as the only visual source.
- Never render reference-only numbers or states as runtime proof.
- Support `FULL`, `REDUCED`, and `STATIC` motion modes and TalkBack summaries.
- No fixed four-column metric strip; required text must remain readable and wrapped.
- No Android/device visual validation claim without a real runtime.

## Review Focus

- Long French labels and large-font settings must not be clipped; cover with source/layout contracts.
- Empty or unknown evidence must remain visually rich but semantically unknown; cover each scene model.
- Geometry must remain inside the hero bounds on a narrow viewport; cover by bounded primitive contracts.
- Network devices must not become connected merely because they were observed; preserve existing adapter source.
- Dialer/activation actions must remain reachable when decorative scenes are present; cover activity source contracts.

### Task 1: Reference and architecture ledger

**Files:**
- Create: `docs/design/sentinel-d1/phase4-fidelity-matrix.md`
- Create: `docs/superpowers/specs/2026-10-18-sentinel-d1-phase4-visual-fidelity-design.md`
- Create: `docs/superpowers/plans/2026-10-18-sentinel-d1-phase4-visual-fidelity.md`

- [ ] Verify the 13 originals, archive hash, dimensions, and existing mappings.
- [ ] Record each reference, Android surface, scene kind, actual data source, and current visual limitation in the matrix.
- [ ] Commit the documentation before production code.

### Task 2: Volumetric scene renderer

**Files:**
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelHolographicSceneSourceContractTest.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelHolographicScene.kt`

- [ ] Add failing source contracts for `SentinelSceneMaterial`, depth/extrusion/highlight/particle primitives, and each official scene family.
- [ ] Run the focused test and confirm it fails because the richer primitives are absent.
- [ ] Implement bounded radial materials, depth offsets, perspective planes, volumetric sphere/shield, globe mesh, phone layers, cube faces, quarantine cylinder, threat core, and identity silhouettes.
- [ ] Keep state accent and evidence semantics model-driven; retain drag/reduced-motion/static behavior.
- [ ] Run the focused test and the existing scene/token tests.
- [ ] Commit `feat(android): deepen phase4 holographic scene materials`.

### Task 3: Responsive evidence and navigation safety

**Files:**
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/SentinelReferencePanelsSourceContractTest.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelReferencePanels.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt`
- Modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt`

- [ ] Add failing contracts for adaptive two-column metrics, no forced one-line metric text, topology-specific Network Center scene, and activation/dialer priority.
- [ ] Run the focused tests and confirm expected failures.
- [ ] Implement the responsive metric grid, topology scene wiring, wrapped activation state rows, and compact dialer hero that never displaces keypad/call controls.
- [ ] Run focused screen contracts and source isolation checks.
- [ ] Commit `fix(android): prevent phase4 layout collisions and metric clipping`.

### Task 4: Truthful matrix and validation evidence

**Files:**
- Modify: `docs/design/sentinel-d1/phase4-fidelity-matrix.md`
- Create/modify: `docs/design/sentinel-d1/phase4-validation.md`

- [ ] Record commands, results, artifact status, and the explicit no-device/no-capture limitation.
- [ ] Do not mark a visual or device gate passed without real captures.
- [ ] Commit `docs(android): record phase4 fidelity gates`.

### Task 5: Full verification and delivery

- [ ] Run `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `lintDebug`, and `assembleDebug` from the same final commit.
- [ ] Inspect package, version, signing certificate, APK size, and SHA-256.
- [ ] Publish only a uniquely tagged Phase 4 test artifact if GitHub permissions permit; verify the downloaded hash.
- [ ] Push the branch and create a corrective PR targeting `feat/sentinel-d1-phase3-official-visuals`, preserving #1699/#1701/#1702.
- [ ] Complete an independent self-review and use only an allowed Phase 4 verdict.
