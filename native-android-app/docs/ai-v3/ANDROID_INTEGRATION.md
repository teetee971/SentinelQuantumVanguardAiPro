# AI V3 trusted Android integration contract — not yet wired

The security kernel is not a working inference engine. Keep product availability
UNAVAILABLE until a real backend, Android authentication and action adapters are qualified.
Do not add a chat screen with canned responses or claim voice output exists because
SentinelAiV3Config.voiceResponsesEnabled is true. Configuration is not capability evidence.

## Composition and lifecycle

Only trusted application code may construct an Owner using SentinelAiAndroidClock.createOwner.
Supply a fixed action catalog and a verifier of completed Android authentication. The default
owner has an empty catalog and an authentication verifier that always denies. No Phone Core,
SMS, MMS, VPN or Room behavior is changed or registered by this patch.

Keep Owner and DecisionPort private to the application/UI boundary. Give the model adapter
only the Session returned by owner.openSessionScope (or ProposalPort when manual scoping is
necessary). Session is AutoCloseable; close on ViewModel clearing, cancelled user flow and
background/logout according to the chosen foreground policy. Close revokes both pending and
approved proposals, is idempotent, and does not recreate a session. Reopening requires a new
handle. Logout/lock must call owner.revokeAll. Process death loses all capabilities; never
persist them or restore them from SavedStateHandle. Do not create these roots in Compose
recomposition and do not pass Context, Owner, DecisionPort or raw controller to inference code.

A scope test is not an Android lifecycle instrumentation test. Actual lifecycle wiring,
navigation, saved state, process death and interrupted authentication still require testing.
No Application, navigation, manifest, dependency or permission changes were made here.

## Authentication and approval

Display immutable action identity and strictly parsed parameters from the original Proposal.
Authentication must bind to that exact token/session/action/parameters/expiry, not to model
text or an unbound global "authenticated" flag. Before calling decisions.approve, complete
an Android BiometricPrompt/device-credential flow satisfying the product's assurance policy.
The installed verifier must validate and consume the completed proof for this proposal.
It must not launch an asynchronous prompt or return true just because a button was clicked.
Do not install the test fixture's always-true verifier in application code.

The new API is a fail-closed integration seam, not an implementation of Android human
authentication. No such verifier is shipped, so production action execution remains disabled.
Separate approval and execution so execution can recheck monotonic expiry, session revocation,
parameter validation and current role/permission state. A successful approve boolean alone
is not an execution capability; execution must go through the SAME owner's DecisionPort.
Changes to proposal fields, including expiry, are rejected. Replay never repeats an effect.

## Action adapter rules and cancellation boundary

Register only explicit action identifiers with validators that reject every extra field,
unsupported enum value, ambiguous encoding, path/URL outside the action's permitted scope,
and invalid ranges. Generic bounds are 64 ASCII identifier characters and 4096 parameter
characters without NUL; these bounds DO NOT substitute for action-specific parsing.

permitted must check effective Android permissions AND required roles at execution time.
The Android API must also enforce its own permissions. The OS may revoke a permission after
the check: catch the resulting failure without claiming success or automatic retry.

Validators, proof verifiers, permission checks and effects must be bounded synchronous trusted
code. Do not schedule/defer an effect in execute and return success before it occurs. Do not
call revocation from within the effect and then continue to perform more effects. Do not
acquire application locks in an order that can cycle back into the authority. No network
inference or UI prompting while its monitor is held. Callback reentry into propose/approve/
execute is denied; reject/revoke during checks cancels admission.

Execution and external revocation share one monitor. Once revoke returns, a not-yet-started
effect cannot start for the revoked session. Revocation waits for an effect admitted earlier.
It cannot undo an Android request already submitted. Android delivery/network completion
is not represented by ExecutionResult.EXECUTED: that result only says the registered
synchronous adapter returned normally. OUTCOME_UNKNOWN means an adapter threw after entry;
retain transport-specific truth and do not retry automatically. There is no cross-process,
crash-safe exactly-once guarantee and no protection against malicious code with reflection
or arbitrary execution inside the same Android process.

## Inference and qualification gates

SentinelAiEngineRouter only sorts descriptors. available/supportsText flags do not initialize
or attest a backend; remoteAllowed is a caller policy input, not a consent UI. No backend,
model file, remote endpoint or credentials is wired. Before enabling inference, choose a
licensed runtime/model or remote service, verify artifacts and actual readiness, bound input,
output and cancellation, and measure S24+ RAM/latency/thermal/battery behavior. Remote data
must require explicit session-scoped consent with no silent local-to-remote fallback.

Qualify the 8 original suites and added regression suites, full Android compilation,
role/permission changes, authentication cancellation/lockout, pause/resume/process death,
revoke-vs-confirm-vs-execute races, and then Phone Core/SMS/MMS/VPN regressions. Build and
verify the exact debug APK signature and manifest and record its SHA-256. No paid Actions.

On Termux + Ubuntu proot, with the project's JDK/SDK configured, from native-android-app:

```sh
bash ./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ai.*' --rerun-tasks --console=plain
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Instrumented tests need a connected authorized Android device/emulator, test dependencies
and actual integration test cases; executing an empty connected test task is not evidence.
Standalone JVM alternative (repository root, locally installed dependencies):

```sh
python3 scripts/validate-sentinel-ai-jvm.py \
 --kotlinc /path/to/kotlinc \
 --junit /path/to/junit-4.13.2.jar \
 --hamcrest /path/to/hamcrest-core-1.3.jar \
 --android-jar /path/to/sdk/platforms/android-37/android.jar
```

This writes a new temporary evidence directory using Python's platform-specific temp choice
(including TMPDIR on Termux), with logs, exact commands, hashes and per-class XML. It does
not download dependencies or alter existing sources. Never label it Android qualification.
