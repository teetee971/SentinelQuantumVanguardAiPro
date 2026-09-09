# GitHub Actions — workflow inventory

## Current source of truth

Only workflow files currently present in `.github/workflows/` are operational. Historical workflow names are not execution paths.

## Active workflows

- `ai-governance-validation.yml` — AI governance regression validation.
- `android-release.yml` — signed Android release on version tags.
- `build-aab-playconsole.yml` — unsigned Android App Bundle build and packaging-policy validation, intermediate artifact toward Play Console publication.
- `build-native-android.yml` — canonical Android build and validation artifact.
- `codeql-analysis.yml` — CodeQL security analysis.
- `frontend-validation.yml` — frontend build, static-link and public-claim validation.
- `integrity-check.yml` — repository integrity, secret-pattern and isolation checks.
- `osint-validation.yml` — authorized OSINT validation.
- `security-fuzz.yml` — deterministic authorized security fuzzing.
- `security-governance-validation.yml` — security-governance regression suite and fuzzing.
- `security-validation.yml` — security scenario catalog validation and safe scenario execution.
- `sentinel-continuous-security.yml` — scheduled hourly security, isolation, supply-chain, static-link and build validation.
- `sentinel-isolation.yml` — dedicated Sentinel isolation regression control.

The former Windows/.NET validation workflow has been removed. It must not be recreated as a parallel validation chain without a documented architectural need.

## Android release policy

The canonical Android project is `native-android-app/`.

The Android baseline is `compileSdk 37`, `targetSdk 36`, `minSdk 24`, JDK 17, Android Gradle Plugin 9.4.0 and Gradle 9.6.

Production release is prepared only by `.github/workflows/android-release.yml` from a version tag matching the Android `versionName` and pointing to the current `main` head. The job uses the `android-production` environment, validates signing secrets, records the certificate, verifies the checksum and creates a draft release for human review and device testing.

Production signing secrets are:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

No debug keystore is an acceptable production fallback.

## Supply-chain controls

Active third-party GitHub Actions references are pinned to immutable commit SHAs. Workflows use least-privilege repository permissions appropriate to their tasks. Release publication is isolated to the release workflow.

The hourly Sentinel loop is read-only at repository scope and does not grant `security-events: write` because its current checks do not publish security events.

## Security boundary

Sentinel must remain completely separate from external projects and from operational dependencies belonging to another project. `sentinel-isolation.yml`, `sentinel-continuous-security.yml` and `scripts/check-sentinel-isolation.js` form the automated isolation controls.

## CI status

The validation workflows for PRs #386, #390 and #391 completed successfully on 9 September 2026, including APK/AAB builds, CodeQL, frontend, gouvernance, isolation, fuzzing and Lighthouse pre-production. This does not replace a signed tag workflow or tests on physical devices.

## Maintenance rule

When a workflow is deleted or renamed, update this inventory in the same change. Do not retain dead workflow names as operating instructions.

**Last reviewed:** September 2026
