#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-androidtest"
[[ -d "$ROOT" ]] || { echo "BLOQUÉ : copie de qualification absente"; exit 2; }
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ : modifications locales, rien écrasé"; exit 2; }
git -C "$ROOT" fetch --no-tags origin feature/d1-global-audit-20261020
REV="$(git -C "$ROOT" rev-parse FETCH_HEAD)"
git -C "$ROOT" checkout --detach "$REV"
echo "CORRECTIF_SOURCE=$REV"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$ROOT"
echo "VERDICT=ANDROID_TESTS_COMPILES_NON_EXECUTES"
