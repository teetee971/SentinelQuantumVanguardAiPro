#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BASE="$HOME/sentinel-qa-release-110"
OUT="$HOME/sentinel-security-lab-proof"
FIXTURE="$BASE/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$FIXTURE" ]] || { echo "BLOQUÉ : APK debug de référence absent"; exit 2; }
[[ -d "$BASE/.git" || -f "$BASE/.git" ]] || { echo "BLOQUÉ : dépôt source absent"; exit 2; }
git -C "$BASE" fetch --no-tags origin feature/d1-global-audit-20261020
REV="$(git -C "$BASE" rev-parse FETCH_HEAD)"
if [[ -e "$OUT" ]]; then
 [[ "$(git -C "$OUT" rev-parse HEAD 2>/dev/null || true)" == "$REV" ]] || { echo "BLOQUÉ : dossier d'essai déjà occupé"; exit 2; }
else
 git -C "$BASE" worktree add --detach "$OUT" "$REV"
fi
echo "SOURCES=$REV"
echo "FIXTURE_SHA256=$(sha256sum "$FIXTURE" | cut -d' ' -f1)"
proot-distro login ubuntu -- bash -c '
 set -euo pipefail
 cd "$1/native-android-app"
 export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
 export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
 ./gradlew :app:testDebugUnitTest --tests "com.sentinel.quantum.security.ApkSignatureVerifierProofTest" -PsentinelSignatureFixture="$2" --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$OUT" "$FIXTURE"
echo "VERDICT=TEST_JVM_APKSIG_FIXTURE_VALIDE_ET_ALTEREE_REUSSI"
echo "Aucune installation, aucune modification de l'APK source."
