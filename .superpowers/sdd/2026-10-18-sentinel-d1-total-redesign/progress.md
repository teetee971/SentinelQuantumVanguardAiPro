# SDD ledger — plan: docs/superpowers/plans/2026-10-18-sentinel-d1-total-redesign.md

Base: 7e9b883 (plan commit, branch feat/sentinel-d1-3d-total-redesign)

Pre-flight: Task 1 produces shared D.1 primitives and status semantics consumed by Tasks 2–7; existing call sites must remain source-compatible while the new APIs are introduced.
Pre-flight: Task 2 produces shared shell/navigation semantics consumed by Tasks 3–7; all existing routes and standalone intents remain preserved.
Pre-flight: Tasks 3–4 consume existing runtime presentation facts and Task 1 primitives; they must not duplicate or override domain truth.
Pre-flight: Task 5 consumes Task 1 primitives and Task 2 shell conventions but keeps Telecom/SMS/MMS service contracts authoritative.
Pre-flight: Task 6 consumes Task 1 primitives and existing lifecycle/voice contracts; UI state must not promote platform readiness.
Pre-flight: Task 8 consumes all prior task outputs and the absence of an Android runtime is a qualification blocker, not a reason to fabricate evidence.

Ruling: Execute inline in the current isolated repository because no subagent tools are available; keep task boundaries, per-task TDD and final self-review.
Ruling: Use the existing Compose Canvas visual foundation instead of adding a heavyweight 3D engine; the brief requires lightweight fallback and the repository already has motion-aware Canvas decoration.

## Task 1 — completed

- Added centralized D.1 palette and explicit status roles, including `TEST` and `PURP`.
- Added `SentinelQuantumCore`, `SentinelEvidenceGraph`, and shared loading/empty/error/action primitives.
- Kept animation optional through `MotionLevel`; static and reduced modes do not schedule frame animation.
- Preserved existing visual-token tests and added coverage for palette/status semantics and core evidence models.
- Verification: targeted unit tests pass; `:app:lintDebug` passes.

## Tasks 2–7 — implementation checkpoint

- Connected `SentinelBottomNavigation`, `SentinelHeroVisual` and the native `SentinelQuantumCore` to the Home command center while preserving existing routes, test tags and standalone intents.
- Extended the D.1 core to Phone Core dialer, active call, number identity, SMS/MMS, activation/diagnostic and Voice Studio surfaces. Existing Telecom, SMS/MMS, VPN and voice boundaries remain the source of truth.
- Harmonized Phone Security, Network Center, VPN, Digital Exposure, Security Audit, System Doctor, permissions, SMS/e-mail analysis, community, settings, call filtering, OSINT and local logs with runtime-driven nodes, evidence/provenance labels and explicit unknown/limited/locked states.
- Added the Assistant Sentinel route and deterministic local prompt policy that is explicitly advice rather than technical proof.
- Corrected Canvas `dp`/`px` coordinate handling for high-density Android devices and made evidence edges render only for proof-backed references.
- Verification checkpoint: `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest` (1,405 tests), and `:app:lintDebug` pass. No Android runtime is available for install, screenshots or instrumentation execution.
- Final secondary-surface checkpoint: `9b670dc765fd3dec678b260cdd24769195466343`; final debug APK SHA-256 is `849aab489fe08637fe64d0e659430db5c7b9d1e2c3004f7a2864f7d1193350c7`.

