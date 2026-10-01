# GitHub Actions — workflow inventory

## Current source of truth

Only workflow files currently present in `.github/workflows/` are operational. Historical workflow names are not execution paths.

## Active workflows

Inventaire exact des fichiers de workflow présents dans le dépôt :

- `ai-governance-validation.yml`
- `android-release.yml`
- `arcep-numbering-refresh.yml`
- `autonomous-maintenance.yml`
- `build-aab-playconsole.yml`
- `build-native-android.yml`
- `ci-canary-matrix.yml`
- `ci-smoke.yml`
- `cisa-kev-refresh.yml`
- `codeql-analysis.yml`
- `copilot-setup-steps.yml`
- `correlated-vulnerability-watch-validation.yml`
- `frontend-validation.yml`
- `integrity-check.yml`
- `intel-public-data-validation.yml`
- `lighthouse-live.yml`
- `lighthouse-preproduction.yml`
- `osint-validation.yml`
- `persistent-vulnerability-watch-validation.yml`
- `preproduction-final-gate.yml`
- `scheduled-vulnerability-collector-validation.yml`
- `scheduled-vulnerability-watch.yml`
- `security-fuzz.yml`
- `security-governance-validation.yml`
- `security-validation.yml`
- `sentinel-continuous-security.yml`
- `sentinel-isolation.yml`
- `sentinel-source-export.yml`
- `sentinel-watchdog.yml`
- `social-intelligence-ci.yml`
- `system-evolution-scout.yml`
- `wangiri-api.yml`

The former Windows/.NET validation workflow has been removed. It must not be recreated as a parallel validation chain without a documented architectural need.

## Android release policy

The canonical Android project is `native-android-app/`.

The Android baseline is `compileSdk 37`, `targetSdk 36`, `minSdk 24`, `versionCode 7`, `versionName 1.0.6`, JDK 17, Android Gradle Plugin 9.4.1, Kotlin 2.4.20 and Gradle 9.8.0.

Production release is prepared only by `.github/workflows/android-release.yml` from a version tag matching the Android `versionName` and pointing to the current `main` head. The job uses the `android-production` environment, validates signing secrets, builds signed APK and AAB artifacts, exports the resolved Android dependency graph, verifies signatures/checksums, binds SBOM + dependency inventory + binary evidence to `release-evidence.json`, and creates a draft release for human review and device testing.

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

CI status is SHA-specific and must be read from the workflow runs for the commit under review. A previous green run is not carried forward as evidence for a later commit. APK/AAB builds, CodeQL, frontend, governance, isolation, fuzzing, pre-production and Lighthouse are independent gates; none replaces the signed tag workflow or tests on physical devices.

## Maintenance rule

When a workflow is deleted or renamed, update this inventory in the same change. Do not retain dead workflow names as operating instructions.

**Last reviewed:** 1 October 2026
