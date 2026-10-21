#!/usr/bin/env bash
# Run on a Linux host with an existing, isolated Android emulator. Never on a physical phone.
set -euo pipefail
APP_APK="${1:?Usage: script app-debug.apk app-debug-androidTest.apk results-dir}"
TEST_APK="${2:?Test APK required}"
OUT="${3:?Results directory required}"
[[ -s "$APP_APK" && -s "$TEST_APK" ]] || { echo "APK manquant"; exit 2; }
command -v adb >/dev/null || { echo "adb absent"; exit 2; }
command -v sha256sum >/dev/null || { echo "sha256sum absent"; exit 2; }
EXPECTED_APP="5d9aa8658aaf338583b55fae80e9af18270d9151ac3dcd98e142a093872811e3"
EXPECTED_TEST="e238852c292bc45960ffd15f2c48305b53ec77763e23e21d20d7982b9600ad56"
[[ "$(sha256sum "$APP_APK" | cut -d " " -f1)" == "$EXPECTED_APP" ]] || { echo "BLOCKED: mauvais APK application (V3 attendu)"; exit 2; }
[[ "$(sha256sum "$TEST_APK" | cut -d " " -f1)" == "$EXPECTED_TEST" ]] || { echo "BLOCKED: mauvais APK de test (V3 attendu)"; exit 2; }
mkdir -p "$OUT"
mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" { print $1 }')
[[ "${#DEVICES[@]}" -eq 1 ]] || { echo "BLOCKED: exiger un seul émulateur ADB, détectés=${#DEVICES[@]}"; exit 3; }
SERIAL="${DEVICES[0]}"
ADB=(adb -s "$SERIAL")
QEMU="$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
QEMU_BOOT="$("${ADB[@]}" shell getprop ro.boot.qemu | tr -d '\r')"
[[ "$SERIAL" == emulator-* && ( "$QEMU" == "1" || "$QEMU_BOOT" == "1" ) ]] || { echo "BLOCKED: cible ADB non confirmée comme émulateur local isolé"; exit 3; }
API="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$API" =~ ^[0-9]+$ ]] && (( API >= 24 )) || { echo "BLOCKED: API Android invalide/non supportée: $API"; exit 3; }
echo "Émulateur ADB=$SERIAL API=$API"
echo "Empreintes APK :"
sha256sum "$APP_APK" "$TEST_APK" | tee "$OUT/apk-sha256.txt"
"${ADB[@]}" wait-for-device
"${ADB[@]}" install -r "$APP_APK" | tee "$OUT/install-app.txt"
"${ADB[@]}" install -r "$TEST_APK" | tee "$OUT/install-test.txt"
if ! "${ADB[@]}" shell pm list instrumentation | tee "$OUT/instrumentation-list.txt" | grep -Fq "com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner"; then
  echo "BLOCKED: instrumentation runner absent"; exit 4
fi
set +e
"${ADB[@]}" shell am instrument -w -r \
  -e class com.sentinel.quantum.ui.AllStaticNavigationSurfacesInstrumentationTest \
  com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner \
  > "$OUT/navigation-smoke.txt" 2>&1
RC=$?
set -e
cat "$OUT/navigation-smoke.txt"
"${ADB[@]}" logcat -d -t 2500 > "$OUT/logcat-tail.txt" 2>&1 || true
if [[ "$RC" -ne 0 ]] || ! grep -Fqx 'INSTRUMENTATION_CODE: -1' "$OUT/navigation-smoke.txt" || ! grep -Fqx 'OK (23 tests)' "$OUT/navigation-smoke.txt" || grep -Eq '^(FAILURES!!!|INSTRUMENTATION_FAILED:)' "$OUT/navigation-smoke.txt"; then
  echo "FAILED: navigation smoke non prouvé; voir $OUT/navigation-smoke.txt"; exit 5
fi
echo "PASS: 23 tests de navigation exécutés sur émulateur; Phone Core physique/VPN/UX restent NON VALIDÉS."
