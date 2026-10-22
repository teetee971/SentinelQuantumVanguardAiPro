#!/data/data/com.termux/files/usr/bin/bash
# Qualification locale isolée du candidat 1.0.10 ; pas de push, signature, installation.
set -euo pipefail
DEST="${1:-$HOME/sentinel-qa-release-110}"
URL="https://github.com/teetee971/SentinelQuantumVanguardAiPro.git"
BASE="191cc89d92599b31b3811c78a758cf26659cb81c"
SOURCES=("1718:383a2afd1c7312a1d4cd9a84e3762dce043ca99d" "1719:5624aa4f618df5935aa1824735624936af96e25e" "1720:5220c48f19ef82ff58647c79020d47a485269a16")
LOGDIR="$HOME/sentinel-qa-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/release-110-$(date +%Y%m%d-%H%M%S)-$$.log"
exec > >(tee -a "$LOG") 2>&1
[[ ! -e "$DEST" ]] || { echo "BLOQUÉ : destination déjà présente"; exit 2; }
for cmd in git proot-distro sha256sum; do command -v "$cmd" >/dev/null || { echo "BLOQUÉ : outil absent $cmd"; exit 2; }; done
test -x "$HOME/sentinel-aapt2-amd64/gradle-wrapper/aapt2" || { echo "BLOQUÉ : AAPT2 absent"; exit 2; }
git clone --no-checkout "$URL" "$DEST"
cd "$DEST"
git fetch --no-tags origin refs/heads/release/candidate-1.0.10-preflight-20261021 refs/pull/1718/head refs/pull/1719/head refs/pull/1720/head
for sha in "$BASE" "${SOURCES[@]#*:}"; do git cat-file -e "$sha^{commit}" || { echo "BLOQUÉ : commit introuvable $sha"; exit 3; }; done
git switch --detach "$BASE"
git -c user.name="Sentinel Local QA" -c user.email="sentinel-local-qa@users.noreply.github.com" switch -c qa/release-110
for entry in "${SOURCES[@]}"; do
  id="${entry%%:*}"; sha="${entry#*:}"
  echo "Intégration PR #$id ($sha)"
  if ! git -c user.name="Sentinel Local QA" -c user.email="sentinel-local-qa@users.noreply.github.com" merge --no-ff --no-edit "$sha"; then
    echo "BLOQUÉ : conflit lors de l'intégration PR #$id"
    git merge --abort || true
    exit 4
  fi
done
ACTUAL="$(git rev-parse HEAD)"
echo "CANDIDAT_INTEGRE_SHA=$ACTUAL"
APP="$DEST/native-android-app"
proot-distro login ubuntu -- bash -c '
 set -euo pipefail
 cd "$1"
 export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
 export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$APP"
[[ "$(git rev-parse HEAD)" == "$ACTUAL" && -z "$(git status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ : sources modifiées"; exit 5; }
grep -Eq '^[[:space:]]*versionCode 11$' "$APP/app/build.gradle" || { echo "BLOQUÉ : versionCode"; exit 5; }
grep -Eq '^[[:space:]]*versionName "1.0.10"$' "$APP/app/build.gradle" || { echo "BLOQUÉ : versionName"; exit 5; }
for apk in "$APP/app/build/outputs/apk/debug/app-debug.apk" "$APP/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"; do
 test -s "$apk" || { echo "BLOQUÉ : APK manquant"; exit 5; }
 sha256sum "$apk"
done
echo "QUALIFICATION_LOCALE_RÉUSSIE — compilation uniquement, aucune instrumentation exécutée, signature production ou installation."
