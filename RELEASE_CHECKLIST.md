# Release Checklist — Sentinel Quantum Vanguard AI Pro

This checklist describes the current release path. It must not be used to infer that a release is already validated.

## 1. Source integrity

- [ ] Changes are on `main` or on a reviewed release commit.
- [ ] No secrets, keystores, credentials, or private data are committed.
- [ ] Sentinel remains strictly isolated from external applications and projects.
- [ ] GitHub Actions references pass the repository pinning check.
- [ ] Sentinel isolation checks contain no forbidden cross-project dependency or configuration.

## 2. Android source

- [ ] Canonical Android source is `native-android-app/`.
- [ ] Package/application configuration matches the current project.
- [ ] No legacy Android source tree or obsolete flavor is required.
- [ ] `minSdk` is 23, `targetSdk` is 36 and `compileSdk` is 37.
- [ ] Build uses JDK 17, AGP 9.4.0 and Gradle 9.6.
- [ ] Release signing uses only the current secrets:
  - `KEYSTORE_BASE64`
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`

## 3. Build

- [ ] Android release build executes successfully.
- [ ] APK output exists and is non-empty.
- [ ] APK signature is verified with Android tooling.
- [ ] SHA-256 checksum is generated and matches the APK.
- [ ] Installation/runtime smoke test succeeds on a supported Android device or emulator.

## 4. Security validation

- [ ] `npm run test:security-fuzz` succeeds.
- [ ] Security governance tests succeed.
- [ ] AI governance tests succeed where applicable.
- [ ] Sentinel isolation tests succeed.
- [ ] No security gate was disabled or weakened to obtain a passing result.

## 5. CI evidence

A release is not considered CI-validated merely because a workflow file exists.

Required evidence:

1. workflow starts on a GitHub-hosted runner;
2. job steps actually execute;
3. relevant tests/builds complete;
4. results are inspected;
5. failures, if any, are resolved or explicitly accepted with documented risk.

A failure before the first step is a runner/infrastructure failure and does not constitute test evidence.

## 6. Release publication

- [ ] Release tag follows the repository's current semantic-versioning policy.
- [ ] Tag commit is reachable from `main`.
- [ ] `.github/workflows/android-release.yml` executes successfully.
- [ ] APK and `.sha256` are published as release assets.
- [ ] Release notes describe only verified functionality.
- [ ] No historical production-readiness claim is copied without current evidence.

## 7. Final security gate

Do not publish a release as security-validated if any required security, isolation, signing, or build evidence is missing.

Do not weaken controls to bypass infrastructure failures.

## Current references

- `AUDIT.md`
- `SECURITY.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `docs/PRODUCTION_RELEASE_GUIDE.md`
- `.github/workflows/android-release.yml`
- `.github/workflows/security-fuzz.yml`
- `.github/workflows/sentinel-isolation.yml`

**Last reviewed:** September 2026
