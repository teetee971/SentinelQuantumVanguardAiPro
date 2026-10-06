#!/usr/bin/env bash
# Reuse the built APKs and the current AVD. No role qualification bypass.
set -euo pipefail
OUT="${1:?Evidence directory required}"
mkdir -p "$OUT/viewport"
cleanup() {
  local status=$?
  trap - EXIT
  adb shell wm size reset || status=1
  adb shell wm density reset || status=1
  exit "$status"
}
trap cleanup EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for profile in standard s24plus-equivalent; do
  if [[ "$profile" == standard ]]; then size=1080x2400; density=420; else size=1440x3120; density=480; fi
  adb shell wm size "$size"
  adb shell wm density "$density"
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  adb shell wm size > "$OUT/viewport/$profile-size.txt"
  adb shell wm density > "$OUT/viewport/$profile-density.txt"
  if ! python3 ../scripts/android-viewport-state.py "$OUT/viewport/$profile-size.txt" "$OUT/viewport/$profile-density.txt" "$size" "$density" > "$OUT/viewport/$profile-effective.json"; then
    echo "Viewport $profile did not reach requested $size / $density dpi."
    cat "$OUT/viewport/$profile-size.txt" "$OUT/viewport/$profile-density.txt"
    exit 1
  fi
  timeout --signal=INT --kill-after=10s 180s adb shell am instrument -w -r \
    -e class com.sentinel.quantum.ui.MainNavigationQualificationTest \
    -e qualificationProfile "$profile" \
    com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner \
    > "$OUT/viewport/$profile-tests.txt" 2>&1
  bash ../scripts/android-logcat-collect.sh "$OUT/viewport/$profile-logcat.txt"
  node ../scripts/android-logcat-analysis.cjs "$OUT/viewport/$profile-logcat.txt" "$OUT/viewport/$profile-crash-anr.json"
  # am instrument often exits 0 even for assertion failures. Require all three methods.
  if ! grep -Eq '^OK \(3 tests\)' "$OUT/viewport/$profile-tests.txt"; then
    cat "$OUT/viewport/$profile-tests.txt"
    exit 1
  fi
  if grep -Eq '^FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE: -[1234]' "$OUT/viewport/$profile-tests.txt"; then
    cat "$OUT/viewport/$profile-tests.txt"
    exit 1
  fi
  adb pull "/sdcard/Pictures/SentinelQualification/$profile" "$OUT/viewport/$profile"
done
python3 - "$OUT/viewport" <<'PY'
import json, pathlib, struct, sys
root = pathlib.Path(sys.argv[1])
screens = ['home', 'communications', 'protection', 'more', 'communications-recreated',
           'protection-recreated', 'more-recreated', 'home-relaunch', 'phone-core', 'dialer-keypad', 'sms']
profiles = []
for profile, dimensions, density in [('standard', (1080, 2400), 420), ('s24plus-equivalent', (1440, 3120), 480)]:
    for screen in screens:
        data = (root / profile / (screen + '.png')).read_bytes()
        if len(data) < 256 or data[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', data[16:24]) != dimensions:
            raise SystemExit(f'Invalid screenshot/dimensions: {profile}/{screen}')
    profiles.append(dict(profile=profile, width=dimensions[0], height=dimensions[1], density=density,
                         tests=3, result='PASS', screenshots=screens, oem_validation=False))
(root / 'summary.json').write_text(json.dumps(dict(schema_version=1, result='PASS', profiles=profiles), indent=2) + '\n')
PY
