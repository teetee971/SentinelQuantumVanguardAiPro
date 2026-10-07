#!/usr/bin/env python3
from pathlib import Path
import shutil


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)


build_path = Path('.github/workflows/build-native-android.yml')
build = build_path.read_text()
build = replace_once(
    build,
    '''          APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" -type f -print -quit)\n          test -n "$APK_PATH"\n\n          SDK_PACKAGES=("emulator" "platforms;android-29" "system-images;android-29;google_apis;x86_64")''',
    '''          APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" -type f -print -quit)\n          test -n "$APK_PATH"\n\n          # Shared oracle owns FATAL EXCEPTION: attribution and ANR in com\\.sentinel\\.quantum attribution.\n          assert_clean_logcat() {\n            local context="$1"\n            local slug="$2"\n            local evidence="$RUNNER_TEMP/${slug}-logcat-brief.txt"\n            local error="$RUNNER_TEMP/${slug}-logcat-error.txt"\n            local status=0\n            adb logcat -d -v brief > "$evidence" 2> "$error" || status=$?\n            if [[ "$status" -ne 0 ]]; then\n              echo "Unable to read logcat while ${context}; crash absence is UNKNOWN."\n              cat "$error" || true\n              return 1\n            fi\n            if ! python3 ../scripts/phone-core-logcat-crash-oracle.py "$evidence"; then\n              echo "Sentinel crashed or ANR'd while ${context}."\n              adb logcat -d -v time | tail -n 400 || true\n              return 1\n            fi\n          }\n\n          SDK_PACKAGES=("emulator" "platforms;android-29" "system-images;android-29;google_apis;x86_64")''',
    'build helper insertion',
)
build = replace_once(
    build,
    '''            if adb logcat -d -v brief | grep -Eq 'FATAL EXCEPTION: main|ANR in com\\.sentinel\\.quantum'; then\n              echo "Sentinel crashed or ANR'd during launch."\n              adb logcat -d -v time | tail -n 400\n              exit 1\n            fi''',
    '''            assert_clean_logcat "waiting for the first Phone Core setup launch" "sentinel-first-launch"''',
    'first launch oracle',
)
build = replace_once(
    build,
    '''            if adb logcat -d -v brief | grep -Eq 'FATAL EXCEPTION: main|ANR in com\\.sentinel\\.quantum'; then\n              echo "Sentinel crashed or ANR'd while resuming Phone Core setup."\n              adb logcat -d -v time | tail -n 400\n              exit 1\n            fi''',
    '''            assert_clean_logcat "resuming Phone Core setup" "sentinel-setup-resume"''',
    'resume oracle',
)
build_path.write_text(build)

emu_path = Path('.github/workflows/android-emulation-qualification.yml')
emu = emu_path.read_text()
emu = replace_once(
    emu,
    '''          OUTPUT="$RUNNER_TEMP/phone-core-emulation-api${API_LEVEL}"\n          mkdir -p "$OUTPUT"\n          # Runtime evidence must start from a fresh app scope and fresh log buffer.''',
    '''          OUTPUT="$RUNNER_TEMP/phone-core-emulation-api${API_LEVEL}"\n          mkdir -p "$OUTPUT"\n\n          # Shared oracle owns FATAL EXCEPTION: attribution and ANR in com\\.sentinel\\.quantum attribution.\n          assert_clean_runtime_logcat() {\n            local context="$1"\n            local evidence="$OUTPUT/min-sdk-logcat-brief.txt"\n            local error="$OUTPUT/min-sdk-logcat-error.txt"\n            local status=0\n            adb logcat -d -v brief > "$evidence" 2> "$error" || status=$?\n            if [[ "$status" -ne 0 ]]; then\n              echo "Unable to read logcat while ${context}; crash absence is UNKNOWN."\n              cat "$error" || true\n              return 1\n            fi\n            if ! python3 ../scripts/phone-core-logcat-crash-oracle.py "$evidence"; then\n              echo "Crash/ANR detected while ${context}."\n              adb logcat -d -v time | tail -n 400 || true\n              return 1\n            fi\n          }\n\n          # Runtime evidence must start from a fresh app scope and fresh log buffer.''',
    'emulation helper insertion',
)
emu = replace_once(
    emu,
    '''            if adb logcat -d -v brief | grep -Eq 'FATAL EXCEPTION:|ANR in com\\.sentinel\\.quantum'; then\n              echo "Crash/ANR detected on minSdk first launch."\n              adb logcat -d -v time | tail -n 400\n              exit 1\n            fi''',
    '''            assert_clean_runtime_logcat "qualifying the minSdk first launch"''',
    'minSdk oracle',
)
emu = replace_once(
    emu,
    "          const path = require('node:path');\n          const api = Number(process.env.API_LEVEL);",
    "          const path = require('node:path');\n          const { spawnSync } = require('node:child_process');\n          const api = Number(process.env.API_LEVEL);",
    'spawnSync import',
)
emu = replace_once(
    emu,
    '''          const crashOrAnrAbsent = read('logcat-status.txt').trim() === '0' && nonEmpty('logcat.txt') &&\n            !/FATAL EXCEPTION:|ANR in com\\.sentinel\\.quantum/.test(read('logcat.txt'));''',
    '''          const crashOraclePath = process.env.GITHUB_WORKSPACE\n            ? path.join(process.env.GITHUB_WORKSPACE, 'scripts', 'phone-core-logcat-crash-oracle.py')\n            : path.resolve(process.env.PWD || '.', 'scripts', 'phone-core-logcat-crash-oracle.py');\n          const crashOracle = exists('logcat.txt')\n            ? spawnSync('python3', [crashOraclePath, file('logcat.txt')], { encoding: 'utf8' })\n            : { status: 2 };\n          const crashOrAnrAbsent = read('logcat-status.txt').trim() === '0' && nonEmpty('logcat.txt') && crashOracle.status === 0;''',
    'machine verdict oracle',
)
emu_path.write_text(emu)

out = Path('ci-patch-output')
out.mkdir(exist_ok=True)
shutil.copyfile(build_path, out / 'build-native-android.yml.txt')
shutil.copyfile(emu_path, out / 'android-emulation-qualification.yml.txt')
