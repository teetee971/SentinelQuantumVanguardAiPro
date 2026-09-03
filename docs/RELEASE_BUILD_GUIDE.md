# Release build guide

## Source of truth

The canonical Android production project is `native-android-app/`.

The active production release workflow is `.github/workflows/android-release.yml`. Historical workflows such as `release-apk.yml`, `release.yml` and `build-android.yml` are no longer active and must not be used as instructions.

## Release policy

Production Android releases are tag-controlled. The release workflow verifies that the tagged commit is reachable from `main` before building.

Production signing requires these GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

A debug keystore must never be used for a production release.

The workflow decodes the keystore into a protected temporary location, builds from `native-android-app/`, verifies the APK, generates SHA-256 checksums, uploads artifacts, creates the GitHub Release, and cleans the temporary keystore with an always-run cleanup step.

## Local verification

Use the Gradle wrapper in `native-android-app/`. Never commit signing credentials, keystores, or generated release secrets.

Before distribution, verify:

1. The APK is a release build.
2. The APK is signed with the intended production certificate.
3. The SHA-256 checksum matches the distributed APK.
4. The source commit exactly matches the intended tag.
5. Security and isolation validation has passed in CI.

## CI security requirements

Ordinary build and validation workflows use read-only repository permissions. Release publication is the only workflow that requires repository write permission.

All third-party GitHub Actions in active workflows must remain pinned to immutable 40-character commit SHAs.

Never interpolate untrusted pull-request data directly into shell commands. Never expose production signing secrets to pull-request validation jobs.

## Validation status

A code correction is not equivalent to a successful CI run. The repository currently has a GitHub Actions infrastructure blocker in which some jobs have failed before executing their first step. Until the validation jobs actually execute and pass, CI security validation remains pending.

## Canonical paths

- Android project: `native-android-app/`
- Android build workflow: `.github/workflows/build-native-android.yml`
- Android production release: `.github/workflows/android-release.yml`
- Security governance: `.github/workflows/security-governance-validation.yml`
- Fuzzing: `.github/workflows/security-fuzz.yml`
- Sentinel isolation: `.github/workflows/sentinel-isolation.yml`
- Action supply-chain pinning: `scripts/check-github-actions-pinning.js`
- Sentinel isolation scanner: `scripts/check-sentinel-isolation.js`
