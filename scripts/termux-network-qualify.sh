#!/data/data/com.termux/files/usr/bin/bash
# Sentinel: manual, local-only Android qualification from Termux.
# Does not run GitHub Actions, install APKs, access signing keys or alter phone roles.
set -euo pipefail
REPO="${1:-$HOME/sentinel-network-qa}"
REF="${2:-docs/network-center-beyond-wifiman-20261020}"
URL="https://github.com/teetee971/SentinelQuantumVanguardAiPro.git"
AAPT="$HOME/sentinel-aapt2-amd64/gradle-wrapper/aapt2"
LOGDIR="$HOME/sentinel-qa-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/qualify-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG") 2>&1
echo "Journal: $LOG"
command -v git >/dev/null || { echo "git absent"; exit 2; }
command -v proot-distro >/dev/null || { echo "proot-distro absent"; exit 2; }
test -x "$AAPT" || { echo "AAPT2 ARM64 wrapper absent: $AAPT"; exit 2; }
if [[ ! -d "$REPO/.git" ]]; then
  [[ ! -e "$REPO" ]] || { echo "Dossier existant non Git; abandon."; exit 2; }
  git clone --no-checkout "$URL" "$REPO"
fi
[[ "$(git -C "$REPO" remote get-url origin)" == "$URL" ]] || {
  echo "Dépôt d'origine différent. Abandon."; exit 2;
}
[[ -z "$(git -C "$REPO" status --porcelain)" ]] || {
  echo "Worktree non propre; aucune modification automatique."; exit 3;
}
git -C "$REPO" fetch --no-tags origin "$REF"
git -C "$REPO" checkout --detach FETCH_HEAD
SHA="$(git -C "$REPO" rev-parse HEAD)"
echo "COMMIT=$SHA"
echo "REF=$REF"
APP="$REPO/native-android-app"
test -f "$APP/gradlew" || { echo "Projet Android absent"; exit 2; }
# Our proot shares the Termux home through an Android data path.
ANDROID_PATH="$APP"
export SENTINEL_QA_ANDROID_PATH="$ANDROID_PATH"
proot-distro login ubuntu -- bash -c '
  set -euo pipefail
  cd "$SENTINEL_QA_ANDROID_PATH"
  export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
  export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug \
    --no-daemon --console=plain \
    -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
'
[[ "$(git -C "$REPO" rev-parse HEAD)" == "$SHA" ]] || { echo "HEAD modifié pendant la compilation"; exit 4; }
[[ -z "$(git -C "$REPO" status --porcelain --untracked-files=no)" ]] || {
  echo "Fichiers suivis modifiés pendant la compilation"; exit 4;
}
APK="$APP/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK" || { echo "APK debug absent"; exit 5; }
sha256sum "$APK"
echo "SUCCES LOCAL : compilation et tests unitaires Gradle du commit $SHA"
echo "PAS une preuve de tests instrumentés, de signature de production ou d'installation."
