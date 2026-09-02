# Android Release Keystore Setup

This guide covers the signing material used by the current Android release pipeline.

## Canonical Android project

The Android source is `native-android-app/`.

The active release workflow is `.github/workflows/android-release.yml` and builds the `assembleRelease` variant.

## Required GitHub Actions secrets

Configure these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

Do not use the obsolete `RELEASE_*` secret names in new configuration.

## Generate a PKCS12 keystore

Use a secure local environment and choose unique passwords. Never commit the keystore or password values.

```bash
keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore release.keystore \
  -alias sentinel-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Verify the resulting keystore locally with `keytool -list` before storing it securely.

## Encode for GitHub Actions

Linux/macOS:

```bash
base64 -w 0 release.keystore > release.keystore.base64.txt
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64.txt
```

Treat the generated Base64 file as secret material and remove it after securely transferring the value to GitHub Actions Secrets.

## Security requirements

- Never commit `release.keystore` or its Base64 representation.
- Never commit signing passwords.
- Never print secret values in CI logs.
- Keep an offline backup of the signing material in a secure vault.
- Restrict repository Actions administration to trusted maintainers.
- Rotate credentials if compromise is suspected.

## Release verification

The active workflow validates that the release tag points to a commit reachable from `main`, validates the required signing secrets, builds from `native-android-app/`, publishes the APK artifact, and generates SHA-256 checksums.

The repository must not describe an APK as production-validated until the corresponding build and security gates have actually executed successfully.

## Important project boundary

This signing configuration belongs exclusively to Sentinel Quantum Vanguard AI Pro. It must remain completely separate from A KI PRI SA YÉ and must not be reused across projects.

**Last reviewed:** September 2026
