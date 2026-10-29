#!/usr/bin/env bash
# Local build only. Never dispatches GitHub Actions or uploads an artifact.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK installation}"
OUT=${PHONE_CORE_BUILD_LOG_DIR:-"$ROOT/native-android-app/build/phone-core-v1-evidence"}
mkdir -p "$OUT"
cd "$ROOT/native-android-app"
"$JAVA_HOME/bin/java" -version 2>&1 | tee "$OUT/java.log"
# Android SDK package name for compileSdk 37 is platforms;android-37.0.
# On ARM64 use the repository's documented native aapt2 override if SDK aapt2 is x86_64.
bash ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug \
    :app:bundleReleaseUnsigned :app:assembleDebugAndroidTest \
    --no-daemon --console=plain --max-workers=2 "$@" 2>&1 | tee "$OUT/gradle.log"
find app/build/outputs -type f \( -name '*.apk' -o -name '*.aab' \) -exec sha256sum {} + | tee "$OUT/artifacts.sha256"
