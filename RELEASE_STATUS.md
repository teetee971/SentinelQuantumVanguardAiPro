# RELEASE STATUS — CURRENT

This file previously described an old v1.0.0 release attempt, obsolete workflows and a branch that are no longer the current release path. That information is historical and is retired as an operational instruction.

## Qualification dated 2026-11-07

**NOT READY FOR PLAY UPLOAD.** Candidate 24 / 1.0.10 is separate from stable v22 on the S24+. See [current qualification](docs/GOOGLE_PLAY_QUALIFICATION_20261107.md). No key, role, installed app or main branch is changed. Confirm this code exceeds every version already used in Play Console. Historical release workflow below is not authorization to dispatch it.

Signed release also requires the public `-PsentinelAccountDeletionUrl=https://…/delete-account` configuration and a real verified account/data deletion service. No URL is fabricated in source; optional account login fails closed by default. This does not implement backend deletion or prove its operation.

## Current release path

Android source of truth: `native-android-app/`

Release workflow: `.github/workflows/android-release.yml`

Trigger: version tag `v*`

Android baseline: `compileSdk 37`, `targetSdk 36`, `minSdk 24`, `versionCode 24`, `versionName 1.0.10`, JDK 17, AGP 9.4.1, Kotlin 2.4.20 and Gradle 9.8.0.

The workflow requires the tag to point exactly to the current `main` head and to match `versionName`, validates production signing secrets, builds the signed release APK and signed release AAB, exports the resolved Android `releaseRuntimeClasspath` dependency graph, generates SHA-256 checksums and signer evidence, binds those artifacts plus the SBOM to `release-evidence.json`, uploads the evidence bundle and creates a draft GitHub Release.

The release build refuses to proceed without explicit signing configuration and has no debug-signing fallback.

## Current signing secrets

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Validation status

Android CI is executing again on `main`: the `Build Native Android APK` and `Build AAB Play Console` workflows run on GitHub-hosted runners, and the `android-release.yml` workflow is ready for signed tag builds of both APK and AAB once the signing secrets above are configured.

A release is still only considered CI-validated when the relevant workflows have executed successfully on the exact release commit, as described in `RELEASE_CHECKLIST.md`.

Do not use the historical v1.0.0 instructions in this file to trigger or publish a release.
