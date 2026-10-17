# Sentinel Integration Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a canonical capability registry, integration-state model, diagnostics contract, and fail-closed `integration-product-truth` gate.

**Architecture:** Static capability metadata lives in one canonical JSON registry. Runtime Android code exposes typed integration state keyed by the same stable capability IDs. CI validates that no user-visible capability can silently default to `CONNECTED`, and that every non-connected state has a bounded diagnostic reason.

**Tech Stack:** JSON, Node.js test runner, Kotlin/JVM, Android unit tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-17-sentinel-integration-core-design.md`

## Global Constraints

- Valid product states are exactly `CONNECTED`, `LOCKED_EXTERNAL_DEPENDENCY`, `PHYSICAL_REQUIRED`, `INTENTIONALLY_DISABLED`, `CI_ONLY`.
- Missing evidence never implies success.
- CallScreening critical-path behavior must not gain disk, network, AndroidKeyStore, or heavyweight integration work.
- CI/emulator evidence never substitutes for physical S24+/SIM/carrier/OEM proof.
- Existing domain engines keep ownership of side effects.

## Review Focus

- Unknown capability IDs must fail closed and never become CONNECTED.
- Duplicate capability IDs must fail validation.
- Missing diagnostic reason for a non-CONNECTED state must fail validation.
- Stale runtime observations must not preserve a CONNECTED claim.
- CI_ONLY/INTENTIONALLY_DISABLED capabilities must never count as production-connected completion.

---

### Task 1: Canonical capability registry schema

**Files:**
- Create: `config/integration-capabilities.json`
- Create: `scripts/integration-capability-registry.test.js`

**Interfaces:**
- Produces stable capability IDs and static metadata consumed by later tasks.

- [ ] Write RED tests asserting unique IDs, required owner/surface fields, valid static classification, and presence of the 25 capability families from the spec.
- [ ] Run `node --test scripts/integration-capability-registry.test.js`; expect FAIL because the registry does not exist.
- [ ] Create `config/integration-capabilities.json` with schema version 1 and one entry per product capability family.
- [ ] Re-run the test; expect PASS.
- [ ] Commit `feat(integration): add canonical capability registry`.

### Task 2: Typed Android integration-state model

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/integration/IntegrationState.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/integration/IntegrationCapabilityId.kt`
- Create: `native-android-app/app/src/test/java/com/sentinel/quantum/integration/IntegrationStateTest.kt`

**Interfaces:**
- Produces `IntegrationState`, `IntegrationDiagnosticReason`, and stable capability IDs for runtime consumers.

- [ ] Write RED JVM tests for exact enum values, unknown-ID rejection, and non-CONNECTED diagnostic requirements.
- [ ] Run the targeted Gradle unit test; expect FAIL because types are absent.
- [ ] Implement the minimal typed model with no side effects.
- [ ] Re-run targeted tests; expect PASS.
- [ ] Commit `feat(integration): add typed product integration states`.

### Task 3: Runtime evidence and snapshot aggregation

**Files:**
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/integration/IntegrationObservation.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/integration/IntegrationSnapshot.kt`
- Create: `native-android-app/app/src/main/java/com/sentinel/quantum/integration/IntegrationRegistry.kt`
- Test: `native-android-app/app/src/test/java/com/sentinel/quantum/integration/IntegrationRegistryTest.kt`

**Interfaces:**
- Consumes stable capability IDs from Task 2.
- Produces `IntegrationRegistry.snapshot(nowMs)` for UI/System Doctor/release evidence.

- [ ] Write RED tests for stale evidence, duplicate observation handling, missing evidence, and fail-closed state resolution.
- [ ] Run targeted tests; expect FAIL.
- [ ] Implement an in-process bounded registry with explicit TTL on runtime observations and no domain side effects.
- [ ] Re-run targeted tests; expect PASS.
- [ ] Commit `feat(integration): add bounded runtime integration registry`.

### Task 4: Product-truth validator

**Files:**
- Create: `scripts/check-integration-product-truth.js`
- Create: `scripts/check-integration-product-truth.test.js`
- Modify: `package.json`

**Interfaces:**
- Consumes `config/integration-capabilities.json` and source declarations.
- Produces a non-zero exit on false/missing product integration claims.

- [ ] Write RED tests covering unknown capability, duplicate IDs, invalid CONNECTED claims, CI_ONLY counting, and missing diagnostic reason.
- [ ] Run `node --test scripts/check-integration-product-truth.test.js`; expect FAIL.
- [ ] Implement the validator and register `test:integration-product-truth` in `package.json`.
- [ ] Run the new test and `npm run test:android-product-truth`; expect PASS.
- [ ] Commit `test(integration): enforce product integration truth`.

### Task 5: Release-gate wiring

**Files:**
- Modify: the existing product/repository validation workflow that executes product-truth gates.
- Test: extend `scripts/check-integration-product-truth.test.js` with workflow invocation assertions.

**Interfaces:**
- Consumes `npm run test:integration-product-truth`.
- Produces a required CI verdict before merge/release claims.

- [ ] Add RED workflow-source assertion that the integration truth command is mandatory.
- [ ] Run the source test; expect FAIL.
- [ ] Add the command to the existing required product-truth/repository validation job without weakening any other gate.
- [ ] Re-run source tests and syntax checks; expect PASS.
- [ ] Commit `ci(integration): require integration product truth`.

## Acceptance

Foundation is complete when the registry covers every product capability family, runtime state cannot default to CONNECTED, and CI fails on any false/missing integration claim.