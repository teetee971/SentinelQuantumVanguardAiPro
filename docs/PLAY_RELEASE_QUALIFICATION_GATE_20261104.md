# Google Play qualification — execution gate (2026-11-04)

Status: **NOT READY**. Source changes are not a verified artifact.

## Baseline
- Qualification branch: `qualification/play-android-20261104`, forked from `work/phone-core-v1-20261029`.
- Android package: `com.sentinel.quantum`; versionCode 10 / versionName 1.0.9 as inspected in `native-android-app/app/build.gradle`.
- `releaseUnsigned` explicitly strips signing; **never upload it to Play**.
- Release signing uses `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; keep secrets outside Git.
- Phone Core and D.1 branches diverge; do not bulk merge without per-file review.
- Auth0 login exists, but verification of `email_verified` and server authorization is not implemented in the UI/API. `ConnectedServiceAccessPolicy` is a local, **not-yet-wired** presentation helper, not a security boundary.

## Qualification sequence
1. Review branch diff, merge only selected D.1 patches, resolve conflicts, preserve Room schema and phone/SMS role contracts.
2. Wire verified-email state from a trusted identity source; validate tokens and entitlement on every backend route. Provide verification/resend, signout and account deletion. Never require login for local Phone Core.
3. Build and execute tests from the same immutable commit; record SHA, Gradle version, test XML counts, failures and warnings.
4. Run Android lint, unit tests, instrumented tests on physical S24+ for role acquisition/revocation, SMS/MMS, notifications, offline behavior and login.
5. Produce `bundleRelease` only with configured signing secrets, inspect signature and package/version, record SHA-256. Produce a corresponding debug/test APK from the same commit.
6. Audit manifest merged into **release** AAB, permission justification, AD_ID, dependencies/SDK inventory, all data transmissions, TLS and account deletion.
7. Complete Play declarations (Data Safety, App Access, ads ID, restricted SMS/call-log permissions), privacy policy, account deletion URL and accurate screenshots.
8. Upload signed AAB to **internal testing** first; review Play pre-launch report. Do not promote to production before evidence and policy clearance.

## Suggested Termux/proot checks (adapt SDK paths to installed environment)
```sh
git status --short
git rev-parse HEAD
cd native-android-app
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --offline --no-daemon --max-workers=1 --console=plain
./gradlew :app:lintRelease --offline --no-daemon --max-workers=1 --console=plain
# Configure signing secrets securely outside repository before the next command.
./gradlew :app:bundleRelease --offline --no-daemon --max-workers=1 --console=plain
```
Do not call the above checks PASS until their outputs and XML reports are inspected. An offline build may fail because dependencies or SDK components are missing.

## Stop-ship conditions
- Unverified email accepted for a connected service.
- Guest denied essential local security/telephony.
- Direct backend access bypasses auth/entitlement.
- AAB unsigned, mismatched package/version, or signature provenance unclear.
- Privacy/Data Safety assertions unsupported by release traffic and SDK inspection.
- Restricted permissions lack approved core use-case and policy justification.
- Broken login, verification, account deletion or test credentials.
