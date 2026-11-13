#!/data/data/com.termux/files/usr/bin/bash
# Integration QUALIFICATION ONLY: no pushes, no APK install, no signing.
set -euo pipefail
DEST="${1:-$HOME/sentinel-qa-integrated-v3}"
URL="https://github.com/teetee971/SentinelQuantumVanguardAiPro.git"
LOGDIR="$HOME/sentinel-qa-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/integrate-$(date +%Y%m%d-%H%M%S)-$$.log"
exec > >(tee -a "$LOG") 2>&1
echo "Journal: $LOG"
command -v git >/dev/null || { echo "Git absent"; exit 2; }
command -v proot-distro >/dev/null || { echo "proot-distro absent"; exit 2; }
command -v sha256sum >/dev/null || { echo "sha256sum absent"; exit 2; }
test -x "$HOME/sentinel-aapt2-amd64/gradle-wrapper/aapt2" || { echo "AAPT2 absent"; exit 2; }
# Fixed SHAs: any change to one source branch requires explicit review and script update.
SOURCES=(
  "1718:383a2afd1c7312a1d4cd9a84e3762dce043ca99d"
  "1719:5624aa4f618df5935aa1824735624936af96e25e"
  "1720:5220c48f19ef82ff58647c79020d47a485269a16"
  "1721:555737368c5d85fba9846fc6723f551ae27b5bcd"
)
if [[ -e "$DEST" ]]; then
  echo "Dossier déjà présent: $DEST. Aucun écrasement; choisissez un autre répertoire."; exit 2
fi
git clone --no-checkout "$URL" "$DEST"
cd "$DEST"
git fetch --no-tags origin refs/pull/1718/head refs/pull/1719/head refs/pull/1720/head refs/pull/1721/head
# Verify exact pinned commits; refuse changes to source snapshots.
for source in "${SOURCES[@]}"; do
  n="${source%%:*}"; sha="${source#*:}"
  git cat-file -e "$sha^{commit}" || { echo "Commit absent pour #$n"; exit 3; }
done
BASE="${SOURCES[3]#*:}"
git switch --detach "$BASE"
git -c user.name="Sentinel Local QA" -c user.email="sentinel-local-qa@users.noreply.github.com" \
  switch -c qa/d1-integrated
for idx in 0 1 2; do
  source="${SOURCES[$idx]}"; n="${source%%:*}"; sha="${source#*:}"
  echo "=== Fusion locale PR #$n ($sha) ==="
  if ! git -c user.name="Sentinel Local QA" -c user.email="sentinel-local-qa@users.noreply.github.com" \
      merge --no-ff --no-edit "$sha"; then
    echo "CONFLIT PR #$n : aucun fichier ne sera résolu automatiquement."
    git diff --name-only --diff-filter=U
    git merge --abort || true
    exit 4
  fi
done
FINAL="$(git rev-parse HEAD)"
echo "INTEGRATION_SHA=$FINAL"
echo "PARENTS: PR #1721 + fusions locales #1718 #1719 #1720"
APP="$DEST/native-android-app"
proot-distro login ubuntu -- bash -c '
  set -euo pipefail
  cd "$1"
  export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
  export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest \
    --no-daemon --console=plain \
    -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$APP"
[[ "$(git rev-parse HEAD)" == "$FINAL" ]] || { echo "SHA modifié pendant build"; exit 5; }
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || { echo "Fichiers suivis modifiés"; exit 5; }
APK="$APP/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK" || { echo "APK debug absent"; exit 5; }
TESTAPK="$APP/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test -s "$TESTAPK" || { echo "APK tests absent"; exit 5; }
sha256sum "$APK" "$TESTAPK"
echo "SUCCES INTEGRATION LOCALE $FINAL — aucune installation, signature production ou test instrumenté."
