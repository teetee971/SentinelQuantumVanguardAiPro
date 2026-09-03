# GitHub Actions — workflow inventory

## Current source of truth

This document supersedes older workflow documentation. Only workflow files currently present in `.github/workflows/` are operational.

## Active workflows

- `ai-governance-validation.yml` — AI governance regression validation.
- `android-release.yml` — signed Android release on version tags.
- `build-native-android.yml` — canonical Android build and validation artifact.
- `codeql-analysis.yml` — CodeQL security analysis.
- `defender-for-devops.yml` — Microsoft Defender for DevOps validation.
- `frontend-validation.yml` — frontend build and validation.
- `integrity-check.yml` — repository integrity, secret-pattern and isolation checks.
- `osint-validation.yml` — authorized OSINT validation.
- `project-isolation.yml` — project isolation verification.
- `security-fuzz.yml` — deterministic authorized security fuzzing.
- `security-governance-validation.yml` — security-governance regression suite and fuzzing.
- `security-validation.yml` — security scenario catalog validation and safe scenario execution.
- `sentinel-isolation.yml` — dedicated Sentinel isolation regression control.

No deleted workflow name is an alternative execution path. The files listed above are the complete current inventory.

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

Active third-party GitHub Actions are pinned to immutable commit SHAs. Workflows use least-privilege repository permissions appropriate to their tasks. Release publication is isolated to the release workflow.

## Security boundary

Sentinel must remain completely separate from external projects and from operational dependencies belonging to another project. The project-isolation scanners are the automated enforcement layer.

## CI status

Recent GitHub-hosted jobs have failed before their first step, including after runner-image testing. This is an infrastructure-level blocker rather than evidence that the test suites failed. Until runners execute the steps and the suites pass, CI validation is pending.

## Maintenance rule

When a workflow is deleted or renamed, update this inventory in the same change. Do not retain dead workflow names as operating instructions.

**Last reviewed:** September 2026
