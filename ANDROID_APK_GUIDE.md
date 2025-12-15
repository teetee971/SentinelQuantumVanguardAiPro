# Sentinel Quantum Vanguard AI Pro - Android APK Guide

## 📱 Application Overview

A secure, production-ready Android WebView application for professional cybersecurity use (CERT/SOC).

### Key Features

✅ **Secure WebView**
- Loads `https://sentinelquantumvanguardaipro.pages.dev`
- JavaScript and DOM Storage enabled
- Local file access blocked
- Mixed content (HTTP) blocked
- External navigation restricted to domain only

✅ **Security**
- `FLAG_SECURE` prevents screenshots and screen recording
- HTTPS-only network configuration
- No sensitive data storage
- Domain-restricted navigation

✅ **UI/UX**
- Professional dark splash screen (cybersecurity theme)
- Immersive fullscreen mode
- Dark theme only
- Loading progress indicator
- Offline error page with retry button
- Back button WebView navigation

✅ **CI/CD**
- Automated APK build on push to main
- Gradle cache optimization
- Detailed build logs
- APK artifact upload

---

## 🏗️ Project Structure

```
android-app/android/
├── app/
│   ├── build.gradle                          # App-level build configuration
│   ├── src/main/
│   │   ├── kotlin/com/sentinel/
│   │   │   ├── MainActivity.kt              # Main WebView activity
│   │   │   ├── SplashActivity.kt            # Professional splash screen
│   │   │   └── MainApplication.kt           # Application class
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml        # Main layout with WebView
│   │   │   │   └── activity_splash.xml      # Splash screen layout
│   │   │   ├── drawable/
│   │   │   │   ├── splash_logo.xml          # Professional cybersec logo
│   │   │   │   ├── splash_background.xml    # Dark splash background
│   │   │   │   ├── ic_offline.xml           # Offline icon
│   │   │   │   └── progress_bar.xml         # Loading progress
│   │   │   ├── values/
│   │   │   │   ├── colors.xml               # Dark theme colors
│   │   │   │   ├── strings.xml              # App strings
│   │   │   │   └── styles.xml               # Dark theme styles
│   │   │   └── xml/
│   │   │       └── network_security_config.xml
│   │   └── AndroidManifest.xml              # App manifest
│   └── proguard-rules.pro
├── build.gradle                              # Project-level build config
├── settings.gradle                           # Project settings
└── gradlew                                   # Gradle wrapper script
```

---

## 🚀 Building the APK

### Option 1: GitHub Actions (Recommended - Automatic)

The CI/CD workflow automatically builds the APK when you push to `main` branch.

**Workflow file:** `.github/workflows/build-android.yml`

**Features:**
- ✅ Gradle cache for faster builds
- ✅ Detailed logs with `--stacktrace --info`
- ✅ APK uploaded as artifact
- ✅ Runs on Ubuntu with Java 17

**To download the APK:**
1. Go to GitHub Actions tab
2. Click on the latest "Build Android APK" workflow run
3. Download `Sentinel-APK` from Artifacts section

### Option 2: Local Build (Android Studio)

1. **Prerequisites:**
   - Android Studio (latest version)
   - JDK 17
   - Android SDK with API 34

2. **Steps:**
   ```bash
   cd android-app/android
   ./gradlew clean
   ./gradlew assembleDebug
   ```

3. **Output location:**
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option 3: Command Line Build

```bash
# Navigate to Android project
cd android-app/android

# Make gradlew executable (Linux/Mac)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Build with detailed logs
./gradlew assembleDebug --stacktrace --info

# For Windows
gradlew.bat assembleDebug
```

---

## 📦 Build Flavors

The app supports two product flavors for different distribution channels:

### 1. Public Flavor
- **Application ID:** `com.sentinel.quantum.public`
- **Suitable for:** Public distribution
- **Build command:** `./gradlew assemblePublicDebug`

### 2. Institutional Flavor
- **Application ID:** `com.sentinel.quantum.institutional`
- **Suitable for:** CERT/SOC institutional use
- **Build command:** `./gradlew assembleInstitutionalDebug`

---

## 🔐 Release Build (Signed APK)

For production release, you need to sign the APK with a keystore.

### Step 1: Generate Keystore

```bash
keytool -genkey -v -keystore release.keystore \
  -alias sentinel-release \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

### Step 2: Configure Signing

Add to `app/build.gradle`:

```gradle
android {
    signingConfigs {
        release {
            storeFile file('release.keystore')
            storePassword 'your-keystore-password'
            keyAlias 'sentinel-release'
            keyPassword 'your-key-password'
        }
    }
    
    buildTypes {
        release {
            signingConfig signingConfigs.release
            // ... existing config
        }
    }
}
```

### Step 3: Build Release APK

```bash
./gradlew assembleRelease
```

**Output:** `app/build/outputs/apk/release/app-release.apk`

---

## 🔒 Security Features Implemented

### 1. WebView Security
```kotlin
// FLAG_SECURE - Prevents screenshots
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)

// Block local file access
allowFileAccess = false
allowContentAccess = false
allowFileAccessFromFileURLs = false
allowUniversalAccessFromFileURLs = false

// Block mixed content
mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

// Domain restriction
shouldOverrideUrlLoading {
    // Only allow navigation within target domain
}
```

### 2. Network Security
- HTTPS-only configuration
- No cleartext traffic allowed
- Network security config XML

### 3. UI Security
- Immersive fullscreen mode
- System bars hidden
- No screenshots/recording allowed

---

## 🎨 Design Specifications

### Color Palette (Professional Cybersecurity)
- **Background Dark:** `#0A0E1A`
- **Primary Dark:** `#1A1F2E`
- **Primary Darker:** `#0D1117`
- **Accent Cyan:** `#00D9FF` (tech/security theme)
- **Accent Blue:** `#0066FF`
- **Text Primary:** `#FFFFFF`
- **Text Secondary:** `#B0B8C4`

### Splash Screen
- Duration: 2 seconds
- Professional geometric logo (shield + hexagon)
- Minimalist design (no emojis)
- Futuristic cybersecurity aesthetic
- App name: "Sentinel Quantum Vanguard AI Pro"
- Subtitle: "CERT / SOC CYBERSECURITY"

---

## 📱 Installation

### For Developers/Testers

1. Enable "Unknown Sources" or "Install unknown apps" on your Android device
2. Transfer the APK to your device
3. Open the APK file and install

### SHA-256 Checksum Verification

Before distributing, generate checksum:

```bash
sha256sum app-debug.apk > app-debug.apk.sha256
```

Users should verify before installation:

```bash
sha256sum -c app-debug.apk.sha256
```

---

## 🌐 Distribution (Outside Play Store)

### Option 1: Direct Download Page

Create a professional download page with:
- Download button
- SHA-256 checksum
- QR code for easy mobile download
- Installation instructions
- Security warnings

See `docs/download-page.html` for template.

### Option 2: GitHub Releases

1. Create a new release on GitHub
2. Upload the APK as a release asset
3. Include SHA-256 checksum in release notes
4. Tag the release (e.g., `v1.0.0`)

---

## 🧪 Testing Checklist

- [ ] APK builds successfully
- [ ] App launches without crashes
- [ ] Splash screen displays for 2 seconds
- [ ] WebView loads the target URL
- [ ] JavaScript works correctly
- [ ] Back button navigates within WebView
- [ ] Offline error page shows when no network
- [ ] Retry button works
- [ ] Screenshots are blocked (FLAG_SECURE)
- [ ] External links are blocked
- [ ] Fullscreen mode is immersive
- [ ] Dark theme is applied throughout

---

## 🐛 Troubleshooting

### Build fails with "Could not resolve dependencies"
- **Cause:** Network/proxy issues
- **Solution:** Check internet connection, configure proxy if needed

### APK install fails on device
- **Cause:** Unknown sources not enabled
- **Solution:** Enable "Install unknown apps" for your file manager

### WebView shows blank page
- **Cause:** Network connection issue
- **Solution:** Check device internet connection, verify URL is accessible

### Screenshots still work despite FLAG_SECURE
- **Cause:** Some Android versions/custom ROMs may bypass this
- **Solution:** This is expected behavior, FLAG_SECURE is best-effort

---

## 📝 Version History

### v1.0.0 (Current)
- ✅ Secure WebView implementation
- ✅ Professional splash screen
- ✅ Dark theme
- ✅ FLAG_SECURE protection
- ✅ Offline handling
- ✅ CI/CD workflow

### Future Enhancements (Optional)
- 🔔 Push notifications (CERT alerts)
- 🔄 Auto-update mechanism
- 📊 Analytics (privacy-focused)
- 🔐 Certificate pinning
- 🌍 Multi-language support

---

## 📞 Support

For issues or questions:
- GitHub Issues: https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues
- Professional use: Contact CERT/SOC team

---

## 📄 License

See LICENSE file in repository root.

---

## 🔗 Related Links

- Web Application: https://sentinelquantumvanguardaipro.pages.dev
- Repository: https://github.com/teetee971/SentinelQuantumVanguardAiPro
- CI/CD Workflow: `.github/workflows/build-android.yml`

---

**Built with ❤️ for cybersecurity professionals**
