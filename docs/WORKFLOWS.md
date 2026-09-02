# GitHub Actions — workflow inventory

## Current source of truth

This document supersedes older workflow documentation. Only the workflow files currently present in `.github/workflows/` are operational.

## Active workflow roles

- `project-isolation.yml` — verifies Sentinel remains isolated from other projects and forbidden dependencies.
- `security-governance-validation.yml` — deterministic security-governance regression suite and governance fuzzing.
- `security-fuzz.yml` — authorized synthetic security fuzzing.
- `codeql-analysis.yml` — CodeQL analysis.
- `integrity-check.yml` — repository integrity checks.
- `frontend-validation.yml` — frontend validation.
- `build-native-android.yml` — builds the canonical Android project without publishing a release.
- `android-release.yml` — production Android release, tag-controlled and signed.

Other workflow files may exist for specialized validation; their actual presence and configuration in the repository are authoritative.

## Android release policy

The canonical Android project is `native-android-app/`.

Production release is performed only by `.github/workflows/android-release.yml` from a version tag matching the workflow policy. The workflow verifies tag ancestry from `main`, validates signing secrets, builds the release APK, generates SHA-256 checksums and publishes the release.

Production signing secrets are:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

No debug keystore is an acceptable production fallback.

## Supply-chain controls

Active third-party GitHub Actions are pinned to immutable commit SHAs. Ordinary workflows use least-privilege repository permissions. Release publication is isolated to the release workflow.

## Security boundary

Sentinel must remain completely separate from A KI PRI SA YÉ and from operational Firebase dependencies belonging to another project. The project-isolation scanner is the automated enforcement layer.

## CI status

Recent GitHub-hosted jobs have failed before their first step, including after a runner-image canary. This is an infrastructure-level blocker rather than evidence that the test suites failed. Until runners execute the steps and the suites pass, CI validation is pending.

## Historical references

Older documentation may mention `build-android.yml`, `release-apk.yml`, `release.yml`, `pages-deploy.yml` or `android-app/android/`. Those references are historical and are not current operating procedures.