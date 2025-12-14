# Sentinel Quantum Vanguard AI Pro - Native Android App

## 🛡️ Overview

Native Android application for Sentinel Quantum Vanguard AI Pro cybersecurity platform.

**WebView-based architecture** embedding the complete Sentinel static site with native Android navigation.

## ✅ Features

- **Native Android UI** with Material Design 3
- **Bottom Navigation Menu**: Accueil, Téléphone Sécurisé, SOC/EDR, System Status, Permissions
- **Secure WebView** with JavaScript interface
- **Offline-first**: All HTML/CSS/JS embedded in assets
- **No spyware, no surveillance** - Defensive cybersecurity only
- **RGPD Compliant** - Opt-in permissions, transparency dashboard

## 📦 Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Gradle 8.0+

### Build APK (Debug)

```bash
cd android
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Build APK (Release)

```bash
./gradlew assembleRelease
```

**Note**: Release APK requires signing. Configure `keystore.properties` with your signing key.

### Install on Device

```bash
./gradlew installDebug
```

Or via ADB:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Android Requirements

- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Permissions**: Internet, Network State (required), others optional/opt-in

## 🔧 Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/sentinel/quantumvanguard/
│   │   │   ├── MainActivity.kt          # Main WebView activity
│   │   │   ├── SentinelApplication.kt   # App class
│   │   │   └── SentinelJSInterface.kt   # JS bridge
│   │   ├── res/
│   │   │   ├── layout/                   # XML layouts
│   │   │   ├── menu/                     # Navigation menus
│   │   │   ├── values/                   # Strings, colors, styles
│   │   │   └── xml/                      # Data extraction rules
│   │   ├── assets/www/                   # Embedded static site
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 🌐 WebView Configuration

- **JavaScript**: Enabled (required for modals)
- **DOM Storage**: Enabled
- **File Access**: Local assets only
- **Network Access**: CDN allowed, no external tracking
- **Cache Mode**: Default (respects HTTP headers)

## 🔐 Security & Privacy

- ✅ **No data collection** - All processing local
- ✅ **No analytics** - No third-party SDKs
- ✅ **No ads** - Clean, ad-free experience
- ✅ **Backup disabled** - No cloud backup of app data
- ✅ **HTTPS only** - CleartextTraffic disabled
- ✅ **ProGuard enabled** - Code obfuscation in release

## 📋 Permissions Explained

| Permission | Status | Purpose |
|-----------|--------|---------|
| `INTERNET` | ✅ Required | Load web content, threat intel updates |
| `ACCESS_NETWORK_STATE` | ✅ Required | Check connectivity |
| `READ_PHONE_STATE` | ⚠️ Opt-in | Call metadata analysis |
| `READ_CALL_LOG` | ⚠️ Opt-in | Suspect call detection |
| `READ_CONTACTS` | ⚠️ Opt-in | Trusted contacts whitelist |
| `READ_SMS` | ⚠️ Opt-in | Smishing detection |
| `CALL_PHONE` | ⚠️ Opt-in | Emergency SOS |
| `ACCESS_FINE_LOCATION` | ⚠️ Opt-in | Panic mode geolocation |
| `RECORD_AUDIO` | ⚠️ Opt-in | Call recording (legal disclaimer) |
| `FOREGROUND_SERVICE` | ⚠️ Opt-in | Real-time protection |

**All opt-in permissions** are declared in manifest but NOT requested by default.
Users must explicitly enable features that require sensitive permissions.

## 🚀 GitHub Actions CI/CD

Workflow file: `.github/workflows/android-build.yml`

**Automatic APK build** on push to main/develop branches.

Download artifacts from Actions tab after successful build.

## 📄 License

See root LICENSE file.

## ⚠️ Disclaimer

**Defensive cybersecurity platform**
- No mass surveillance
- No global interception
- Probabilistic approach
- RGPD/Play Store compliant
- Educational purpose

---

**Version**: 1.0.0-beta  
**Package**: com.sentinel.quantumvanguard  
**Build**: Native Kotlin + WebView
