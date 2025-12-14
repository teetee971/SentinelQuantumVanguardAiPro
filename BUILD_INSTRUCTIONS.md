# 📱 Building Sentinel Quantum Vanguard Android APK

## 🎯 Quick Start

### Option 1: Build Locally (Recommended)

**Requirements:**
- Android Studio Hedgehog (2023.1.1+)
- JDK 17
- Android SDK 34

**Steps:**
1. Open Android Studio
2. Click "Open an Existing Project"
3. Navigate to `/android` folder
4. Wait for Gradle sync
5. Click "Build" → "Build Bundle(s) / APK(s)" → "Build APK(s)"
6. APK location: `android/app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Command Line

```bash
cd android
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Option 3: GitHub Actions (Automatic)

1. Push code to `main` or `develop` branch
2. Go to Actions tab
3. Wait for "Android APK Build" workflow to complete
4. Download `sentinel-debug-apk` artifact
5. Extract ZIP to get `app-debug.apk`

## 📦 Installation

### On Android Device

1. Enable "Unknown Sources" in Settings → Security
2. Transfer APK to device
3. Tap APK file to install
4. Accept permissions (Internet, Network State)

### Via ADB

```bash
adb install -r app-debug.apk
```

## 🔧 Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/sentinel/quantumvanguard/
│   │   │   ├── MainActivity.kt           # Main WebView activity
│   │   │   ├── SentinelApplication.kt    # App initialization
│   │   │   └── SentinelJSInterface.kt    # JavaScript bridge
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml  # Main layout
│   │   │   ├── menu/                     # Navigation menus
│   │   │   └── values/                   # Strings, colors, styles
│   │   ├── assets/www/                   # Embedded HTML site
│   │   └── AndroidManifest.xml
│   └── build.gradle                       # App dependencies
├── build.gradle                           # Project config
├── settings.gradle
├── gradle.properties
├── gradlew                                # Gradle wrapper
└── README.md
```

## 🎨 Features

### Native Android UI
- Material Design 3 theme
- Bottom navigation bar (5 sections)
- Toolbar with overflow menu
- Sentinel dark theme (#0a0a0a background)

### Bottom Navigation Menu
1. **Accueil** - Home page (index.html)
2. **Téléphone** - Phone security (public/telephone-security.html)
3. **SOC/EDR** - Mobile EDR & SOC (public/mobile-edr-soc.html)
4. **Status** - System status (public/system-status.html)
5. **Permissions** - Android permissions (public/android-apk-official.html)

### WebView Configuration
- JavaScript enabled (required for modals)
- DOM storage enabled
- Local file access (assets only)
- No cleartext traffic (HTTPS enforced)
- Secure cookies

### JavaScript Interface
- `SentinelAndroid.showToast(message)` - Show native toast
- `SentinelAndroid.getAppVersion()` - Get app version
- `SentinelAndroid.isNativeApp()` - Returns true

## 🔐 Security & Privacy

### Data Collection: ZERO
- ❌ No analytics
- ❌ No crash reporting
- ❌ No user tracking
- ❌ No third-party SDKs
- ✅ 100% offline-capable

### Permissions
- ✅ **INTERNET** - Required for CDN assets
- ✅ **ACCESS_NETWORK_STATE** - Check connectivity
- ⚠️ **READ_PHONE_STATE** - Opt-in (call analysis)
- ⚠️ **READ_CALL_LOG** - Opt-in (suspect calls)
- ⚠️ **READ_CONTACTS** - Opt-in (trusted whitelist)
- ⚠️ **READ_SMS** - Opt-in (smishing detection)
- ⚠️ **CALL_PHONE** - Opt-in (emergency SOS)
- ⚠️ **ACCESS_FINE_LOCATION** - Opt-in (panic mode)
- ⚠️ **RECORD_AUDIO** - Opt-in (call recording)
- ⚠️ **FOREGROUND_SERVICE** - Opt-in (real-time protection)

**All sensitive permissions** are declared but NOT requested automatically.
Users must explicitly enable features requiring them.

### ProGuard (Release)
- Code obfuscation enabled
- Unused code removal
- Optimized APK size

## 🚀 GitHub Actions Workflow

File: `.github/workflows/android-build.yml`

**Triggers:**
- Push to `main`, `develop`, or `copilot/**` branches
- Changes to `android/**`, `*.html`, `public/**`, or `assets/**`
- Manual trigger (`workflow_dispatch`)

**Steps:**
1. Checkout code
2. Set up JDK 17
3. Copy HTML/CSS/JS to Android assets
4. Make gradlew executable
5. Build debug APK
6. Upload APK as artifact (30-day retention)

**Download APK:**
1. Go to repository → Actions
2. Select successful workflow run
3. Download `sentinel-debug-apk` artifact
4. Unzip to get `app-debug.apk`

## 📊 Build Output

### Debug APK
- **Package**: com.sentinel.quantumvanguard.debug
- **Version**: 1.0.0-beta
- **Min SDK**: 26 (Android 8.0+)
- **Target SDK**: 34 (Android 14)
- **Size**: ~2-5 MB (depends on assets)
- **Debuggable**: Yes
- **Signing**: Debug keystore (auto-generated)

### Release APK (Future)
- Requires signing key configuration
- ProGuard enabled (code optimization)
- ~30-50% smaller than debug
- Not debuggable
- Play Store ready

## 🛠️ Troubleshooting

### Gradle sync failed
```bash
cd android
./gradlew clean
./gradlew build --refresh-dependencies
```

### gradlew permission denied
```bash
chmod +x gradlew
```

### APK not generated
```bash
./gradlew assembleDebug --stacktrace --info
```

### Assets not found in app
```bash
cd ..  # Go to project root
./android/copy-assets.sh
cd android
./gradlew assembleDebug
```

### App crashes on start
- Check Android version >= 8.0
- Check Logcat for errors:
```bash
adb logcat | grep Sentinel
```

## 📱 Testing

### On Emulator
1. Open AVD Manager in Android Studio
2. Create device (API 26+)
3. Run app from Android Studio

### On Physical Device
1. Enable Developer Options
2. Enable USB Debugging
3. Connect via USB
4. `adb devices` to verify
5. `adb install app-debug.apk`

## 🔄 Update Static Site

When HTML/CSS/JS changes:

```bash
# From project root
./android/copy-assets.sh

# Rebuild APK
cd android
./gradlew assembleDebug
```

## 📝 Version Management

Update version in `android/app/build.gradle`:

```gradle
defaultConfig {
    versionCode 2        // Increment for each release
    versionName "1.1.0"  // Semantic versioning
}
```

## 🌐 Play Store Deployment (Future)

Requirements:
- Release APK signed with production key
- App icon in all densities
- Screenshots (phone + tablet)
- Privacy policy URL
- Content rating questionnaire
- Target audience declaration

## ⚠️ Important Notes

1. **This is a WebView wrapper** - Main logic is in HTML/CSS/JS
2. **No server required** - All content embedded in APK
3. **Offline-first** - Works without internet (except CDN assets)
4. **RGPD compliant** - No data collection, opt-in permissions
5. **Defensive only** - No spyware, no surveillance capabilities
6. **Probabilistic approach** - No guaranteed threat detection
7. **Educational purpose** - Professional cybersecurity training tool

## 📄 Related Documentation

- `android/README.md` - Android project overview
- `MOBILE_SECURITY_IMPLEMENTATION.md` - Mobile security features
- `public/android-apk-official.html` - User-facing APK documentation

---

**Sentinel Quantum Vanguard AI Pro v1.0.0-beta**  
Native Android Application  
Defensive Cybersecurity Platform  
RGPD Compliant • No Spyware • Educational Purpose
