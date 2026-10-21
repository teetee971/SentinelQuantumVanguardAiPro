#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BASE="$HOME/sentinel-qa-release-110"
OUT="$HOME/sentinel-security-lab-androidtest"
[[ -e "$BASE/.git" ]] || { echo "BLOQUÉ: dépôt de référence absent"; exit 2; }
git -C "$BASE" fetch --no-tags origin feature/d1-global-audit-20261020
REV="$(git -C "$BASE" rev-parse FETCH_HEAD)"
if [[ -e "$OUT" ]]; then
 [[ "$(git -C "$OUT" rev-parse HEAD 2>/dev/null || true)" == "$REV" ]] || { echo "BLOQUÉ: dossier déjà utilisé, aucune suppression"; exit 2; }
else
 git -C "$BASE" worktree add --detach "$OUT" "$REV"
fi
[[ -z "$(git -C "$OUT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ: source locale modifiée"; exit 2; }
echo "SOURCE=$REV"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$OUT"
echo "VERDICT=ANDROID_UI_TESTS_COMPILES_NON_EXECUTES"
