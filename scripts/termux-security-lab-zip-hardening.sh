#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BASE="$HOME/sentinel-qa-release-110"
OUT="$HOME/sentinel-security-lab-zip-hardening"
[[ -e "$BASE/.git" ]] || { echo "BLOQUÉ: dépôt source absent"; exit 2; }
git -C "$BASE" fetch --no-tags origin feature/d1-global-audit-20261020
REV="$(git -C "$BASE" rev-parse FETCH_HEAD)"
if [[ -e "$OUT" ]]; then
 [[ "$(git -C "$OUT" rev-parse HEAD 2>/dev/null || true)" == "$REV" ]] || { echo "BLOQUÉ: dossier déjà présent, aucune suppression"; exit 2; }
else
 git -C "$BASE" worktree add --detach "$OUT" "$REV"
fi
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:testDebugUnitTest --tests "com.sentinel.quantum.security.ApkArchiveValidatorTest" --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$OUT"
python - "$OUT/native-android-app/app/build/test-results/testDebugUnitTest" <<'PY'
import sys,pathlib,xml.etree.ElementTree as ET
files=list(pathlib.Path(sys.argv[1]).rglob("TEST-*ApkArchiveValidatorTest.xml"))
if len(files)!=1: raise SystemExit("BLOQUÉ: preuve XML manquante ou ambiguë")
r=ET.parse(files[0]).getroot()
nums=[int(r.attrib.get(k,0)) for k in ("tests","skipped","failures","errors")]
print("TESTS=%d SKIPPED=%d FAILURES=%d ERRORS=%d"%tuple(nums))
if nums!=[8,0,0,0]: raise SystemExit("VERDICT=PREUVE_NON_CONFORME")
print("VERDICT=ZIP_HARDENING_8_TESTS_REUSSIS")
PY
