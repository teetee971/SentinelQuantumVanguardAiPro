#!/usr/bin/env bash
# Execute the Security Lab UI test ONLY on a confirmed Android emulator.
# No physical Android device is ever an allowed instrumentation target.
set -euo pipefail
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/root/android-sdk}}"
ADB="$SDK/platform-tools/adb"
[[ -x "$ADB" ]] || { echo "BLOCKED_NO_ADB: no Android SDK adb"; exit 2; }
mapfile -t devices < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if (( ${#devices[@]} != 1 )); then
  echo "BLOCKED_DEVICE_SELECTION: exactly one connected device required; found ${#devices[@]}"
  exit 2
fi
SERIAL="${devices[0]}"
if [[ "$SERIAL" != emulator-* ]]; then
  echo "BLOCKED_PHYSICAL_DEVICE: refusing non-emulator serial"
  exit 3
fi
QEMU="$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r\n')"
if [[ "$QEMU" != "1" ]]; then
  echo "BLOCKED_NOT_EMULATOR: ro.kernel.qemu != 1"
  exit 3
fi
echo "EMULATOR_VERIFIED=$SERIAL"
ROOT="${1:-$HOME/sentinel-security-lab-androidtest/native-android-app}"
[[ -x "$ROOT/gradlew" ]] || { echo "BLOCKED_PROJECT_MISSING"; exit 2; }
cd "$ROOT"
# Gradle connected test can target the verified emulator only because just one device is online.
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.sentinel.quantum.security.SecurityLabProInstrumentationTest \
  --no-daemon --console=plain
echo "VERDICT=SECURITY_LAB_UI_INSTRUMENTATION_PASSED_ON_EMULATOR"
