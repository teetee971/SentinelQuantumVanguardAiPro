#!/data/data/com.termux/files/usr/bin/bash
# Sentinel S24+ read-only qualification build. No APK installation, pairing, or key access.
set -uo pipefail
HOME_DIR="${HOME}"
ROOT="${HOME_DIR}/sentinel-bluetooth-test/native-android-app"
AAPT="${HOME_DIR}/sentinel-aapt2-amd64/gradle-wrapper/aapt2"
LOG_DIR="${HOME_DIR}/sentinel-qa-logs"
mkdir -p "${LOG_DIR}"
LOG="${LOG_DIR}/android-qa-$(date +%Y%m%d-%H%M%S).log"
if [[ ! -f "${ROOT}/gradlew" || ! -x "${AAPT}" ]]; then
  echo "Pré-requis absents : vérifier le worktree Android et le wrapper AAPT2."
  exit 2
fi
printf 'Démarrage qualification Android. Journal : %s\n' "${LOG}"
CURRENT_SHA=$(git -C "${HOME_DIR}/sentinel-bluetooth-test" rev-parse HEAD 2>/dev/null || true)
EXPECTED_SHA="${SENTINEL_EXPECTED_SHA:-}"
if [[ -n "${EXPECTED_SHA}" && "${CURRENT_SHA}" != "${EXPECTED_SHA}" ]]; then
  echo "ÉCHEC : worktree sur ${CURRENT_SHA}, commit attendu ${EXPECTED_SHA}. Aucune compilation lancée." | tee -a "${LOG}"
  exit 4
fi
if [[ -z "${EXPECTED_SHA}" ]]; then
  echo "ATTENTION : aucun commit attendu fourni. Résultat limité au commit local ${CURRENT_SHA}." | tee -a "${LOG}"
fi
{
  echo "=== PROVENANCE GIT ==="
  git -C "${HOME_DIR}/sentinel-bluetooth-test" log -1 --format="%H %s" || true
  git -C "${HOME_DIR}/sentinel-bluetooth-test" status --short || true
  echo "=== VERSION BUILD ==="
  grep -E "versionCode|versionName|applicationId" "${ROOT}/app/build.gradle" || true
} | tee -a "${LOG}"
proot-distro login ubuntu -- bash -c '
  cd /data/data/com.termux/files/home/sentinel-bluetooth-test/native-android-app || exit 2
  export ANDROID_HOME=/root/android-sdk
  export ANDROID_SDK_ROOT=/root/android-sdk
  export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug \
    --no-daemon --console=plain \
    -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' 2>&1 | tee -a "${LOG}"
rc=${PIPESTATUS[0]}
if [[ "${rc}" -ne 0 ]]; then
  echo "ÉCHEC build/tests (${rc}). Voir ${LOG}"
  exit "${rc}"
fi
APK="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "${APK}" ]]; then
  echo "ÉCHEC : APK Debug absent"
  exit 3
fi
sha256sum "${APK}" | tee -a "${LOG}"
echo "QUALIFICATION LOCALE RÉUSSIE : build/tests. APK DEBUG uniquement."
echo "Ne pas installer cet APK : une signature compatible doit être vérifiée séparément."
echo "Aucun changement de données Android, de rôles, de signature ni de Play Protect."
