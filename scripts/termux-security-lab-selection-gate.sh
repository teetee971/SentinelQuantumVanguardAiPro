#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-androidtest"
git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "BLOQUE: copie Git absente"; exit 2; }
test -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" || { echo "BLOQUE: modifications locales"; exit 2; }
git -C "$ROOT" fetch --no-tags origin feature/d1-global-audit-20261020
git -C "$ROOT" checkout --detach "$(git -C "$ROOT" rev-parse FETCH_HEAD)"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:testDebugUnitTest --tests "com.sentinel.quantum.security.ApkSelectionStateTest" --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$ROOT"
echo "VERDICT=SELECTION_APK_ANTI_RESULTATS_PERIMES_JVM_OK"
