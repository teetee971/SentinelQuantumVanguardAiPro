# APK DELIVERY MANIFEST — CURRENT SOURCE OF TRUTH

## Important

The former contents of this document described deleted Android workflows, obsolete paths and a debug-keystore fallback. Those claims are retired and must not be used for production operations.

## Current Android project

The canonical Android project is `native-android-app/`.

## Current workflows

Build validation without publication: `.github/workflows/build-native-android.yml`

Production release: `.github/workflows/android-release.yml`

The production release workflow is tag-controlled (`v*`). It verifies that the tagged commit is reachable from `main`, validates production signing secrets, builds the release APK, generates SHA-256 checksums, uploads artifacts and creates the GitHub Release.

## Signing

Production signing uses GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

A debug keystore must never be used as an implicit production fallback.

## Validation rule

An APK is not considered production-ready merely because a workflow exists. The build, signature, checksum, source tag and security/isolation validation must be verified by an actually executed CI run.

## Historical documentation

References in older documentation to `android-app/android/`, `release-apk.yml`, `release.yml`, `build-android.yml` or `pages-deploy.yml` are historical and must not be treated as active instructions.

## Current CI status

GitHub Actions has recently experienced a runner/infrastructure blocker in this repository where jobs failed before executing their first step. Until a runner executes the validation steps and they pass, CI validation remains pending.