# Sentinel Integration Core Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the approved Sentinel Integration Core architecture in independently reviewable slices until every product capability is truthfully connected, locked, disabled, CI-only, or awaiting physical proof.

**Architecture:** Four ordered implementation plans share one canonical capability registry. The foundation must land first; Android wiring follows; external dependencies are then classified/activated where real infrastructure exists; release/physical qualification closes the loop.

**Tech Stack:** Kotlin/Android, Node.js, JSON, GitHub Actions, existing Sentinel evidence infrastructure.

**Spec:** `docs/superpowers/specs/2026-10-17-sentinel-integration-core-design.md`

## Global Constraints

- Never merge stale stacked PRs wholesale; replay only unique current deltas.
- Never claim 100% runtime integration from code presence or emulator-only proof.
- Never activate privileged/autonomous dormant modules solely to improve completion percentage.
- Keep CallScreening critical path isolated and bounded.
- Preserve privacy/provenance boundaries from the approved spec.

## Review Focus

- Cross-plan capability IDs must remain identical.
- A capability may advance state only when its own evidence requirements are met.
- External dependency failures must not regress unrelated connected local capabilities.
- Physical-required states must survive CI success unchanged.
- Release reports must be bound to exact head/build evidence.

---

### Task 1: Foundation

Implement `2026-10-17-sentinel-integration-foundation.md` completely. Do not start state-consuming UI migration before the registry and product-truth gate are stable.

### Task 2: Android/Intelligence wiring

Implement `2026-10-17-sentinel-integration-android.md`, selectively replaying unique #1695 deltas on the then-current main.

### Task 3: External/dormant dependency truth

Implement `2026-10-17-sentinel-integration-external.md`. Promote only dependencies with real deployable/runtime evidence; otherwise preserve truthful locked/disabled states.

### Task 4: Release and physical truth

Implement `2026-10-17-sentinel-integration-release.md`, then execute the exact release-candidate S24+ campaign for capabilities marked `PHYSICAL_REQUIRED`.

## Acceptance

The master plan is complete only when the exact-head integration report contains every registered capability and no user-visible/runtime-relevant feature lacks a truthful state or required producer-to-consumer integration chain.