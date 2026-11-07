# Test-only replacement kit — preparation, no installation

User device diagnostic at192.168.1.16:42175 identifies installed test APK SHA256 db7215b5b9124cf5f0bca9b5e8c5c85f0cfb30591c6ee120ede41e194d5a78b7, payload5368c3e3b8ba67e31ad56804dea532132f4b437cd46ebdeece552c8d9c2f9420: exact known legacy/unguarded v22 tests. No test executed. Main package still reports22/1.0.9. Evidence comes from the user's terminal; Codex cannot directly reach the private LAN.

A test-only delivery ZIP is provided at delivery/Sentinel-tests-only-2add1102.zip. SHA2561b598384342130897d37a402b88a1ad8509fb3183942dd01e36057c6a2e33045,1034333 bytes. Its eight flat entries contain the corrected cloud-debug AndroidTest APK, preparation scripts, metadata, payload verifier, README and SHA256SUMS. No main application APK, keystore or private key is included.

## Gates before any future test execution

1. Verify ZIP and inner SHA256SUMS; copy the already-pulled installed-tests.apk as installed-test-reference.apk. Its exact known signed hash is required.
2. Run verify-main-v22.sh DEVICE from Termux. It reads only the installed main APK's SHA256. Required known signed v22 hash10e70d21409daabcee0ba898703a1cb60897d127d22afffc84b6ca79f94390c8 comes from the user's successful v22 signing report. A version22 label alone is insufficient. Different hash or failed query: STOP and report, no compatibility assumption.
3. Inside the existing Ubuntu/proot, sign-tests-only.sh uses the existing local androiddebugkey. It validates old test signature, expected certificate68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2, source APK9defb9f68a40b2ce2554159e844ed32b3aab42a1aef0c1a84a9094b01924f748, new v2 signature, exact payload/native offsets and unchanged keystore. No key generation, no private-key export and no adb in this signing script. Existing outputs never overwritten.
4. Expected preparation result TEST_APK_SIGNED_NOT_INSTALLED. Return TEST_SIGNING_REPORT.json before proceeding. No device installation or test execution is performed by preparation. Only the seven isolated reputation cases are a later candidate; never the legacy APK or whole suite.

Default existing key path is /root/.android/debug.keystore in Ubuntu, overridable with SENTINEL_KEYSTORE. SDK apksigner.jar found through ANDROID_HOME or /root/android-sdk, overridable with APKSIGNER_JAR. Missing/mismatched existing key: STOP, never regenerate. Public certificate export is scratch-only; no existing certificate or keystore is altered.

## Executed host checks

- Initial signing scaffold:5 tests/5 failures, then5/0 using actual SDK apksigner/keytool and the EXISTING cloud debug key.
- Added read-only main identity verifier:8 tests/3 failures before implementation; final8 tests/0 failures/skips.
- Cases: real cryptographic signing and payload preservation with no installation, modified input rejection, main-package metadata rejection, no output overwrite, reference-certificate mismatch rejection; read-only main identity match, different main rejection and ADB-query failure rejection. ADB is simulated in these three identity cases, not a physical main-APK verification.
- Signing self-test uses the existing host debug certificate fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6 and an adapted TEMPORARY metadata fixture. It does not claim use of the S24+ key. Key bytes are unchanged before/after every test and a main-file sentinel remains untouched. The shipped metadata remains pinned to the actual S24+ reference/certificate.
- ZIP exact entry allowlist and all inner SHA256 verified. Source code2add1102 Android tree remains unchanged; no new main APK build. Existing Android unit1446 and compilation proofs remain valid for unchanged Android sources.

Commands: source /workspace/toolchains/env.sh; python3 qualification/test-guard/test_sign_tests_only.py; bash -n qualification/test-guard/test-only-delivery/{sign-tests-only,verify-main-v22}.sh. Packaging: python3 qualification/test-guard/prepare_test_only_kit.py INPUT_APK NEW_ZIP. It refuses to overwrite and pins cloud input SHA9def; reproducible ZIP timestamps and flat names.

Physical state: main exact hash NOT YET VERIFIED, local S24+ signing NOT YET EXECUTED, new AndroidTest APK NOT INSTALLED, seven Android tests NOT EXECUTED. Main v22 and all app data preserved. These are explicit remaining gates, not PASS claims.
