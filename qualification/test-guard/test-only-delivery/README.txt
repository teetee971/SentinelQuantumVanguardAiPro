SENTINEL — TEST APK ONLY — SIGNED NOT INSTALLED

This kit contains no main application APK and never installs or runs Android tests.
It prepares only com.sentinel.quantum.test, containing the seven isolated reputation
storage cases. Source code2add1102; production sources remain the stable v22 base.

1. Verify SHA256SUMS in the extracted kit.
2. Copy the diagnostic's installed-tests.apk to installed-test-reference.apk.
   Required signed reference SHA256:
   db7215b5b9124cf5f0bca9b5e8c5c85f0cfb30591c6ee120ede41e194d5a78b7
3. In Termux, run verify-main-v22.sh DEVICE. It reads the installed main APK hash only.
   It requires the exact signed stable v22 APK previously reported:
   10e70d21409daabcee0ba898703a1cb60897d127d22afffc84b6ca79f94390c8
   If different, STOP and report the output; do not assume compatibility from version22 alone.
4. Bind the kit into the existing Ubuntu/proot and run sign-tests-only.sh there.
   Existing /root/.android/debug.keystore (or SENTINEL_KEYSTORE) and SDK apksigner.jar
   (or APKSIGNER_JAR) are required. Uses existing androiddebugkey/debug credentials.
   Missing/mismatched key: STOP, never generate or replace a key.

The certificate must match the installed reference:
68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2

The script verifies source digest, installed reference signature, local certificate,
new v2 signature, exact ZIP payload, content fingerprint, native offsets, and unchanged
keystore. Existing output files are never overwritten. Public certificate export is
scratch-only; private keys are never exported or included in this kit.

Expected result: TEST_APK_SIGNED_NOT_INSTALLED in TEST_SIGNING_REPORT.json.
Output: app-local-isolated-androidTest.apk. No installation or device test has occurred.
Return the JSON report before the next step. Do not run the legacy test APK or a full
instrumentation suite. Do not use sentinelAllowDestructive on the personal S24+.
Only the isolated CommunicationReputationStorageInstrumentationTest is a later candidate.
PhoneCoreSetupResumeInstrumentationTest remains reserved for disposable devices.
