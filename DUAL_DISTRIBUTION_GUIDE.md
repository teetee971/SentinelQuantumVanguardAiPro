# 📱 DUAL DISTRIBUTION - Institutional & Play Store

## 🎯 Overview

Sentinel Quantum Vanguard AI Pro provides **two distinct builds** for different deployment scenarios:

### 1. 🏢 **Institutional Build** (APK)
- **Distribution:** GitHub Releases, Private MDM, Direct Download
- **Target:** Defense, Enterprise, Government
- **Permissions:** Advanced (READ_CALL_LOG, READ_SMS, RECORD_AUDIO)
- **Package ID:** `com.sentinel.quantum.institutional`

### 2. 📱 **Public Build** (AAB)
- **Distribution:** Google Play Store
- **Target:** General Public
- **Permissions:** Google Play compliant (CallScreeningService)
- **Package ID:** `com.sentinel.quantum.public`

---

## 🏗️ Build Architecture

### Product Flavors Configuration

```gradle
android {
    flavorDimensions "distribution"

    productFlavors {
        institutional {
            dimension "distribution"
            applicationId "com.sentinel.quantum.institutional"
            versionNameSuffix "-institutional"
            buildConfigField "boolean", "ALLOW_CALL_LOG", "true"
            buildConfigField "boolean", "IS_INSTITUTIONAL_BUILD", "true"
        }

        public {
            dimension "distribution"
            applicationId "com.sentinel.quantum.public"
            versionNameSuffix "-public"
            buildConfigField "boolean", "ALLOW_CALL_LOG", "false"
            buildConfigField "boolean", "IS_INSTITUTIONAL_BUILD", "false"
        }
    }
}
```

### Build Variants Generated

| Variant | Type | Output | Use Case |
|---------|------|--------|----------|
| `institutionalDebug` | Debug APK | Development | Testing advanced features |
| `institutionalRelease` | Release APK | Production | Institutional deployment |
| `publicDebug` | Debug APK | Development | Testing Play Store features |
| `publicRelease` | Release AAB | Production | Google Play upload |

---

## 🔐 Permissions Comparison

### Institutional Build

**AndroidManifest (institutional flavor):**
```xml
<!-- Advanced Permissions -->
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

**Capabilities:**
- ✅ Full call log access
- ✅ SMS phishing detection
- ✅ Call recording
- ✅ Smart call handling
- ✅ Security scanning

### Public Build

**AndroidManifest (public flavor):**
```xml
<!-- Google Play Compliant - No restricted permissions -->
<!-- Uses CallScreeningService API instead -->
```

**Capabilities:**
- ✅ Call screening (via CallScreeningService)
- ✅ Caller ID display
- ✅ Basic spam detection
- ❌ No call log access
- ❌ No SMS reading
- ❌ No call recording

---

## 🚀 Build Commands

### Local Development

```bash
cd android-app/android

# Build Institutional APK (debug)
./gradlew assembleInstitutionalDebug

# Build Institutional APK (release)
./gradlew assembleInstitutionalRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore \
  -Pandroid.injected.signing.store.password=PASSWORD \
  -Pandroid.injected.signing.key.alias=ALIAS \
  -Pandroid.injected.signing.key.password=PASSWORD

# Build Public AAB (release)
./gradlew bundlePublicRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore \
  -Pandroid.injected.signing.store.password=PASSWORD \
  -Pandroid.injected.signing.key.alias=ALIAS \
  -Pandroid.injected.signing.key.password=PASSWORD
```

### Output Locations

```
android-app/android/app/build/outputs/
├── apk/
│   ├── institutionalDebug/
│   │   └── app-institutional-debug.apk
│   ├── institutionalRelease/
│   │   └── app-institutional-release.apk
│   ├── publicDebug/
│   │   └── app-public-debug.apk
│   └── publicRelease/
│       └── app-public-release.apk
└── bundle/
    └── publicRelease/
        └── app-public-release.aab
```

---

## 🤖 GitHub Actions Workflows

### Institutional APK Release

**Workflow:** `.github/workflows/android-release.yml`

**Trigger:**
```bash
# Tag-based
git tag v1.0.1
git push origin v1.0.1

# Manual dispatch
# GitHub UI → Actions → "Build & Release Android APK (INSTITUTIONAL)"
```

**Output:**
- `SentinelQuantumVanguardAIPro-INSTITUTIONAL-v1.0.1.apk`
- `institutional-apk-sha256.txt` (checksum)

**Release Tag:** `v*.*.*`

### Public AAB Release

**Workflow:** `.github/workflows/android-aab-public.yml`

**Trigger:**
```bash
# Tag-based
git tag play-v1.0.1
git push origin play-v1.0.1

# Manual dispatch
# GitHub UI → Actions → "Build Public AAB (Play Store)"
```

**Output:**
- `SentinelQuantumVanguardAIPro-PUBLIC-v1.0.1.aab`

**Release Tag:** `play-v*.*.*`

---

## 📦 Distribution Methods

### Institutional Build (APK)

#### Method 1: GitHub Releases
```
1. Go to: https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases
2. Find release tagged v1.0.1
3. Download: SentinelQuantumVanguardAIPro-INSTITUTIONAL-v1.0.1.apk
4. Verify SHA-256 checksum
```

#### Method 2: Direct Download
```
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.1/SentinelQuantumVanguardAIPro-INSTITUTIONAL-v1.0.1.apk
```

#### Method 3: QR Code
Generate QR code pointing to GitHub release URL for easy mobile access.

#### Method 4: Enterprise MDM
Upload APK to enterprise Mobile Device Management system for fleet deployment.

### Public Build (AAB)

#### Google Play Console Upload
```
1. Build AAB via GitHub Actions (play-v* tag)
2. Download AAB from GitHub Releases
3. Upload to Google Play Console:
   - App → Release → Production
   - Upload AAB
   - Complete store listing
   - Submit for review
```

---

## ✅ Security & Verification

### SHA-256 Checksum Verification

```bash
# Download checksum file
wget https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.1/institutional-apk-sha256.txt

# Verify APK integrity
sha256sum -c institutional-apk-sha256.txt
```

Expected output:
```
SentinelQuantumVanguardAIPro-INSTITUTIONAL-v1.0.1.apk: OK
```

### Code Signing Verification

```bash
# Verify APK signature
apksigner verify --verbose SentinelQuantumVanguardAIPro-INSTITUTIONAL-v1.0.1.apk

# Expected output:
# Verified using v1 scheme (JAR signing): true
# Verified using v2 scheme (APK Signature Scheme v2): true
# Verified using v3 scheme (APK Signature Scheme v3): true
```

---

## 🎯 Use Cases & Deployment Scenarios

### Institutional Build

| Scenario | Description |
|----------|-------------|
| **Defense/Military** | Full call monitoring and recording for security operations |
| **Enterprise Security** | Corporate device management with advanced threat detection |
| **Government Agencies** | Institutional compliance with local regulations |
| **Private MDM** | Fleet deployment via Mobile Device Management |
| **Security Firms** | Professional security monitoring services |

### Public Build

| Scenario | Description |
|----------|-------------|
| **Google Play Store** | Public distribution to general users |
| **Consumer Market** | Personal use with basic call screening |
| **Small Business** | Simple spam call protection |
| **BYOD Programs** | Bring Your Own Device enterprise scenarios |

---

## 📋 Version Management

### Version Numbering Strategy

```
Institutional: v1.0.1
Public:        play-v1.0.1
```

**Important:** Keep `versionCode` and `versionName` synchronized:

```gradle
defaultConfig {
    versionCode 2       // Increment for each release
    versionName "1.0.1" // User-visible version
}
```

### Release Checklist

#### Before Institutional Release
- [ ] Update `versionCode` and `versionName`
- [ ] Test on Samsung device (Android 14)
- [ ] Test on Pixel device (Android 13)
- [ ] Test on Huawei device (Android 12)
- [ ] Verify all advanced permissions work
- [ ] Generate production keystore backup
- [ ] Create release tag `v1.0.1`
- [ ] Trigger GitHub Actions workflow
- [ ] Verify SHA-256 checksum

#### Before Play Store Release
- [ ] Update `versionCode` and `versionName`
- [ ] Test on multiple devices
- [ ] Verify CallScreeningService works
- [ ] Ensure no restricted permissions
- [ ] Update Play Store listing
- [ ] Create privacy policy
- [ ] Create release tag `play-v1.0.1`
- [ ] Trigger GitHub Actions workflow
- [ ] Upload AAB to Play Console
- [ ] Submit for review

---

## 🔧 Troubleshooting

### Build Errors

**Error:** "Duplicate permission declaration"
```
Solution: Remove duplicate permissions from main AndroidManifest.xml
Flavor-specific permissions go in flavor manifests only.
```

**Error:** "Application ID conflict"
```
Solution: Uninstall existing app before installing different flavor
adb uninstall com.sentinel.quantum.institutional
adb uninstall com.sentinel.quantum.public
```

### Runtime Issues

**Issue:** Institutional build missing advanced features
```
Check: BuildConfig.IS_INSTITUTIONAL_BUILD == true
Verify: Flavor-specific code is using correct BuildConfig checks
```

**Issue:** Public build requesting restricted permissions
```
Check: Ensure public flavor manifest doesn't include restricted permissions
Verify: CallScreeningService is implemented as fallback
```

---

## 📊 Feature Comparison Matrix

| Feature | Institutional | Public |
|---------|---------------|--------|
| Call Screening | ✅ Full access | ✅ Limited (CallScreeningService) |
| Call Log Access | ✅ Direct | ❌ Not available |
| SMS Detection | ✅ Enabled | ❌ Disabled |
| Call Recording | ✅ Enabled | ❌ Disabled |
| Package Scanning | ✅ Enabled | ❌ Disabled |
| ProGuard Obfuscation | ✅ Enabled | ✅ Enabled |
| Code Signing | ✅ Production | ✅ Production |
| Google Play | ❌ Not compliant | ✅ Compliant |
| Enterprise MDM | ✅ Compatible | ⚠️ Limited |
| Application ID | `...institutional` | `...public` |
| Distribution | APK (direct) | AAB (Play Store) |

---

## 🌐 Distribution URLs

### Institutional Build
```
Release Page:
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases

Direct APK:
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.1/SentinelQuantumVanguardAIPro-INSTITUTIONAL-v1.0.1.apk

SHA-256:
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.1/institutional-apk-sha256.txt
```

### Public Build (AAB)
```
Release Page:
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases (play-v* tags)

Play Store:
https://play.google.com/store/apps/details?id=com.sentinel.quantum.public
(once published)
```

---

## 📚 Additional Documentation

- **Build Guide:** `ANDROID_PRODUCTION_BUILD_GUIDE.md`
- **Security Audit:** `PRODUCTION_SECURITY_AUDIT.md`
- **Quick Start:** `QUICKSTART_PRODUCTION_APK.md`
- **Implementation:** `IMPLEMENTATION_SUMMARY.md`

---

**Last Updated:** 2025-12-15  
**Status:** ✅ Dual Distribution Ready  
**Builds:** Institutional APK + Public AAB
