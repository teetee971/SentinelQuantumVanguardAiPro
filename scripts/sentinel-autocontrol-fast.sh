#!/data/data/com.termux/files/usr/bin/bash
# Sentinel Autocontrol: fast non-destructive evidence check; --build delegates to full QA.
set -euo pipefail
ROOT="$HOME/sentinel-qa-integrated-v2"
PIN="7c21bb1cdb9b0de8b7c0261b7e8fb8f98834704c"
APP_SHA="5d9aa8658aaf338583b55fae80e9af18270d9151ac3dcd98e142a093872811e3"
TEST_SHA="29676891c3095a05cd1f66f1cea9c81e75539047e69fbc5bcbf0b0c94653325f"
LOGDIR="$HOME/sentinel-qa-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/autocontrol-$(date +%Y%m%d-%H%M%S)-$$.log"
exec > >(tee "$LOG") 2>&1
fail(){ echo "VERDICT=BLOQUÉ — $*"; exit 2; }
[[ "${1:-}" == "" || "$1" == "--build" ]] || fail "Option inconnue (acceptée : --build)"
command -v git >/dev/null || fail "Git indisponible"
command -v sha256sum >/dev/null || fail "sha256sum indisponible"
[[ -d "$ROOT/.git" ]] || fail "Répertoire intégré introuvable"
CURRENT="$(git -C "$ROOT" rev-parse HEAD)"
[[ "$CURRENT" == "$PIN" ]] || fail "Nouvelle version non qualifiée : $CURRENT ; arrêt sans mise à jour"
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || fail "Fichiers sources modifiés : ne pas réutiliser les anciennes preuves"
APP="$ROOT/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
TEST="$ROOT/native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -s "$APP" && -s "$TEST" ]] || fail "Un APK est absent : lancer bash ~/sentinel-check.sh"
[[ "$(sha256sum "$APP" | awk '{print $1}')" == "$APP_SHA" ]] || fail "APK debug non conforme"
[[ "$(sha256sum "$TEST" | awk '{print $1}')" == "$TEST_SHA" ]] || fail "APK instrumentation non conforme"
echo "SNAPSHOT=$CURRENT"
echo "ARTIFACTS=CONFORMES — SHA-256 des deux APK vérifiés"
if [[ "${1:-}" == "--build" ]]; then
  [[ -f "$HOME/sentinel-check.sh" ]] || fail "Script de build complet introuvable"
  echo "Contrôle approfondi demandé..."
  bash "$HOME/sentinel-check.sh"
  echo "VERDICT=BUILD_COMPLET_RÉUSSI — résultats dans le journal du build"
else
  echo "VERDICT=CONFORME_SANS_REBUILD — pas de compilation, pas de test relancé"
fi
echo "Instrumentation sur émulateur et validation production : NON EXÉCUTÉES"
echo "JOURNAL=$LOG"
