#!/data/data/com.termux/files/usr/bin/bash
# V3 immutable snapshot proof: no network, no Gradle, no install, no push.
set -euo pipefail
ROOT="$HOME/sentinel-qa-integrated-v3"
SHA="021e35ecae99c75d067fa73fee0f0d263f06d7cb"
APP_HASH="5d9aa8658aaf338583b55fae80e9af18270d9151ac3dcd98e142a093872811e3"
TEST_HASH="e238852c292bc45960ffd15f2c48305b53ec77763e23e21d20d7982b9600ad56"
fail(){ echo "VERDICT=BLOQUÉ : $*"; exit 2; }
[[ -d "$ROOT/.git" ]] || fail "Dossier V3 absent"
[[ "$(git -C "$ROOT" rev-parse HEAD)" == "$SHA" ]] || fail "Commit V3 différent"
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || fail "Sources V3 modifiées"
APP="$ROOT/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
TEST="$ROOT/native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -s "$APP" && -s "$TEST" ]] || fail "Un APK manque"
[[ "$(sha256sum "$APP" | cut -d' ' -f1)" == "$APP_HASH" ]] || fail "SHA APK debug incorrect"
[[ "$(sha256sum "$TEST" | cut -d' ' -f1)" == "$TEST_HASH" ]] || fail "SHA APK androidTest incorrect"
KOTLIN="$ROOT/native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/AllStaticNavigationSurfacesInstrumentationTest.kt"
[[ -f "$KOTLIN" ]] || fail "Contrat de navigation absent"
COUNT="$(grep -Ec '^[[:space:]]*@Test fun .*Renders\(' "$KOTLIN")"
[[ "$COUNT" == "23" ]] || fail "Nombre de scénarios de navigation inattendu : $COUNT"
echo "VERDICT=CONFORME_V3 : commit et deux APK vérifiés, 23 scénarios déclarés"
echo "ATTENTION : 23 scénarios non exécutés sur Android; production non qualifiée"
