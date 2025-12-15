# Release Build Guide

## Creating a Signed Release APK

### Prerequisites
- Android Studio installed
- JDK 17 or higher
- Android SDK with API 34

### Step 1: Generate Release Keystore

Open terminal in the Android project directory and run:

```bash
cd android-app/android
keytool -genkey -v -keystore release.keystore \
  -alias sentinel-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You will be prompted to enter:
- Keystore password (save this securely!)
- Key password (save this securely!)
- Your name, organization, city, state, country

**⚠️ IMPORTANT:** Keep `release.keystore` and passwords secure. Without them, you cannot update your app!

### Step 2: Configure Signing in Gradle

Create or edit `android-app/android/keystore.properties`:

```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=sentinel-release
storeFile=release.keystore
```

**⚠️ Security:** Add `keystore.properties` to `.gitignore` to prevent committing passwords!

### Step 3: Update app/build.gradle

The signing configuration is already set up to use GitHub Actions injected properties or local `keystore.properties`.

For local builds, add to `app/build.gradle` (if not present):

```gradle
// Load keystore properties
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        release {
            if (keystorePropertiesFile.exists()) {
                storeFile file(keystoreProperties['storeFile'])
                storePassword keystoreProperties['storePassword']
                keyAlias keystoreProperties['keyAlias']
                keyPassword keystoreProperties['keyPassword']
            }
        }
    }
}
```

### Step 4: Build Release APK

```bash
cd android-app/android
./gradlew assembleRelease
```

The signed APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

Or for specific flavors:
```
app/build/outputs/apk/public/release/app-public-release.apk
app/build/outputs/apk/institutional/release/app-institutional-release.apk
```

### Step 5: Generate SHA-256 Checksum

```bash
# For Linux/Mac
sha256sum app/build/outputs/apk/release/app-release.apk > app-release.apk.sha256

# For Windows (PowerShell)
Get-FileHash app-release.apk -Algorithm SHA256 | Format-List
```

### Step 6: Verify APK Signature

```bash
# Using apksigner (from Android SDK build-tools)
apksigner verify --verbose app-release.apk

# Expected output should show:
# Verified using v1 scheme (JAR signing): true
# Verified using v2 scheme (APK Signature Scheme v2): true
```

---

## Publishing to GitHub Releases

### Step 1: Create Release Tag

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### Step 2: Create GitHub Release

1. Go to repository on GitHub
2. Click "Releases" → "Create a new release"
3. Select tag `v1.0.0`
4. Title: `Sentinel Quantum Vanguard AI Pro v1.0.0`
5. Description:

```markdown
## Sentinel Quantum Vanguard AI Pro v1.0.0

### 📱 Professional Cybersecurity WebView Application

**For CERT / SOC professional use**

### Download
- [app-release.apk](link-to-apk) - Main APK file

### SHA-256 Checksum
```
PASTE_SHA256_CHECKSUM_HERE
```

### What's Included
✅ Secure WebView with FLAG_SECURE
✅ HTTPS-only connections
✅ Domain-restricted navigation
✅ Dark theme optimized for professionals
✅ Offline error handling
✅ Immersive fullscreen mode

### Security Features
- Screenshot and screen recording blocked
- Local file access disabled
- Mixed content (HTTP) blocked
- No data collection or tracking

### System Requirements
- Android 6.0 (API 23) or higher
- Internet connection required
- ~5 MB storage space

### Installation
1. Download APK file
2. Enable "Install unknown apps" in Android settings
3. Verify SHA-256 checksum
4. Install APK
5. Launch application

### Verification
Verify APK integrity:
```bash
sha256sum app-release.apk
```

### Support
For issues: [GitHub Issues](link-to-issues)
```

6. Upload `app-release.apk` as a release asset
7. Publish release

---

## Automated Release Build (GitHub Actions)

### Option 1: Manual Workflow Trigger

1. Store keystore in GitHub Secrets (base64 encoded):
```bash
base64 release.keystore > release.keystore.b64
```

2. Add GitHub Secrets:
   - `KEYSTORE_FILE`: Content of `release.keystore.b64`
   - `KEYSTORE_PASSWORD`: Your keystore password
   - `KEY_ALIAS`: `sentinel-release`
   - `KEY_PASSWORD`: Your key password

3. Update `.github/workflows/build-android.yml` to include release build:

```yaml
- name: Decode Keystore
  env:
    ENCODED_KEYSTORE: ${{ secrets.KEYSTORE_FILE }}
  run: |
    echo $ENCODED_KEYSTORE | base64 -d > android-app/android/app/release.keystore

- name: Build Release APK
  env:
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: |
    cd android-app/android
    ./gradlew assembleRelease \
      -Pandroid.injected.signing.store.file=$PWD/app/release.keystore \
      -Pandroid.injected.signing.store.password=$KEYSTORE_PASSWORD \
      -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
      -Pandroid.injected.signing.key.password=$KEY_PASSWORD
```

### Option 2: Release Workflow

Create `.github/workflows/release.yml`:

```yaml
name: Build and Release APK

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: gradle

      - name: Decode Keystore
        run: echo "${{ secrets.KEYSTORE_FILE }}" | base64 -d > android-app/android/app/release.keystore

      - name: Build Release APK
        working-directory: ./android-app/android
        run: |
          chmod +x gradlew
          ./gradlew assembleRelease \
            -Pandroid.injected.signing.store.file=$PWD/app/release.keystore \
            -Pandroid.injected.signing.store.password=${{ secrets.KEYSTORE_PASSWORD }} \
            -Pandroid.injected.signing.key.alias=${{ secrets.KEY_ALIAS }} \
            -Pandroid.injected.signing.key.password=${{ secrets.KEY_PASSWORD }}

      - name: Generate Checksum
        working-directory: ./android-app/android/app/build/outputs/apk/release
        run: sha256sum *.apk > checksums.txt

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            android-app/android/app/build/outputs/apk/release/*.apk
            android-app/android/app/build/outputs/apk/release/checksums.txt
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

---

## APK Optimization Tips

### 1. Enable ProGuard/R8 (Already configured)
- Minifies code
- Obfuscates code
- Removes unused code

### 2. Resource Shrinking (Already configured)
- Removes unused resources
- Reduces APK size

### 3. Use APK Splits (Optional)
For different architectures:
```gradle
android {
    splits {
        abi {
            enable true
            reset()
            include 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
            universalApk true
        }
    }
}
```

### 4. Bundle vs APK
For Play Store distribution (future), use Android App Bundle:
```bash
./gradlew bundleRelease
```

---

## Security Best Practices

1. **Never commit keystore or passwords to git**
   - Add to `.gitignore`
   - Use GitHub Secrets for CI/CD

2. **Store keystore backups securely**
   - Keep encrypted backup
   - Store in secure location
   - Document recovery procedure

3. **Rotate signing keys periodically**
   - Plan key rotation strategy
   - Test with beta releases first

4. **Verify APK before distribution**
   - Test installation
   - Verify signature
   - Check SHA-256 checksum

---

## Distribution Checklist

Before distributing the APK:

- [ ] Built with release configuration
- [ ] Signed with release keystore
- [ ] ProGuard/R8 enabled
- [ ] Tested on multiple devices
- [ ] Tested on different Android versions
- [ ] Generated SHA-256 checksum
- [ ] Verified APK signature
- [ ] Created release notes
- [ ] Updated version number
- [ ] Tagged release in git
- [ ] Uploaded to GitHub Releases or download page
- [ ] Updated download page with new checksum
- [ ] Notified users of new version

---

## Version Numbering

Follow Semantic Versioning (semver):
- **Major.Minor.Patch** (e.g., 1.0.0)
- Major: Breaking changes
- Minor: New features (backward compatible)
- Patch: Bug fixes

Update in `app/build.gradle`:
```gradle
defaultConfig {
    versionCode 1  // Increment for each release
    versionName "1.0.0"  // Semantic version
}
```

---

## Support & Updates

For future updates:
1. Increment `versionCode` and `versionName`
2. Build and sign with same keystore
3. Test thoroughly
4. Publish as new GitHub Release
5. Users can update by installing new APK over old one

---

**Remember:** Keep your keystore and passwords secure. Losing them means you cannot update your app!
