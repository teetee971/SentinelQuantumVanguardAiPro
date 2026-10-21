#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-zip-hardening"
[[ -e "$ROOT/.git" ]] || { echo "BLOQUÉ : copie de qualification introuvable"; exit 2; }
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ : modifications locales ; aucune suppression"; exit 2; }
git -C "$ROOT" fetch --no-tags origin feature/d1-global-audit-20261020
REV="$(git -C "$ROOT" rev-parse FETCH_HEAD)"
git -C "$ROOT" checkout --detach "$REV"
echo "SOURCE=$REV"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:testDebugUnitTest --tests "com.sentinel.quantum.security.ApkArchiveValidatorTest" --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$ROOT"
python - "$ROOT/native-android-app/app/build/test-results/testDebugUnitTest" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
files=list(pathlib.Path(sys.argv[1]).rglob("TEST-*ApkArchiveValidatorTest.xml"))
if len(files)!=1: raise SystemExit("BLOQUÉ: preuve XML manquante")
r=ET.parse(files[0]).getroot()
values=[int(r.attrib.get(k,0)) for k in ("tests","skipped","failures","errors")]
print("TESTS=%d SKIPPED=%d FAILURES=%d ERRORS=%d"%tuple(values))
if values!=[8,0,0,0]: raise SystemExit("VERDICT=NON_VALIDE")
print("VERDICT=ZIP_HARDENING_8_TESTS_REUSSIS")
PY
