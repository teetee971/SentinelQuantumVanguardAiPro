# APK Distribution Directory

This directory contains the compiled Android APK for **Sentinel Quantum Vanguard AI Pro**.

## Automated Build

The APK is automatically built and published by the GitHub Actions workflow:
- **Workflow**: `.github/workflows/build-and-publish-apk.yml`
- **Trigger**: Push to `main` or `develop` branches (when `android-app/**` changes)
- **Manual trigger**: Available via "Run workflow" button on GitHub Actions

## APK File

- **Filename**: `SentinelQuantumVanguardAIPro.apk`
- **Build Type**: Release (signed with debug keystore)
- **Download URL**: `https://teetee971.github.io/SentinelQuantumVanguardAiPro/apk/SentinelQuantumVanguardAIPro.apk`

## How It Works

1. GitHub Actions builds the React Native Android app
2. Gradle compiles the release APK
3. APK is renamed to `SentinelQuantumVanguardAIPro.apk`
4. APK is automatically committed to this directory
5. GitHub Pages serves the APK for download

## Installation

On Android devices:
1. Visit the GitHub Pages console
2. Click "Télécharger l'APK" button
3. Allow installation from unknown sources if prompted
4. Install and launch the app

## Manual Build (Local)

To build manually:
```bash
cd android-app
npm install
cd android
./gradlew assembleRelease
# APK located at: app/build/outputs/apk/release/app-release.apk
```
