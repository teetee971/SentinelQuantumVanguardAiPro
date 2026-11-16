# Sentinel AI V3 — security correction pass, 2026-11-16

Status: security kernel corrected and JVM-tested; Android/inference qualification OPEN.
No production-readiness or physical-device success claim.

## Provenance and preservation

Fetched origin transfer/sentinel-ai-v3-20261116 and verified HEAD exactly matched
723c6c6067eefadb7c3935e8d13a02f7e5b49dfd BEFORE modifying files.
Dedicated branch: fix/sentinel-ai-v3-security-20261116.
The transfer contains 8 production Kotlin files and 8 test classes. It does not establish
that the rest of the Galaxy's dirty worktree is present. The previous audit commit
f43668cff371dda0ec21822c9d841249fded8eb0 remains on audit/sentinel-v3-p0-20261116.
No LocalAgent tests were rerun. No main changes, push, paid Actions or destructive Git operation.

Pre-edit archive: /workspace/work/sentinel-ai-v3-evidence/ai-transfer-baseline.tar.gz
SHA-256: d114444ee2a43c48501c11190a87cc22e53c2f4c9acbe66f8e9e71f47c3543e9
All 16 archived file contents were compared byte-for-byte with `git show` at the exact
transfer SHA, independently of the working tree. Source hashes are in the evidence ZIP.

## Findings, corrections and impact

1. ApprovalAuthority accepted every nonblank action and had no human-authentication check.
   Reproduction: unconfiguredAuthorityMustNotIssueArbitraryAction fails on transfer.
   Now the catalog is copied at root creation, empty by default. A per-action validator and
   a trusted verifier of completed authentication are mandatory for an executable grant.
   Authentication failure consumes the proposal. The built-in verifier always denies.
   No Android authentication implementation or production action is registered by this pass.
2. confirm/approve previously returned a boolean with no revocation-safe execution seam.
   Using that boolean later could run an effect after session/global revocation. There was
   no original executor to exploit; this is an integration hazard, not a claim of an actual
   previously-wired Android action bypass. New DecisionPort.execute consumes an approved
   exact proposal and rechecks parameters, permissions/roles, monotonic expiry and session.
   Execution and revocation are serialized. A revoke that returns prevents new effects;
   it waits for effects already admitted. Platform effects cannot be retroactively undone.
3. Gate accepted time rollback relative to a later request. New permanent rollback poison
   clears pending requests. Controller already detected decreasing time but failed to revoke
   on clock exceptions; exceptions now permanently poison it too. revokeAll cannot recover it.
4. Pending requests were count-bounded but arbitrary parameter strings were not size-bounded.
   Both primitives now enforce identifier format and length <=64 and parameters <=4096 chars,
   with NUL rejected. Catalog validators must still implement strict semantic parsing.
5. Proposal expiry metadata was not included in the original authority decision check.
   New stored Proposal equality binds every displayed field, including expiry. Tokens remain
   process-local UUID capabilities; they must not be persisted or logged.
6. Trusted callbacks can reenter synchronized JVM monitors. Approve/execute/propose reentry
   during checks is now denied. Reentrant reject/revoke changes the generation and prevents
   admission. Failed role lookup, failed proof verification and expired grants never execute.
   An effect that throws is OUTCOME_UNKNOWN and cannot be retried using the same capability.
7. Approved-but-unused grants need capacity bounds too: at most 128 total authority entries,
   with expiry pruning. Session scopes close by revoking, without holding a second monitor
   around an authority call. No model-facing capability exposes approval or execution.

Security boundary: trusted application code and synchronous adapters. This is not isolation
against arbitrary hostile code/reflection in the same Android process. Raw Controller/Gate
remain standalone low-level primitives; their booleans are not accepted by the new executor.
Broker remains proposal-only and wraps the raw primitive; prefer Owner's scoped Session for
real integration, which enforces the registered catalog. Never give raw controller to a model.
API changes intentionally make the old unconfigured authority deny previously arbitrary actions.
Original tests that expected approval now explicitly install TEST-ONLY actions/authentication.

## Engine and Android integration audit

EngineRouter only filters/sorts caller-supplied metadata. No inference invocation exists.
Candidate.available is not a runtime probe; remoteAllowed is not verified user consent.
No references to SentinelAi outside its package were found in production app/src/main.
V3 voice/config flags are preferences, not proof of working voice/inference capabilities.
No fake engine, canned inference response or availability UI was added.

Prepared Android monotonic owner factory and AutoCloseable session handle. Detailed integration,
authentication, lifecycle, cancellation and adapter rules: ANDROID_INTEGRATION.md.
No Application/navigation/manifest/build dependency changes were made without a real qualified
backend and Android verifier. No changes to Phone Core, SMS, MMS, VPN or Room schema.

## Executions and evidence

Before correction: all 8 ORIGINAL AI classes executed with Kotlin 2.1.20, Java 21,
JUnit 4.13.2/Hamcrest 1.3: OK (49 tests), 0.47 seconds.
Four API-compatible regression tests were then compiled against archived transfer sources:
4 executed, 4 FAILED, confirming arbitrary-action admission, unbounded parameters, gate clock
rollback and surviving controller sessions after clock exceptions. See reproduction.log.

Intermediate fixed run: OK (80 tests), followed by additional regressions/reentrancy hardening.
First XML harness attempt compiled all AI sources, then stopped because javac executable was
absent. Harness was corrected to invoke the available JDK compiler module via java -m.

Final recorded run: 11 suites, 86 executed, 0 failures, 0 errors, 0 skipped.
Includes all 8 original suites plus BoundaryRegression (4), ExecutionSecurity (29) and
SessionScope (4). XML reports, exact command argv, source/dependency SHA-256 and tool versions
are in jvm-evidence.zip with external evidence-sha256.json for the archived evidence files.
JUnit failures, including exceptions, are represented as <failure> by the standalone writer.
The eight original suites' counts remain 2,10,4,4,4,7,15,3 = 49.

Final command from repository root:

```sh
python3 scripts/validate-sentinel-ai-jvm.py \
 --kotlinc /workspace/work/mms-recovery/tools/kotlinc/bin/kotlinc \
 --junit /workspace/work/mms-recovery/tools/junit.jar \
 --hamcrest /workspace/work/mms-recovery/tools/hamcrest.jar \
 --android-jar /workspace/work/sentinel-ai-v3-evidence/tools/android-api.jar \
 --output /workspace/work/sentinel-ai-v3-evidence/qualified-jvm-run
```

The API compile JAR is Maven com.google.android:android:4.1.1.4; no Android method was
executed. No stubs were authored for this AI run. This verifies Kotlin source compatibility
with available SystemClock declarations, NOT compileSdk 37 or the project's Kotlin 2.4.20.
Concurrency tests cover single-use approval/execution, confirmation-vs-session-revocation
with barriers (30 races), revoke-after-approval, and waiting for an already-started effect
before revocation returns. They are examples of schedules, not exhaustive proofs.

Attempted `bash ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ai.*' --console=plain`
from native-android-app: exit 1 downloading Gradle 9.8.0 with java.net.ConnectException.
Curl could reach the distribution URL, but the wrapper's Java download remained blocked.
No project build configuration or network policy was changed to bypass this.

NOT executed: full Android compile, Gradle unit suite, instrumentation, real Android proof
verification, actual role/permission transitions, complete Phone Core/SMS/MMS/VPN regressions,
model inference, APK construction/signature/manifest validation and Galaxy device tests.
No new qualified APK exists and no APK SHA-256 is claimed.

## Residual blockers / next pass

- Install and instrument a real Android human-authentication verifier bound to proposal fields;
  decide assurance policy, credential/biometric fallback, lockout and interruption handling.
- Register and qualify individual action adapters with strict parsers, real Android guards and
  synchronous admission semantics. None is shipped; use no always-true production verifier.
- Select a real inference backend/runtime/model, license/artifact verification and resource
  limits. Obtain explicit remote consent if chosen; no API key or paid service is provisioned.
- Wire actual Android lifecycle and navigation with truthful unavailable/initializing/ready/
  busy/error states, session closure and cancellation, without default availability claims.
- Run project Gradle and device qualification commands in ANDROID_INTEGRATION.md. Inspect the
  exact APK and hash it only after a successful build; test physical critical functions.
- Logs and API compile evidence do not establish absence of all bugs or production readiness.
