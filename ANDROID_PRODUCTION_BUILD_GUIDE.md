# 📱 Android Production APK - Build Guide

## 🎯 Overview

This guide explains how to build and release a **production-ready, signed Android APK** for Sentinel Quantum Vanguard AI Pro.

**Key Features:**
- ✅ Signed with production keystore (not debug)
- ✅ ProGuard/R8 code obfuscation enabled
- ✅ Resource shrinking active
- ✅ Compatible Android 10 → 14 (API 29-34)
- ✅ No debug artifacts
- ✅ Installable on Samsung / Pixel / Huawei

---

## 🔐 Step 1: Create GitHub Secrets

### Required Secrets

Go to **GitHub → Settings → Secrets and variables → Actions** and create:

| Secret Name | Description |
|-------------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Your production keystore encoded in base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Password for the keystore |
| `ANDROID_KEY_ALIAS` | Alias name of the key |
| `ANDROID_KEY_PASSWORD` | Password for the key alias |

### Generate Production Keystore

If you don't have a keystore yet:

```bash
# Generate a production keystore
keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore sentinel-release.keystore \
  -alias sentinel-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Sentinel Quantum Vanguard AI Pro,O=YourOrganization,C=US"

# You'll be prompted for:
# - Keystore password (save this as ANDROID_KEYSTORE_PASSWORD)
# - Key password (save this as ANDROID_KEY_PASSWORD)
```

### Encode Keystore to Base64

```bash
# On Linux/macOS
base64 -w 0 sentinel-release.keystore > keystore.base64.txt

# On Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sentinel-release.keystore")) > keystore.base64.txt
```

Copy the content of `keystore.base64.txt` and paste it into the `ANDROID_KEYSTORE_BASE64` secret.

⚠️ **IMPORTANT:** 
- Never commit the `.keystore` file to Git
- Store the keystore file securely offline (backup to secure location)
- If you lose the keystore, you cannot update your app on Google Play

---

## 🚀 Step 2: Trigger Production Build

### Option A: Tag-Based Release (Recommended)

```bash
# Create and push a version tag
git tag v1.0.1
git push origin v1.0.1

# GitHub Actions will automatically:
# 1. Build the signed release APK
# 2. Create a GitHub Release
# 3. Upload the APK
```

### Option B: Manual Dispatch

1. Go to **Actions** tab in GitHub
2. Select **"Build & Release Android APK (PRODUCTION)"**
3. Click **"Run workflow"**
4. Enter version number (e.g., `1.0.1`)
5. Click **"Run workflow"** button

---

## 📦 Step 3: Download & Install APK

### Download from GitHub Releases

1. Go to: `https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases`
2. Find your release (e.g., `v1.0.1`)
3. Download: `SentinelQuantumVanguardAIPro-v1.0.1-PRODUCTION.apk`

### Install on Android Device

#### Method 1: Direct Transfer
```bash
# Transfer APK to device
adb push SentinelQuantumVanguardAIPro-v1.0.1-PRODUCTION.apk /sdcard/Download/

# Install from device
# 1. Open Files app
# 2. Navigate to Downloads
# 3. Tap the APK file
# 4. Allow "Install from unknown sources" if prompted
# 5. Install
```

#### Method 2: ADB Install
```bash
adb install SentinelQuantumVanguardAIPro-v1.0.1-PRODUCTION.apk
```

#### Method 3: Download Directly on Device

From your device's browser, navigate to:
```
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.1/SentinelQuantumVanguardAIPro-v1.0.1-PRODUCTION.apk
```

---

## ✅ Verification Checklist

After installation, verify:

- [ ] **APK installs without errors** (no "parsing error")
- [ ] **App launches successfully**
- [ ] **No crashes on startup**
- [ ] **Permissions are requested at runtime**
- [ ] **Phone module is functional**
- [ ] **Call detection works** (test with an incoming call)
- [ ] **Data persists after app restart**
- [ ] **No debug logging visible**

### Check APK Signature

```bash
# Verify APK is properly signed
apksigner verify --verbose SentinelQuantumVanguardAIPro-v1.0.1-PRODUCTION.apk

# Expected output:
# Verified using v1 scheme (JAR signing): true
# Verified using v2 scheme (APK Signature Scheme v2): true
# Verified using v3 scheme (APK Signature Scheme v3): true
```

### Check APK Info

```bash
# View APK information
aapt dump badging SentinelQuantumVanguardAIPro-v1.0.1-PRODUCTION.apk | grep -E "package|sdkVersion|targetSdkVersion"

# Expected output:
# package: name='com.sentinel' versionCode='1' versionName='1.0'
# sdkVersion:'23'
# targetSdkVersion:'34'
```

---

## 🏗️ Local Production Build (Advanced)

If you want to build locally instead of using GitHub Actions:

### Prerequisites

- JDK 17 installed
- Android SDK installed
- Node.js 18+ installed

### Build Steps

```bash
# 1. Navigate to android-app directory
cd android-app

# 2. Install Node.js dependencies
npm ci

# 3. Navigate to Android directory
cd android

# 4. Make gradlew executable
chmod +x gradlew

# 5. Build release APK with your keystore
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/your/sentinel-release.keystore \
  -Pandroid.injected.signing.store.password=YourKeystorePassword \
  -Pandroid.injected.signing.key.alias=sentinel-release \
  -Pandroid.injected.signing.key.password=YourKeyPassword

# 6. Find your APK
ls -lh app/build/outputs/apk/release/app-release.apk
```

---

## 🔧 Troubleshooting

### Issue: "Parsing Error" on Installation

**Cause:** APK is corrupted or incompatible

**Solutions:**
- Verify APK downloaded completely (check file size ~25-30 MB)
- Ensure Android version is 6.0+ (API 23+)
- Re-download the APK
- Check device architecture compatibility

### Issue: "App not installed" Error

**Cause:** Conflicting signature with previous version

**Solution:**
```bash
# Uninstall existing version first
adb uninstall com.sentinel

# Then install new version
adb install SentinelQuantumVanguardAIPro-v1.0.1-PRODUCTION.apk
```

### Issue: Workflow Fails with "Keystore not found"

**Cause:** Missing or incorrect GitHub secrets

**Solution:**
1. Verify all 4 secrets are created correctly
2. Check `ANDROID_KEYSTORE_BASE64` is valid base64
3. Ensure no extra spaces or newlines in secret values
4. Re-run the workflow

### Issue: Build Succeeds but APK is Too Small (<10 MB)

**Cause:** React Native bundle not included

**Solution:**
1. Check Node.js dependencies installed: `cd android-app && npm ci`
2. Verify workflow step "Install Node dependencies" succeeded
3. Check build logs for bundling errors
4. Re-run workflow

---

## 📊 Build Configuration Details

### ProGuard/R8 Obfuscation

**Status:** ✅ ENABLED for release builds

The production build uses advanced obfuscation:
- Code minification (smaller APK)
- Class/method name obfuscation (anti-reverse engineering)
- Resource shrinking (removes unused resources)
- Log removal (all debug logging stripped)

**Configuration:** `android-app/android/app/proguard-rules.pro`

### Signing Configuration

**Debug builds:**
- Use `debug.keystore` (auto-generated)
- Suitable for development only
- ❌ NOT for production distribution

**Release builds:**
- Use production keystore from GitHub secrets
- Injected via Gradle properties
- ✅ Required for production distribution

**Configuration:** `android-app/android/app/build.gradle`

### Security Features

- **Network Security:** HTTPS-only, cleartext blocked
- **Backup:** Disabled (`android:allowBackup="false"`)
- **Data Storage:** 100% local SQLite
- **No Tracking:** Zero third-party SDKs
- **Permissions:** Runtime-requested, documented

**Configuration:** 
- `android-app/android/app/src/main/AndroidManifest.xml`
- `android-app/android/app/src/main/res/xml/network_security_config.xml`

---

## 🎯 Production Deployment Flow

```
┌─────────────────┐
│  Git Tag Push   │
│   (v1.0.1)      │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│   GitHub Actions        │
│   Workflow Triggered    │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  1. Checkout Code       │
│  2. Setup Java 17       │
│  3. Setup Node.js 18    │
│  4. Install Dependencies│
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  5. Decode Keystore     │
│     (from base64)       │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  6. Build Signed APK    │
│     - ProGuard enabled  │
│     - Resources shrunk  │
│     - Code obfuscated   │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  7. Verify APK          │
│     - Check size        │
│     - Validate signing  │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  8. Create Release      │
│     - Upload APK        │
│     - Generate notes    │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  ✅ DONE!               │
│  APK Ready for Download │
└─────────────────────────┘
```

---

## 📚 Additional Resources

- **Full Security Audit:** See `PRODUCTION_SECURITY_AUDIT.md`
- **Android Development:** See `ANDROID_README.md`
- **Testing Guide:** See `APK_TEST_GUIDE.md`
- **Workflow File:** `.github/workflows/android-release.yml`

---

## ⚠️ Important Notes

### Keystore Security

🔐 **CRITICAL:** Your production keystore is the **ONLY** way to sign updates for your app.

- **Never** commit it to Git
- **Always** keep secure backups (2+ locations)
- **Never** share passwords
- **If lost:** You cannot update your app (must publish new app with new package name)

### READ_CALL_LOG Permission (Android 13+)

⚠️ **Warning:** Starting Android 13, `READ_CALL_LOG` is restricted to default dialer apps.

**Impact:** Permission may be silently denied even if user grants it.

**Recommended Solution:** Migrate to `CallScreeningService` API (officially supported by Google).

See `PRODUCTION_SECURITY_AUDIT.md` for detailed migration guide.

### Version Management

When creating new releases:

1. Update `versionCode` and `versionName` in `android-app/android/app/build.gradle`
2. Create git tag with same version: `git tag v1.0.2`
3. Push tag: `git push origin v1.0.2`
4. GitHub Actions builds and publishes automatically

---

## 🆘 Support

**Issues:** https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues  
**Documentation:** https://sentinelquantumvanguardaipro.pages.dev  
**Security:** See `PRODUCTION_SECURITY_AUDIT.md`

---

**Last Updated:** 2025-12-15  
**Document Version:** 1.0  
**Status:** Production Ready ✅
