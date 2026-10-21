#!/data/data/com.termux/files/usr/bin/bash
# Free/local Security Lab verification, no CI charges, no installations.
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-androidtest"
[[ -e "$ROOT/.git" ]] || { echo "BLOQUÉ: copie Security Lab absente"; exit 2; }
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ: sources modifiées"; exit 2; }
git -C "$ROOT" fetch --no-tags origin feature/d1-global-audit-20261020
git -C "$ROOT" checkout --detach "$(git -C "$ROOT" rev-parse FETCH_HEAD)"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$ROOT"
APP="$ROOT/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
TEST="$ROOT/native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -s "$APP" && -s "$TEST" ]] || { echo "BLOQUÉ: artefacts incomplets"; exit 3; }
echo "=== PREUVES APK ==="
sha256sum "$APP" "$TEST"
echo "VERDICT=QUALIFICATION_LOCALE_COMPILEE_TESTS_JVM_OK"
echo "GATE_EMULATEUR=EN_ATTENTE ; zéro installation ; zéro paiement"
