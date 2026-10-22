#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BASE="$HOME/sentinel-qa-release-110"
OUT="$HOME/sentinel-security-lab-archive-tests"
[[ -e "$BASE/.git" ]] || { echo "BLOQUÉ: dépôt source absent"; exit 2; }
git -C "$BASE" fetch --no-tags origin feature/d1-global-audit-20261020
REV="$(git -C "$BASE" rev-parse FETCH_HEAD)"
if [[ -e "$OUT" ]]; then
 [[ "$(git -C "$OUT" rev-parse HEAD 2>/dev/null || true)" == "$REV" ]] || { echo "BLOQUÉ: dossier occupé ; aucune suppression"; exit 2; }
else
 git -C "$BASE" worktree add --detach "$OUT" "$REV"
fi
echo "SOURCE=$REV"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:testDebugUnitTest --tests "com.sentinel.quantum.security.ApkArchiveValidatorTest" --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$OUT"
python - "$OUT/native-android-app/app/build/test-results/testDebugUnitTest" <<'PY'
import sys,pathlib,xml.etree.ElementTree as ET
matches=list(pathlib.Path(sys.argv[1]).rglob("TEST-*ApkArchiveValidatorTest.xml"))
if len(matches)!=1: raise SystemExit("BLOQUÉ: rapport XML manquant ou ambigu")
r=ET.parse(matches[0]).getroot()
tests=int(r.attrib.get("tests",0)); skips=int(r.attrib.get("skipped",0))
errors=int(r.attrib.get("errors",0)); failures=int(r.attrib.get("failures",0))
print(f"TESTS={tests} SKIPPED={skips} FAILURES={failures} ERRORS={errors}")
if (tests,skips,errors,failures)!=(6,0,0,0): raise SystemExit("VERDICT=ECHEC_OU_PREUVE_INCOMPLETE")
print("VERDICT=ARCHIVES_APK_CONTROLES_6_SUR_6")
PY
