# Sentinel v23 — EXPERIMENTAL / BLOCKED

Source: verified Termux archive SHA-256 `138aa86bfcce3d1d0221178b0b347a697f0991a1642dab4b8a7d4a68f9998e8d`, based on `fafe9f5b1c57b21f276ca62189acab625a490ec9`. Worktree and branch isolated from stable v22.

## Implemented

Bounded JPEG decoding/transcoding, eight EXIF orientations, unchanged fitting originals, one pixel decode per JPEG, fixed input5MiB/source20MP/decoded4MP bounds, bounded reusable output buffer, at most40 quality/resolution attempts, bitmap cleanup and interruption checks. Pure preparation uses the real composer's counting encoder and Long water-filling budgets, retains non-JPEG bytes/order, snapshots originals, then enforces the complete selected-SIM PDU gate. Sender stages that exact PDU and persists exactly its prepared parts. Role/permission/SIM/budget are checked again after preparation and before transport; uncertain transport is never retried. Editing a draft during compression is handled separately by the UI snapshot policy.

AndroidX EXIF1.4.1 is a production dependency for maintained metadata parsing; Robolectric4.16.1 is test-only. Main app versionCode23/versionName1.0.10. JPEG automatic reduction requires API28+, where ImageDecoder offers partial-image reporting. API24–27 retain bounded fitting-original behavior and explicitly refuse the new reduction operation. Laboratory minSdk28, isolated package `com.sentinel.quantum.mmslab.v23` (does not replace either v22 or the previous laboratory).

## Known Important blocker — do not release

`truncatedEntropyWithAnAppendedEndMarkerIsRejected` is deliberately retained as a FAILING regression, not ignored. A JPEG truncated halfway through its entropy stream and then terminated with FFD9 is accepted by Android's native JPEG backend despite `setOnPartialImageListener { false }`. The backend emits warning117 and recovers missing data. This violates the requested strict malformed/truncated-JPEG refusal contract. ImageDecoder does not expose that warning through the public Java API; sampling changes and EOI checks cannot robustly repair it. A vetted strict JPEG validator/decoder exposing entropy completion is still required and is not implemented here. No universal JPEG integrity claim is made. This branch is BLOCKED even if ordinary photographs compress successfully.

Read-only independent review confirmed the mechanism against Android15 Skia/libjpeg:
- https://android.googlesource.com/platform/external/skia/+/refs/tags/android-15.0.0_r1/src/codec/SkJpegDecoderMgr.cpp
- https://android.googlesource.com/platform/external/libjpeg-turbo/+/refs/tags/android-15.0.0_r1/jdhuff.c

The review's other Important finding (role loss stranding READY recovery state before transport) is addressed through durable local failure before provider access; actual callback outcomes are likewise journaled independently of current role. No journal schema or phase was added. Tests use real sandbox preferences and a provider that rejects any unauthorized access. Callback identity, replay protection and Android submission contracts remain unchanged.

## Reproduction

Use JDK17, Gradle9.8.0 and SDK37 with build-tools36.0.0. Bootstrap official Maven dependencies once if absent. Host native-graphics tests use Robolectric SDK35 plus SDK27 for the legacy path; provide their cached android-all-instrumented JARs in ROBOLECTRIC_SDK_DIR. No credentials are required. Final reproduction uses --offline --no-daemon --max-workers=1 and forces test execution; runtime SDK resolution is also offline. Linux x86_64 was the Codex host; this is not Termux/ARM64 physical evidence.

```
export ROBOLECTRIC_SDK_DIR=/path/to/cached-robolectric-sdk-jars
bash qualification/v23/reproduce.sh "$(git rev-parse HEAD)" /outside/worktree/evidence
```

Optional SENTINEL_AAPT2_ARM64 overrides AAPT2 when using the user's existing ARM64 build environment. This script does not install APKs, send MMS, change roles or dispatch GitHub Actions. It continues after test failure to build experimental artifacts, records every exit status and ultimately exits nonzero if any command failed.

## S24+ isolated lab procedure and return to v22

Keep `com.sentinel.quantum` v22 installed. Do not install the main v23 APK or its main-package test APK without separate explicit authorization. The isolated lab APK and its matching test APK are the only installation candidates; they declare no SMS/dialer role and cannot send MMS. Use the current ADB address from wireless-debugging settings, never an old port. Install lab first, then its test APK with `adb install -r -t`, then run:

```
adb -s DEVICE shell am instrument -w -r com.sentinel.quantum.mmslab.v23.test/androidx.test.runner.AndroidJUnitRunner
```

The actual device result must be recorded, including the entropy-corruption case. Do not substitute the historical four successful phone tests for these new cases. No v23 physical tests were executed by Codex. No real MMS may be triggered automatically. Returning to v22 means closing the isolated lab and reopening the already-installed Sentinel; no downgrade, data clearing, role change or production installation is necessary.

Artifacts and exact hashes are recorded by VALIDATION_LEDGER.json in the outside evidence directory. Debug keys are existing local keys, not production signing credentials; no keystore is exported. APK assembly is not a delivery or operator proof. No main merge or official release.
