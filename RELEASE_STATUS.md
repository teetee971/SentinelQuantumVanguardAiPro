# RELEASE STATUS — CURRENT

This file previously described an old v1.0.0 release attempt, obsolete workflows and a branch that are no longer the current release path. That information is historical and is retired as an operational instruction.

## Current release path

Android source of truth: `native-android-app/`

Release workflow: `.github/workflows/android-release.yml`

Trigger: version tag `v*`

Android baseline: `compileSdk 37`, `targetSdk 36`, `minSdk 24`, JDK 17, AGP 9.4.0 and Gradle 9.7.1.

The workflow requires the tag to point exactly to the current `main` head, validates production signing secrets, builds the signed release APK and signed release AAB, generates SHA-256 checksums and signer evidence for both, uploads the artifacts and creates a draft GitHub Release.

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
