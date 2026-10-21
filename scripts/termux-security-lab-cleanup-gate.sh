#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-cancellation"
[[ -e "$ROOT/.git" ]] || { echo "BLOQUÉ: copie de qualification absente"; exit 2; }
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ: sources locales modifiées"; exit 2; }
git -C "$ROOT" fetch --no-tags origin feature/d1-global-audit-20261020
git -C "$ROOT" checkout --detach "$(git -C "$ROOT" rev-parse FETCH_HEAD)"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:testDebugUnitTest --tests "com.sentinel.quantum.security.TemporaryApkScopeTest" --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$ROOT"
python - "$ROOT/native-android-app/app/build/test-results/testDebugUnitTest" <<'PY'
import sys,pathlib,xml.etree.ElementTree as ET
f=list(pathlib.Path(sys.argv[1]).rglob("TEST-*TemporaryApkScopeTest.xml"))
if len(f)!=1: raise SystemExit("BLOQUÉ: rapport XML de test absent")
r=ET.parse(f[0]).getroot()
v=[int(r.attrib.get(k,0)) for k in ("tests","skipped","failures","errors")]
print("TESTS=%d SKIPPED=%d FAILURES=%d ERRORS=%d"%tuple(v))
if v != [3,0,0,0]: raise SystemExit("VERDICT=NETTOYAGE_TEMPORAIRE_NON_VALIDE")
print("VERDICT=NETTOYAGE_TEMPORAIRE_3_TESTS_REUSSIS")
PY
