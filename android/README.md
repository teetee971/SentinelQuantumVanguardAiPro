# Android APK Generation - PWA WebView Wrapper
**Sentinel Quantum Vanguard AI Pro**

## Overview

This document explains how to generate a professional Android APK from the PWA using a WebView wrapper approach. This is the **simplest and most maintainable** solution for packaging a PWA as an Android application.

---

## Architecture Decision

### Why WebView Wrapper?

✅ **Advantages**:
- No Java/Kotlin business logic needed
- PWA is the single source of truth
- Updates via web deployment (no APK republishing)
- Minimal native code maintenance
- Perfect for institutional/corporate distribution

❌ **Trusted Web Activity (TWA)** considerations:
- Requires HTTPS domain (we have this ✅)
- Requires Digital Asset Links verification
- More complex setup for first-time use
- Better for public Play Store distribution

**For this project**: WebView wrapper is recommended for corporate/institutional deployment. TWA can be implemented later if Play Store distribution is needed.

---

## Method 1: PWA Builder (Recommended - No Code)

### What is PWA Builder?
Official Microsoft tool to generate Android APKs from PWAs.
URL: https://www.pwabuilder.com/

### Steps:

1. **Enter PWA URL**:
   ```
   https://your-sentinel-domain.pages.dev
   ```

2. **Validate PWA**:
   - PWA Builder will check manifest.json
   - Verify service worker
   - Check icons

3. **Generate APK**:
   - Click "Package for Android"
   - Choose "Trusted Web Activity" (recommended) or "WebView"
   - Download generated APK

4. **Sign APK** (for production):
   ```bash
   # Create keystore (once)
   keytool -genkey -v -keystore sentinel-release.keystore \
     -alias sentinel -keyalg RSA -keysize 2048 -validity 10000
   
   # Sign APK
   jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
     -keystore sentinel-release.keystore app-release-unsigned.apk sentinel
   
   # Align APK
   zipalign -v 4 app-release-unsigned.apk sentinel-release.apk
   ```

### Pros:
- ✅ Zero coding required
- ✅ Official Microsoft tool
- ✅ Automatic updates from PWA
- ✅ Fast iteration

### Cons:
- ⚠️ Requires online tool
- ⚠️ Less customization control

---

## Method 2: Manual WebView Wrapper (Full Control)

### Prerequisites:
- Android Studio (latest version)
- Java JDK 11+
- Android SDK 31+

### Project Structure:
```
android/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/sentinel/app/
│   │       │       └── MainActivity.java
│   │       ├── res/
│   │       │   ├── values/
│   │       │   │   ├── strings.xml
│   │       │   │   └── colors.xml
│   │       │   ├── mipmap-*/
│   │       │   │   └── ic_launcher.png
│   │       │   └── layout/
│   │       │       └── activity_main.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── gradle/
└── build.gradle
```

### Key Files:

#### 1. `AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.sentinel.quantumvanguard">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="false">
        
        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|keyboard"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

#### 2. `MainActivity.java`
```java
package com.sentinel.quantumvanguard;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    
    private static final String PWA_URL = "https://your-domain.pages.dev";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        webView = new WebView(this);
        setContentView(webView);
        
        // WebView settings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        
        // Enable PWA features
        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(getApplicationContext().getCacheDir().getPath());
        
        // Load PWA
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(PWA_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
```

#### 3. `build.gradle` (app level)
```gradle
plugins {
    id 'com.android.application'
}

android {
    compileSdk 33
    
    defaultConfig {
        applicationId "com.sentinel.quantumvanguard"
        minSdk 21
        targetSdk 33
        versionCode 1
        versionName "1.0.0"
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
```

### Build Commands:
```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## Method 3: Capacitor (Hybrid Approach)

### What is Capacitor?
Official Ionic framework for wrapping web apps in native containers.

### Setup:
```bash
npm install @capacitor/core @capacitor/cli
npx cap init "Sentinel AI" "com.sentinel.quantumvanguard"
npm install @capacitor/android
npx cap add android
```

### Configuration (`capacitor.config.json`):
```json
{
  "appId": "com.sentinel.quantumvanguard",
  "appName": "Sentinel Quantum Vanguard",
  "webDir": "public",
  "server": {
    "url": "https://your-domain.pages.dev",
    "cleartext": false
  },
  "android": {
    "allowMixedContent": false,
    "backgroundColor": "#1a2230"
  }
}
```

### Build:
```bash
npx cap sync
npx cap open android
# Then build in Android Studio
```

### Pros:
- ✅ Well-documented
- ✅ Plugin ecosystem
- ✅ TypeScript support

### Cons:
- ⚠️ Additional dependency
- ⚠️ More complex than plain WebView

---

## Icon Generation

### Required Sizes (Android):
- mipmap-mdpi: 48x48
- mipmap-hdpi: 72x72
- mipmap-xhdpi: 96x96
- mipmap-xxhdpi: 144x144
- mipmap-xxxhdpi: 192x192

### Tools:
1. **Android Asset Studio**: https://romannurik.github.io/AndroidAssetStudio/
2. **ImageMagick** (CLI):
   ```bash
   convert icon-512x512.png -resize 192x192 mipmap-xxxhdpi/ic_launcher.png
   convert icon-512x512.png -resize 144x144 mipmap-xxhdpi/ic_launcher.png
   convert icon-512x512.png -resize 96x96 mipmap-xhdpi/ic_launcher.png
   convert icon-512x512.png -resize 72x72 mipmap-hdpi/ic_launcher.png
   convert icon-512x512.png -resize 48x48 mipmap-mdpi/ic_launcher.png
   ```

---

## Splash Screen

### Simple Approach (Android 12+):
Use `windowSplashScreenBackground` in `themes.xml`:

```xml
<style name="AppTheme" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#1a2230</item>
    <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
    <item name="postSplashScreenTheme">@style/AppTheme.Base</item>
</style>
```

---

## Signing & Distribution

### Production Signing:
```bash
# 1. Generate keystore (KEEP SECURE!)
keytool -genkey -v -keystore sentinel.keystore \
  -alias sentinel \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_STRONG_PASSWORD \
  -keypass YOUR_STRONG_PASSWORD \
  -dname "CN=Sentinel, OU=Security, O=Sentinel, L=Paris, ST=IDF, C=FR"

# 2. Sign APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore sentinel.keystore app-release-unsigned.apk sentinel

# 3. Align (optimize)
zipalign -v 4 app-release-unsigned.apk sentinel-v1.0.0.apk

# 4. Verify
apksigner verify sentinel-v1.0.0.apk
```

### Distribution Options:
1. **Corporate MDM** (recommended for institutional):
   - Upload signed APK to MDM platform
   - Push to managed devices
   - No Play Store needed

2. **Direct Download** (sideloading):
   - Host APK on secure HTTPS server
   - Provide download link
   - Users enable "Unknown sources"

3. **Google Play Store** (public):
   - Requires developer account ($25 one-time)
   - Use TWA for best integration
   - Automatic updates

---

## Testing

### Debug Install:
```bash
# Install on connected device/emulator
adb install app-debug.apk

# Launch app
adb shell am start -n com.sentinel.quantumvanguard/.MainActivity

# View logs
adb logcat | grep Sentinel
```

### Release Testing:
```bash
# Install release APK
adb install sentinel-v1.0.0.apk

# Check signature
adb shell dumpsys package com.sentinel.quantumvanguard | grep signatures
```

---

## Security Considerations

### WebView Hardening:
✅ JavaScript enabled (required for PWA)
✅ DOM storage enabled (required for PWA)
✅ File access disabled
✅ Content access disabled
✅ Mixed content blocked
✅ HTTPS only (cleartext traffic disabled)

### ProGuard/R8:
Enable code obfuscation in `build.gradle`:
```gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
    }
}
```

---

## Maintenance

### PWA Updates:
✅ **Automatic** - WebView loads latest PWA from server
- No APK republishing needed for content/UI changes
- Service worker handles caching
- Users always get latest version

### APK Updates:
Only rebuild APK when:
- Changing native code
- Updating permissions
- Changing app icon
- Updating Android SDK target

---

## Recommended Approach for Sentinel

### For Institutional/Corporate Distribution:

**Option 1: PWA Builder** (fastest)
1. Use PWA Builder to generate APK
2. Sign with corporate certificate
3. Distribute via MDM or secure download

**Option 2: Manual WebView** (most control)
1. Use provided MainActivity.java template
2. Customize branding
3. Build in Android Studio
4. Sign and distribute

### For Play Store Distribution (future):
Use **Trusted Web Activity** (TWA) via PWA Builder or Bubblewrap.

---

## Files Provided

This directory contains:
- `AndroidManifest.xml` - Manifest template
- `MainActivity.java` - WebView activity
- `build.gradle` - Build configuration
- `README.md` - This file

---

## Support

For issues:
1. Check PWA works in mobile browser first
2. Test WebView in Android Studio emulator
3. Review logcat output for errors
4. Verify HTTPS certificate is valid

---

**Last updated**: 2025-12-16  
**Maintainer**: Sentinel Development Team
