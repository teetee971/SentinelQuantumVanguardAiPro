# Phone Core Commercialization Batch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the current qualification false negatives and add one runtime-truth hardening batch without claiming OEM/carrier completion.

**Architecture:** Keep GitHub workflow orchestration fail-closed, but make exact-head/run-attempt selection and retry visibility deterministic. Keep Android behavior behind existing role/permission gates and prove lifecycle-sensitive SMS state with unit/instrumentation-compatible tests. Physical Samsung/operator scenarios remain `PHYSICAL_REQUIRED`.

**Tech Stack:** Node.js built-in test runner, Bash/GitHub Actions, Kotlin Android/Gradle.

**Spec:** User-provided Sentinel Quantum Vanguard AI Pro execution command in the task request.

## Global Constraints

- Emulation is developer qualification only; it does not replace Samsung Galaxy S24+, carrier calls/SMS/MMS, dual-SIM, Bluetooth, OEM screening, or carrier-call voice transformation.
- Statuses remain `READY`, `LIMITED`, or `LOCKED` and must be machine-justified.
- Production code changes require a failing test before implementation.
- Required gates must bind evidence to the exact PR head and exact workflow run attempt.
- Every committed lot must state tests/proofs and residual physical validation.

## Review Focus

- A failed first workflow attempt followed by a successful retry must not make the gate pass from the wrong run or fail from stale state; cover in `scripts/production-merge-gate.test.js`.
- A workflow run that is still in progress must never be treated as completed failure/success from a stale API response; cover in the same gate tests.
- SMS role/permission state must not remain falsely ready after process death or foreground re-entry; cover in the existing SMS activation policy/coordinator tests.
- API 37 answer bridge evidence must bind `ANSWERED` and `ACTIVE` to one call; cover in the existing bridge tests.
- Physical proof files with traversal, symlink, empty content, unknown schema properties, or missing required scenarios must fail closed; cover in `scripts/verify-phone-core-physical-session.test.js`.

### Task 1: Exact-head production gate truth

**Files:**
- Modify: `scripts/production-merge-gate.js`
- Test: `scripts/production-merge-gate.test.js`
- Verify: `package.json` test script for production gate

**Deliverable:** A deterministic selector/evaluator that never accepts a stale failed run over a later exact-head attempt, never treats incomplete API state as terminal, and emits enough attempt/run identity for diagnosis.

- [ ] Write a regression test for stale first attempt plus successful second attempt and for an in-progress run with a stale conclusion field.
- [ ] Run the focused test and observe RED.
- [ ] Implement the smallest exact-head/run-attempt selection/evaluation change.
- [ ] Run focused and repository gate tests and observe GREEN.
- [ ] Commit `fix(ci): make exact-head merge gate retry truth deterministic`.

### Task 2: Android CodeQL cause and guard

**Files:**
- Inspect/modify only the failing CodeQL workflow or Android source identified by the reproduced failure.
- Test: the smallest local command reproducing the failure, plus existing Android unit tests.

**Deliverable:** The actual Android analysis failure is classified and corrected, or a fail-closed diagnostic guard is added if the failure is remote-only; no blind rerun.

- [ ] Reproduce locally or extract a concrete failing step/diagnostic.
- [ ] Add a regression test/contract if the failure is repository-controlled.
- [ ] Apply the minimal correction and run the relevant local verification.
- [ ] Commit with a cause-specific message.

### Task 3: SMS activation lifecycle truth

**Files:**
- Inspect/modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SmsActivationStateCoordinator.kt`
- Inspect/modify: `native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt`
- Test: existing `SmsActivation*Test.kt` and/or a new focused regression test.

**Deliverable:** Role/permission readiness is revalidated after lifecycle/process boundaries and cannot remain falsely `READY`; emulator evidence remains separate from physical carrier proof.

- [ ] Write and run the failing lifecycle/revocation regression.
- [ ] Implement the minimal state transition/persistence correction.
- [ ] Run focused unit tests and Android product-truth checks.
- [ ] Commit `fix(phone-core): preserve SMS activation truth across lifecycle`.

### Task 4: Independent truth audits

**Files:** Existing API 37 bridge, physical verifier, manifest, and evidence tests only as required by findings.

**Deliverable:** Any new production blocker is corrected in a separate cause-specific commit; unresolved OEM/carrier constraints are recorded as `PHYSICAL_REQUIRED`.

- [ ] Review bridge, verifier, manifest, UX status and evidence binding.
- [ ] Add only actionable failing tests and fixes.
- [ ] Run the relevant focused verification before each commit.

## Handoff

Do not call the product commercial or production-ready. Report only concrete commits, fresh command output, new defects, and remaining production blockers.
