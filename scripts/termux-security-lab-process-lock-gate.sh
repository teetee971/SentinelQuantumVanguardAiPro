#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-cancellation"
[[ -e "$ROOT/.git" ]] || { echo "BLOQUÉ: copie de qualification absente"; exit 2; }
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ: sources modifiées, arrêt sans suppression"; exit 2; }
git -C "$ROOT" fetch --no-tags origin feature/d1-global-audit-20261020
git -C "$ROOT" checkout --detach "$(git -C "$ROOT" rev-parse FETCH_HEAD)"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:testDebugUnitTest --tests "com.sentinel.quantum.security.ApkInspectionProcessGuardTest" --tests "com.sentinel.quantum.security.TemporaryApkScopeTest" --tests "com.sentinel.quantum.security.TemporaryApkInventoryTest" --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$ROOT"
python - "$ROOT/native-android-app/app/build/test-results/testDebugUnitTest" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
for name,expected in [('ApkInspectionProcessGuardTest',2),('TemporaryApkScopeTest',3),('TemporaryApkInventoryTest',4)]:
 f=list(root.rglob(f'TEST-*{name}.xml'))
 if len(f)!=1: raise SystemExit(f'BLOQUE: rapport {name} manquant')
 r=ET.parse(f[0]).getroot()
 v=[int(r.attrib.get(k,0)) for k in ('tests','skipped','failures','errors')]
 print(name, 'TESTS=%d SKIPPED=%d FAILURES=%d ERRORS=%d'%tuple(v))
 if v != [expected,0,0,0]: raise SystemExit('VERDICT=PREUVE_INCOMPLETE')
print('VERDICT=VERROU_INTERPROCESSUS_ET_NETTOYAGE_9_TESTS_REUSSIS')
PY
