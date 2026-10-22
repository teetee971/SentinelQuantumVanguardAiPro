#!/data/data/com.termux/files/usr/bin/bash
# Candidate 1.0.10 read-only provenance/signature check. No keystore or installation.
set -euo pipefail
ROOT="$HOME/sentinel-qa-release-110"
APK="$ROOT/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
TEST="$ROOT/native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
EXPECTED_COMMIT="5d7d1c99cdbcc51da2a3a508d4acb76c93f47dec"
EXPECTED_APK="f97c7cc024a806c401d9e16641f1306ef8ec32fb267d70d132bf223f9d86a82d"
EXPECTED_TEST="e238852c292bc45960ffd15f2c48305b53ec77763e23e21d20d7982b9600ad56"
fail(){ echo "VERDICT=BLOQUÉ : $*"; exit 2; }
[[ -d "$ROOT/.git" && -s "$APK" && -s "$TEST" ]] || fail "Dossier ou fichiers candidats absents"
[[ "$(git -C "$ROOT" rev-parse HEAD)" == "$EXPECTED_COMMIT" ]] || fail "Commit candidat différent"
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || fail "Code local modifié"
[[ "$(sha256sum "$APK" | cut -d' ' -f1)" == "$EXPECTED_APK" ]] || fail "APK principal non conforme"
[[ "$(sha256sum "$TEST" | cut -d' ' -f1)" == "$EXPECTED_TEST" ]] || fail "APK de tests non conforme"
echo "INTEGRITE=OK ; COMMIT=$EXPECTED_COMMIT"
proot-distro login ubuntu -- bash -c '
  set -euo pipefail
  apk="$1"
  tool="/root/android-sdk/build-tools/36.0.0/apksigner"
  [[ -x "$tool" ]] || { echo "BLOQUÉ : apksigner absent"; exit 2; }
  timeout 75s "$tool" verify --verbose --print-certs "$apk"
' _ "$APK"
echo "VERDICT=DEBUG_SIGNÉ_ET_CONFORME — uniquement si apksigner a confirmé la validité"
echo "GATE_PRODUCTION=BLOQUÉ : signature debug, tests instrumentés non exécutés, certificat installé non vérifié"
