# Release build guide

## Source of truth

The canonical Android production project is `native-android-app/`.

The active production release workflow is `.github/workflows/android-release.yml`. Historical workflows such as `release-apk.yml`, `release.yml` and `build-android.yml` are no longer active and must not be used as instructions.

## Release policy

Production Android releases are tag-controlled. The release workflow requires the tag to point exactly to the current head of `main` and to match the Android `versionName`.

The job targets the GitHub environment `android-production`. Configure this environment with required reviewers and restrict deployment to protected version tags before adding production secrets.

Production signing requires these GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

A debug keystore must never be used for a production release.

The workflow decodes the keystore into a protected temporary location, builds from `native-android-app/`, verifies the unique APK and its signing certificate, generates and rechecks SHA-256 checksums, uploads the evidence, creates a **draft** GitHub Release, and cleans the temporary keystore with an always-run cleanup step. Publishing the draft remains a separate human decision after device testing.

## Local verification

Use the Gradle wrapper in `native-android-app/`. Never commit signing credentials, keystores, or generated release secrets.

Before distribution, verify:

1. The APK is a release build.
2. The APK is signed with the intended production certificate.
3. The SHA-256 checksum matches the distributed APK.
4. The source commit exactly matches the intended tag.
5. Security and isolation validation has passed in CI.

Après téléchargement de tous les fichiers du brouillon dans un même dossier, revérifier le lot indépendamment :

```text
node scripts/verify-android-release-evidence.js --root /chemin/du-lot --evidence release-evidence.json
```

Le vérificateur recalcule les empreintes, contrôle la provenance du workflow et exige exactement un APK, son checksum, son rapport de certificat et le SBOM référencé. Il ne remplace pas `apksigner` ni l’essai sur appareil réel.

## CI security requirements

Ordinary build and validation workflows use read-only repository permissions. Release publication is the only workflow that requires repository write permission.

All third-party GitHub Actions in active workflows must remain pinned to immutable 40-character commit SHAs.

Never interpolate untrusted pull-request data directly into shell commands. Never expose production signing secrets to pull-request validation jobs.

## Validation status

A code correction is not equivalent to a successful CI run. The validation workflows for PRs #386, #390 and #391 executed successfully on 9 September 2026. A future signed release still requires its own successful tag workflow, protected-environment approval and real-device evidence.

## Canonical paths

- Android project: `native-android-app/`
- Android build workflow: `.github/workflows/build-native-android.yml`
- Android production release: `.github/workflows/android-release.yml`
- Security governance: `.github/workflows/security-governance-validation.yml`
- Fuzzing: `.github/workflows/security-fuzz.yml`
- Sentinel isolation: `.github/workflows/sentinel-isolation.yml`
- Action supply-chain pinning: `scripts/check-github-actions-pinning.js`
- Sentinel isolation scanner: `scripts/check-sentinel-isolation.js`
