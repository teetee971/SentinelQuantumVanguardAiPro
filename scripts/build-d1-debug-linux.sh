#!/usr/bin/env bash
# Local or hosted Linux build for D.1 debug qualification; no release signing, no deployment.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID="$ROOT/native-android-app"
for tool in java node sha256sum; do
  command -v "$tool" >/dev/null || { echo "BLOCKED: missing $tool" >&2; exit 2; }
done
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" || ! -d "$SDK/platforms/android-37" ]]; then
  echo "BLOCKED: Android SDK platform android-37 not available (ANDROID_HOME / ANDROID_SDK_ROOT)" >&2
  exit 2
fi
[[ -f "$ANDROID/gradle/wrapper/gradle-wrapper.jar" ]] || {
  echo "BLOCKED: Gradle wrapper JAR missing" >&2; exit 2;
}
cd "$ROOT"
node scripts/check-android-ui-consistency.js
node scripts/check-d1-activation-source.mjs
cd "$ANDROID"
bash ./gradlew --no-daemon --stacktrace :app:assembleDebug
APK="$ANDROID/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo "BLOCKED: APK missing or empty" >&2; exit 3; }
echo "BUILD_OK: $APK"
echo "COMMIT: $(git -C "$ROOT" rev-parse HEAD)"
echo "BYTES: $(wc -c < "$APK" | tr -d ' ')"
echo "SHA256: $(sha256sum "$APK")"
echo "STATUS: DEBUG_ONLY_NOT_PHYSICALLY_VALIDATED"
