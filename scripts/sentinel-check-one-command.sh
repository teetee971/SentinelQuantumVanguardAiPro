#!/data/data/com.termux/files/usr/bin/bash
# Sentinel one-command local QA, safe for phone. No push/install/ADB/network server.
set -euo pipefail
ROOT="${1:-$HOME/sentinel-qa-integrated-v2}"
PIN="7c21bb1cdb9b0de8b7c0261b7e8fb8f98834704c"
LOGDIR="$HOME/sentinel-qa-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/sentinel-check-$(date +%Y%m%d-%H%M%S)-$$.log"
exec > >(tee "$LOG") 2>&1
echo "SENTINEL LOCAL QA — rapport: $LOG"
fail() { echo "VERDICT=BLOQUÉ — $1"; exit 2; }
command -v git >/dev/null || fail "git absent"
command -v proot-distro >/dev/null || fail "Ubuntu proot indisponible"
test -d "$ROOT/.git" || fail "Dossier intégré absent : $ROOT"
HEAD="$(git -C "$ROOT" rev-parse HEAD)"
[[ "$HEAD" == "$PIN" ]] || fail "Commit non qualifié : $HEAD (attendu $PIN)"
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || fail "Fichiers suivis modifiés"
test -x "$HOME/sentinel-aapt2-amd64/gradle-wrapper/aapt2" || fail "aapt2 absent"
APP="$ROOT/native-android-app"
echo "COMMIT=$HEAD"
if proot-distro login ubuntu -- bash -c '
  set -euo pipefail
  cd "$1"
  export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
  export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest \
    --no-daemon --console=plain \
    -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$APP"; then
  [[ "$(git -C "$ROOT" rev-parse HEAD)" == "$PIN" ]] || fail "Commit modifié durant qualification"
  [[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || fail "Code source modifié pendant qualification"
  APK="$APP/app/build/outputs/apk/debug/app-debug.apk"
  TEST_APK="$APP/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  [[ -s "$APK" && -s "$TEST_APK" ]] || fail "APK manquant"
  sha256sum "$APK" "$TEST_APK"
  echo "VERDICT=RÉUSSI — compilation, tests unitaires, APK et compilation androidTest."
  echo "IMPORTANT : instrumentation non exécutée ; aucune installation ni validation de production."
else
  echo "VERDICT=ÉCHEC — examiner les lignes FAILURE ci-dessus."
  exit 1
fi
