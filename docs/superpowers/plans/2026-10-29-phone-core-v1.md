# Phone Core V1 Implementation Plan

> Execution: native in this session, as explicitly requested by the mission. No approval pause or unrelated module completion.

**Goal:** qualify the smallest Android Phone Core delivery with reproducible evidence.
**Architecture:** retain Telecom-owned calls and the existing role/permission gates; apply only reviewed phone changes from named commits. Keep evidence scoped to call identity and lifecycle.
**Tech stack:** Kotlin, Compose, Android Telecom, Gradle wrapper, JUnit.
**Spec:** user mission of 2026-10-29.

## Constraints
Dedicated branch from da157f4; preserve Room schemas and other modules; no workflow dispatch; physical checks remain NON TESTÉES without a device.

## Review focus
Rotation during role request; cancellation despite RESULT_OK; stale call callbacks after removal/unbind; simultaneous calls with distinct IDs; permission/channel revocation.

## Tasks
- [x] Compare f86e7d6, 9d093ba, e121aefa against base; import only phone patches and their regression tests.
- [x] Preserve pending role through recreation; re-read actual held role and guard launch errors. Test cancellation/held-role precedence.
- [x] Validate lifecycle-scoped evidence, call-targeted actions, notification and DTMF behavior; repair demonstrated defects with regression tests.
- [x] Hide unavailable in-call commands; preserve other modules without changing their validation status.
- [x] Attempt Gradle Kotlin compilation, critical unit tests, APK and unsigned AAB; retain command logs and exact blockers.
- [x] Audit Play permissions/signing; provide Galaxy S24+ checklist, anomaly list, modified files and PR. Inspect workflow triggers before any push.
