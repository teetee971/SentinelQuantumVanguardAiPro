# Security Policy — Sentinel Quantum Vanguard AI Pro

## Scope

Sentinel Quantum Vanguard AI Pro contains a static web/PWA surface and a separate native Android application under `native-android-app/`.

Security validation covers source integrity, project isolation, dependency/workflow controls, deterministic security tests, and Android release signing.

No claim of production certification is made solely from the presence of a workflow or documentation. Validation requires executable evidence.

## Project Isolation

Sentinel Quantum Vanguard AI Pro is strictly independent from external applications and projects.

The repository must not introduce imports, packages, configuration, secrets, Firebase resources, deployment coupling, or runtime integration belonging to an external project.

Isolation is checked by repository isolation controls and regression tests. Changes that weaken or bypass these controls are not acceptable.

## Security Architecture

Critical actions such as block, contain, isolate, delete, quarantine, or disable are policy-gated. The decision layer does not directly execute sensitive actions.

Critical actions require, as applicable, valid policy authorization, trustworthy evidence, bounded uncertainty, safe simulation, target authorization, and human validation.

AI components are decision-support components and must not receive unrestricted authority over sensitive actions.

## Secrets and Android Signing

The active Android release workflow uses these GitHub Actions secrets:

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

Secrets must never be committed to the repository or written to normal logs. The temporary keystore is created with restrictive permissions and removed after the build, including failure paths.

The canonical Android workflow is `.github/workflows/android-release.yml` and builds from `native-android-app/`.

## GitHub Actions Supply Chain

External GitHub Actions references are pinned to immutable 40-character commit SHAs. The repository includes a dedicated pinning check.

Security workflows must retain least-privilege permissions and must not disable security gates merely to work around CI infrastructure failures.

## Validation

Security validation is evidence-based:

1. source change applied;
2. local/deterministic tests executed where available;
3. GitHub Actions runner actually executes steps;
4. results are inspected;
5. security and isolation gates pass.

A workflow that fails before its first step is an infrastructure/runner failure, not evidence that the security tests passed or failed.

## Responsible Disclosure

Report vulnerabilities privately when public disclosure could increase risk. For non-sensitive issues, GitHub Issues may be used. Do not include credentials, private keys, personal data, or other secrets in an issue.

## Known Validation Constraints

GitHub-hosted CI has previously exhibited failures before any job step executed. Until a real runner executes the relevant jobs successfully, CI-dependent claims remain unvalidated.

This constraint does not justify weakening security checks.

## Current References

- `AUDIT.md` — current audit source of truth
- `docs/RELEASE_BUILD_GUIDE.md` — Android build guidance
- `docs/PRODUCTION_RELEASE_GUIDE.md` — release process
- `.github/workflows/android-release.yml` — active Android release workflow
- `.github/workflows/security-fuzz.yml` — active security fuzz workflow
- `.github/workflows/project-isolation.yml` — project isolation workflow
- `.github/workflows/sentinel-isolation.yml` — Sentinel isolation workflow

**Last reviewed:** September 2026
