# RELEASE STATUS — CURRENT

This file previously described an old v1.0.0 release attempt, obsolete workflows and a branch that are no longer the current release path. That information is historical and is retired as an operational instruction.

## Current release path

Android source of truth: `native-android-app/`

Release workflow: `.github/workflows/android-release.yml`

Trigger: version tag `v*`

The workflow verifies that the tag commit is reachable from `main`, validates production signing secrets, builds the signed release APK, generates SHA-256 checksums, uploads artifacts and creates the GitHub Release.

## Current signing secrets

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Validation status

The repository currently has a GitHub Actions runner/infrastructure blocker: recent jobs have failed before executing their first step. Therefore no current release is certified as successfully built or security-validated by CI until a runner executes the complete validation chain.

Do not use the historical v1.0.0 instructions in this file to trigger or publish a release.