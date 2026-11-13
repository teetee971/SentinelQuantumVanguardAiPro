#!/data/data/com.termux/files/usr/bin/bash
# Sentinel release readiness gate. Read-only; never signs, installs, or accesses keystores.
set -euo pipefail
ROOT="$HOME/sentinel-qa-integrated-v3"
GRADLE="$ROOT/native-android-app/app/build.gradle"
APK="$ROOT/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
SNAPSHOT="021e35ecae99c75d067fa73fee0f0d263f06d7cb"
DEBUG_SHA="5d9aa8658aaf338583b55fae80e9af18270d9151ac3dcd98e142a093872811e3"
EXPECTED_PRODUCTION_CERT="5a21315e168410303a7ade3dc21faadb6f7fbc4ae090f98c7bcb45d40c5cccb8"
fail() { echo "VERDICT=BLOQUÉ — $*"; exit 2; }
[[ -f "$GRADLE" && -s "$APK" ]] || fail "Sources ou APK V3 absents"
[[ "$(git -C "$ROOT" rev-parse HEAD)" == "$SNAPSHOT" ]] || fail "Snapshot local inattendu"
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || fail "Sources locales modifiées"
[[ "$(sha256sum "$APK" | cut -d' ' -f1)" == "$DEBUG_SHA" ]] || fail "APK debug différent"
APP_ID="$(sed -nE 's/^[[:space:]]*applicationId "([^"]+)".*/\1/p' "$GRADLE" | head -n1)"
VC="$(sed -nE 's/^[[:space:]]*versionCode ([0-9]+).*/\1/p' "$GRADLE" | head -n1)"
VN="$(sed -nE 's/^[[:space:]]*versionName "([^"]+)".*/\1/p' "$GRADLE" | head -n1)"
[[ "$APP_ID" == "com.sentinel.quantum" && "$VC" =~ ^[0-9]+$ ]] || fail "Métadonnées applicatives inattendues"
echo "PACKAGE=$APP_ID"
echo "VERSION_ACTUELLE=$VN (versionCode=$VC)"
echo "CERTIFICAT_PRODUCTION_ATTENDU_SHA256=$EXPECTED_PRODUCTION_CERT"
echo "APK_DEBUG_SHA256=$DEBUG_SHA"
if (( VC <= 10 )); then
  echo "GATE_VERSION=BLOQUÉ — candidate de mise à jour exige versionCode > 10 (sous réserve de la version réellement installée)"
else
  echo "GATE_VERSION=À CONFIRMER — comparer à la version réellement installée"
fi
echo "GATE_CERTIFICAT=BLOQUÉ — signature debug distincte de la production; certificat installé non recontrôlé"
echo "GATE_RUNTIME=BLOQUÉ — instrumentation/validation réelle non effectuées"
echo "VERDICT=NON_PRÊT_PRODUCTION — lecture seule, aucune signature ni installation"
