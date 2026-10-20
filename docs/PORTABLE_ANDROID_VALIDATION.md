# Independent native Android host qualification

GitHub Actions billing/runner provisioning can block **execution**, not necessarily the source. This script permits a **manual build and evidence capture** on a separate, user-authorized Linux x86-64 host with Java 17, Node/npm, Android SDK and build-tools installed. It does not purchase, provision, or start any remote infrastructure.

Run from repository root:

```bash
npm ci
cd native-android-app && ./gradlew clean && cd ..
export ANDROID_HOME=/path/to/android-sdk
bash scripts/portable-android-validation.sh
```

Requirements: Linux x86-64, Java 17, Node/npm, Android SDK platform matching `compileSdk`, matching official build-tools; network for locked Gradle/Maven dependencies. Do **not** point this at Termux ARM64 with an obsolete Debian AAPT2 override.

The script replays the core source checks, unit tests, lint, release dependency inventory, debug APK packaging, alignment, signature and packaged-secret check from `.github/workflows/build-native-android.yml`. It refuses pre-existing APK outputs or dependency inventory files; clean the Android project before a fresh run. It requires exactly one debug APK and checks inventory structure before declaring host-build success. It fails on any missing step or artifact; saves logs, input commit and APK digest under `.sentinel-local-evidence/`. The directory is local and should not be committed or publicly uploaded without review.

**Limits:** The success marker `HOST_BUILD_VERIFIED_ONLY` does not assert emulator qualification, Android 16 compatibility, Phone Core behavior, physical watch discovery, or release readiness. GitHub required checks remain blocked until runners execute. This is not an alternative green CI check and must not be used to bypass branch protection. APK is debug only.
