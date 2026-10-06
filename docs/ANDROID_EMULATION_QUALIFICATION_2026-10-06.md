# Android developer qualification — 6 October 2026

Initial checkout: clean `main`, `5719103f`. No AGENTS.md was found in the
checkout. Modules: app (minSdk 24, targetSdk 36, compileSdk 37), wearable-contract,
wearable-security. Existing emulator lanes are API 24/29/36/37. The independent
instrumentation workflow remains during the existing shadow rollout.

## Execution evidence inspected

Run [37470803395](https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/runs/37470803395)
is historical evidence for PR source `7877d119`, not for this checkout. Host tests,
lint, builds and connected instrumentation passed; API 24/29 runtime passed;
API 36/37 runtime failed. Downloaded API 37 artifact 11417921173 confirms:
synthetic calls and SENT/DELIVERED SMS callbacks completed, then SEND_SMS
AppOp denial remained `allow`. The existing gate correctly stayed red.

## Changes to the gate

- AppOp setup writes a fresh observation each time and requires two consistent
  observations, bounded to ten infrastructure attempts. Historical denial, query
  failure and contradictory modes cannot pass. The product action is not retried.
- Real MainActivity bottom navigation, Android Back, recreation and relaunch are
  instrumented. Activation, dialer keypad and SMS surfaces also undergo recreation.
  Static navigation smoke tests now reject blank rendered surfaces. An instrumented
  negative control deliberately renders empty content and requires the oracle to fail.
- API 37 additionally runs two real UI tests and a clipping negative control at 1080×2400/420 dpi and
  1440×3120/480 dpi. Eleven screenshots per profile accompany content, selection,
  click and title layout assertions. This is resolution equivalence, not Samsung
  OEM validation. Contrast, full-screen overlaps, keyboard and landscape have not
  received exhaustive automated visual comparison.
- Screening records monotonic callback-to-response duration in microseconds after
  responding, including role/emergency/rule-engine fail-open paths. Every observed
  response must stay strictly below 500,000 µs to qualify its lane.
- Logcat analysis attributes failures to package/PID/stack. Product exceptions,
  ANRs, crashes and unexplained process death fail. Explicit harness force-stops
  remain recorded; system findings remain visible. Screenshots in runtime flows
  trigger log analysis and retain the corresponding buffer/report.
- Qualification JSON is extended with capability status, test, evidence,
  timestamp, environment, reason and deviceRequired. Markdown and crash/ANR JSON,
  properties and both APKs are archived. APKs have a separate artifact so compact
  host reports remain independently downloadable. Empty/placeholder PNGs are rejected.

The first instrumented attempt rejected short, fully fitted titles because
`TextLayoutResult.hasVisualOverflow` compared intrinsic size with the paragraph's
available width (SENTINEL: 67 versus 304 px, no ellipsis). The oracle now checks
actual line bounds, ellipsis and exceeded line limits. One pixel of tolerance
accounts for integer coordinate rounding; the deliberately ellipsized text must
still fail the same predicate. Failure captures survive UTP through scoped
MediaStore on API 29+; API 24 uses its app-owned external directory, pulled before
teardown. A malformed viewport summary produces FAIL JSON/Markdown, not a lost report.

The first runtime attempt also exposed an attribution defect: a DeviceLock
exception in system_server inherited a nearby ActivityTaskManager display event
for Sentinel. Attribution now follows the exception's actual stack records,
including real Sentinel frames, and stops at unrelated same-PID events. The
captured Android 16 buffer retains all 52 system findings with zero product
failures after reanalysis; negative tests still reject product exceptions.
Host JVM tests/lint are labelled HOST_TESTED with host artifact provenance;
they cannot be described as EMULATOR_TESTED merely because their result appears
in an emulator report.

With the corrected oracle, run 37480125025 detected genuine Communications
subtitle truncation on API 24 (256 px, one ellipsized line). Artifact 11420319191
contains the screenshot showing "canaux extern...". SentinelTopBar now permits
two subtitle lines while retaining the existing theme, title and bar structure.
The existing real navigation assertion remains the regression test; the oracle
is not weakened to accept this truncation.

## Voice and physical boundary

VoiceStudioActivity records and plays a private local preview. LiveKitVoiceAudioProcessor
processes direct Float32/FloatS16 frames through SentinelVoipVoicePipeline and
LiveVoiceTransformEngine; SentinelLiveKitCallTransport provides a client room boundary.
No production screen currently instantiates that transport. Unit tests exercise
DSP/processor and transport contracts, but no outgoing transformed production call
has been demonstrated. A token issuer, signaling/room service and PSTN gateway
remain required. Ordinary ROLE_DIALER/InCallService APIs provide no public SIM
uplink capture/transformation/reinjection path.

Carrier calls/SMS/MMS, physical SIM/multi-SIM, audio quality/Bluetooth, Samsung
lockscreen/battery/OEM behavior, Wear OS transport and voice end-to-end remain
DEVICE_REQUIRED. Synthetic modem success cannot upgrade these to PHYSICAL_VALIDATED.
The global `passed` flag remains false until complete current-commit evidence exists.

## Reproduction

```sh
node scripts/check-phone-core-emulation-gate.js
node --test scripts/phone-core-emulator-evidence.test.js scripts/android-logcat-analysis.test.js
npm run test:android-product-truth
cd native-android-app
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :wearable-contract:test :wearable-security:test --no-daemon
./gradlew :app:connectedDebugAndroidTest --no-daemon
bash ../scripts/android-viewport-qualification.sh "$EVIDENCE_DIR"
```

CI result and qualification artifacts are authoritative for their recorded commit,
not for a later documentation update or branch head. SDK/Java/proxy setup failures
must be reported separately from product failures. This cloud executor initially
had Java 21 and no SDK/KVM; Android SDK and Java 17 were installed for local work.
