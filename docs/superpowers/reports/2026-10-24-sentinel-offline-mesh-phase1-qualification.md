# Sentinel Offline Mesh — phase 1 qualification

Date: 2026-10-24
Branch: `feature/sentinel-offline-mesh-foundation-20261024`
Reference baseline: `9c3ec9240150ba903f6757d5db6ba60e5c320db0`
Qualification commit: supplied in the final Git handoff (`git rev-parse HEAD`);
it is not duplicated here because changing this line would change the commit
SHA itself.

## Delivered

- Bidirectional GATT data path: client writes to `CLIENT_TO_SERVER`; server
  emits `SERVER_TO_CLIENT` notifications after CCCD subscription.
- ATT/radio completion handling, bounded stop-and-wait ACKs, retry deadlines,
  interruption invalidation, fragmentation, reassembly, duplicate handling,
  and per-peer/session callback rejection.
- Explicit authenticated channel: ECDH P-256, ECDSA transcript signatures,
  HKDF-SHA-256, AES-256-GCM, direction keys, counters, and replay rejection.
- AndroidKeyStore bridge using `OwnerDeviceKeyManager`; private data remains
  locked when no identity/trust factory is supplied.
- Unit and Android-instrumented contract tests. The instrumented test is a
  service-contract test, not a physical BLE test.

## Executed commands and evidence

All Gradle commands were run from `native-android-app` with JDK 17 and the
configured Android SDK.

| Command | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.security.OfflineMesh*' --no-daemon --console=plain` | PASS — 65 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew :app:testDebugUnitTest --no-daemon --console=plain` | PASS — 1,382 tests, 0 failures, 0 errors, 0 skipped; 65 Offline Mesh tests |
| `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --console=plain` | PASS — APK assembled and instrumented Kotlin compiled |
| `./gradlew :app:lintDebug --no-daemon --console=plain` | BLOCKED — 3 pre-existing `SelectedApkInspector.kt` API-28 errors; 217 warnings, 19 hints; no Offline Mesh lint errors |
| `git diff --check` | PASS on the staged/committed tree |

The debug package step reported unstripped native libraries and packaged them;
that was a warning, not a build failure. No release signing or publishable
artifact was produced.

## Test coverage

`OfflineMeshGattCryptoTest` covers mutual authentication, explicit trust,
transcript/ciphertext tampering, AES-GCM replay rejection, and the absent
identity default. `OfflineMeshGattSecureTransportTest` covers encrypted
bidirectional fragmented messages, locked operation, and stale generations.
The existing transport/reassembler tests cover ACKs, radio failures, bounded
retries, out-of-order fragments, duplicates, expiry, and delivery-once.
`OfflineMeshGattContractInstrumentationTest` covers the Android characteristic
and CCCD contract and confirms that CCCD alone does not authorize private data.

## Limitations and remaining qualification

- No `adb` executable or connected Android device is available in this
  environment. Instrumented tests were compiled but not executed.
- No two-device BLE exchange, notification-loss test, MTU fallback test,
  permission-revocation test, background scheduling test, or radio capture was
  observed. Physical qualification remains mandatory.
- The secure channel is a small versioned JCA protocol, deliberately not
  claimed to be Noise. Independent cryptographic/protocol review and a future
  vetted Noise implementation remain open gates.
- The current frame size assumes the requested 247-byte ATT MTU. A lower
  negotiated MTU is rejected fail-closed by the adapters and needs a dedicated
  compatibility qualification before broader support is claimed.
- `lintDebug` remains blocked by unrelated existing API-level errors in
  `SelectedApkInspector.kt`; that file was not modified.

Status: **NOT READY**. No physical success, cryptographic production approval,
or merge is implied by the results above. PR #1739 remains a draft.
