#!/data/data/com.termux/files/usr/bin/bash
# Independent evidence gate: ensure JVM APK signature proof was actually executed, not skipped.
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-proof/native-android-app/app/build/test-results/testDebugUnitTest"
[[ -d "$ROOT" ]] || { echo "BLOQUÉ: résultats XML absents"; exit 2; }
python - "$ROOT" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
files=list(root.rglob("TEST-*ApkSignatureVerifierProofTest.xml"))
if len(files)!=1:
    print("BLOQUÉ: fichier de preuve absent ou ambigu",len(files))
    sys.exit(2)
element=ET.parse(files[0]).getroot()
tests=int(element.attrib.get("tests","0"))
failures=int(element.attrib.get("failures","0"))
errors=int(element.attrib.get("errors","0"))
skipped=int(element.attrib.get("skipped","0"))
cases=element.findall(".//testcase")
name="validAndCorruptedApkAreDistinguished"
matching=[x for x in cases if x.attrib.get("name")==name]
print(f"TESTS={tests} FAILURES={failures} ERRORS={errors} SKIPPED={skipped} TARGET_CASES={len(matching)}")
if tests != 1 or failures or errors or skipped or len(matching)!=1:
    print("VERDICT=PREUVE_INSUFFISANTE")
    sys.exit(1)
if matching[0].find("skipped") is not None or matching[0].find("failure") is not None:
    print("VERDICT=PREUVE_INSUFFISANTE")
    sys.exit(1)
print("VERDICT=TEST_APKSIG_REELLEMENT_EXECUTE_SANS_SAUT")
PY
