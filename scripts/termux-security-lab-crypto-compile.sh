#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BASE="$HOME/sentinel-qa-release-110"
OUT="$HOME/sentinel-security-lab-crypto"
BRANCH="feature/d1-global-audit-20261020"
[[ -d "$BASE/.git" || -f "$BASE/.git" ]] || { echo "BLOQUÉ : dépôt source introuvable"; exit 2; }
git -C "$BASE" fetch --no-tags origin "$BRANCH"
REV="$(git -C "$BASE" rev-parse FETCH_HEAD)"
if [[ -e "$OUT" ]]; then
  [[ "$(git -C "$OUT" rev-parse HEAD 2>/dev/null || true)" == "$REV" ]] || { echo "BLOQUÉ : dossier de qualification différent ; non modifié"; exit 2; }
else
  git -C "$BASE" worktree add --detach "$OUT" "$REV"
fi
[[ -z "$(git -C "$OUT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ : modifications locales"; exit 2; }
echo "SOURCES=$REV"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$OUT"
echo "VERDICT=CRYPTO_INTEGREE_COMPILEE_ET_TESTS_UNITAIRES_REUSSIS (PAS de test sur appareil)"
