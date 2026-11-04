#!/data/data/com.termux/files/usr/bin/bash
# Compile instrumentation tests only. No emulator, phone install, signing or push.
set -euo pipefail
REPO="${1:-$HOME/sentinel-qa-integrated-v2}"
EXPECTED="7c21bb1cdb9b0de8b7c0261b7e8fb8f98834704c"
LOGDIR="$HOME/sentinel-qa-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/instrumentation-build-$(date +%Y%m%d-%H%M%S)-$$.log"
exec > >(tee -a "$LOG") 2>&1
test -d "$REPO/.git" || { echo "Intégration locale absente : $REPO"; exit 2; }
ACTUAL="$(git -C "$REPO" rev-parse HEAD)"
[[ "$ACTUAL" == "$EXPECTED" ]] || { echo "SHA inattendu : $ACTUAL"; exit 3; }
[[ -z "$(git -C "$REPO" status --porcelain --untracked-files=no)" ]] || { echo "Modifications locales suivies; arrêt."; exit 3; }
command -v proot-distro >/dev/null || { echo "proot-distro absent"; exit 2; }
test -x "$HOME/sentinel-aapt2-amd64/gradle-wrapper/aapt2" || { echo "AAPT2 absent"; exit 2; }
APP="$REPO/native-android-app"
echo "SHA=$EXPECTED"
echo "Compilation des tests androidTest ; aucune exécution sur appareil"
proot-distro login ubuntu -- bash -c '
 set -euo pipefail
 cd "$1"
 export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
 export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
 ./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$APP"
[[ "$(git -C "$REPO" rev-parse HEAD)" == "$EXPECTED" ]] || { echo "SHA changé"; exit 4; }
[[ -z "$(git -C "$REPO" status --porcelain --untracked-files=no)" ]] || { echo "Arbre suivi modifié"; exit 4; }
APK="$APP/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if [[ -s "$APK" ]]; then sha256sum "$APK"; else echo "APK de tests non trouvé à l'emplacement attendu; vérifier outputs/apk"; fi
echo "TESTS INSTRUMENTÉS COMPILÉS UNIQUEMENT : ni exécutés ni validés sur appareil."
