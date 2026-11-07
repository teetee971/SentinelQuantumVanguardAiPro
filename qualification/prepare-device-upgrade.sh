#!/usr/bin/env bash
# Run inside Ubuntu/proot. Signs files only; never invokes adb or installs an APK.
set -euo pipefail
kit_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
metadata="$kit_dir/UPGRADE_INPUT.json"
keystore=${SENTINEL_KEYSTORE:-/root/.android/debug.keystore}
: "${APKSIGNER_JAR:=}"
if [[ -z "$APKSIGNER_JAR" ]]; then
  for candidate in "${ANDROID_HOME:-/root/android-sdk}"/build-tools/*/lib/apksigner.jar; do
    if [[ -f "$candidate" ]]; then APKSIGNER_JAR=$candidate; break; fi
  done
fi
[[ -f "$APKSIGNER_JAR" && -f "$keystore" ]] || { echo 'STOP: SDK apksigner or local debug keystore missing'; exit 2; }
command -v java >/dev/null
command -v keytool >/dev/null
command -v python3 >/dev/null
python3 - "$kit_dir" "$metadata" <<'PY'
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1]);r=json.loads(Path(sys.argv[2]).read_text())
assert r['candidate_versionCode']==22 and r['candidate_versionCode']>r['installed_versionCode'], 'STOP: downgrade or unexpected version'
assert set(r['artifact_sha256'])=={'app-debug-v22.apk','app-debug-androidTest-v22.apk'}
for name,digest in r['artifact_sha256'].items():
 assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest, 'STOP: input APK digest mismatch: '+name
for name in ('app-s24-v22.apk','app-s24-androidTest-v22.apk','DEVICE_SIGNING_REPORT.json'):
 assert not (root/name).exists(), 'STOP: output already exists: '+name
PY
scratch=$(mktemp -d "$kit_dir/.local-signing.XXXXXX")
trap 'rm -rf -- "$scratch"' EXIT
keytool -exportcert -alias androiddebugkey -storepass android -keystore "$keystore" -file "$scratch/certificate.der" > "$scratch/keytool.log" 2>&1
python3 - "$metadata" "$scratch/certificate.der" <<'PY'
from pathlib import Path
import hashlib,json,sys
r=json.loads(Path(sys.argv[1]).read_text())
assert hashlib.sha256(Path(sys.argv[2]).read_bytes()).hexdigest()==r['installed_signer_sha256'], 'STOP: local key does not match installed certificate'
PY
for input in app-debug-v22.apk app-debug-androidTest-v22.apk; do
  java -jar "$APKSIGNER_JAR" sign --ks "$keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled false \
    --out "$scratch/$input" "$kit_dir/$input"
  java -jar "$APKSIGNER_JAR" verify --verbose --print-certs "$scratch/$input" > "$scratch/$input.signature.txt"
done
python3 - "$kit_dir" "$scratch" "$metadata" <<'PY'
from pathlib import Path
import hashlib,json,re,sys,zipfile,struct
root=Path(sys.argv[1]);scratch=Path(sys.argv[2]);r=json.loads(Path(sys.argv[3]).read_text());rows=[]
def entries(path):
 with zipfile.ZipFile(path) as z:
  names=z.namelist();assert len(names)==len(set(names)), 'STOP: duplicate ZIP entries'
  return {n:hashlib.sha256(z.read(n)).hexdigest() for n in names}
def native_offsets(path):
 with zipfile.ZipFile(path) as z, path.open('rb') as f:
  offsets={}
  for i in z.infolist():
   if i.filename.endswith('.so') and i.compress_type==zipfile.ZIP_STORED:
    f.seek(i.header_offset+26);name_len,extra_len=struct.unpack('<HH',f.read(4))
    offsets[i.filename]=i.header_offset+30+name_len+extra_len
  return offsets
for source,output in [('app-debug-v22.apk','app-s24-v22.apk'),('app-debug-androidTest-v22.apk','app-s24-androidTest-v22.apk')]:
 original=root/source;signed=scratch/source
 assert hashlib.sha256(original.read_bytes()).hexdigest()==r['artifact_sha256'][source], 'STOP: input changed during signing'
 report=(scratch/(source+'.signature.txt')).read_text()
 assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in report, 'STOP: v2 verification failed'
 assert 'Number of signers: 1' in report
 signer=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',report)
 assert signer and signer[1]==r['installed_signer_sha256'], 'STOP: signer mismatch'
 before=entries(original);assert before==entries(signed), 'STOP: APK payload changed'
 assert native_offsets(original)==native_offsets(signed), 'STOP: native library alignment changed'
 rows.append({'file':output,'cloud_sha256':r['artifact_sha256'][source],'signed_sha256':hashlib.sha256(signed.read_bytes()).hexdigest(),'zip_entries':len(before),'payload_identical':True,'native_offsets_preserved':True,'signature_v2_verified':True})
result={'status':'SIGNED_NOT_INSTALLED','source_commit':r['source_commit'],'package':'com.sentinel.quantum','candidate_versionCode':21,'installed_versionCode':r['installed_versionCode'],'signer_sha256':r['installed_signer_sha256'],'artifacts':rows,'physical_s24_plus':'NOT_TESTED'}
(scratch/'DEVICE_SIGNING_REPORT.json').write_text(json.dumps(result,indent=2)+'\n')
PY
mv -- "$scratch/app-debug-v22.apk" "$kit_dir/app-s24-v22.apk"
mv -- "$scratch/app-debug-androidTest-v22.apk" "$kit_dir/app-s24-androidTest-v22.apk"
cp -- "$scratch/app-debug-v22.apk.signature.txt" "$kit_dir/app-s24-v22.signature.txt"
cp -- "$scratch/app-debug-androidTest-v22.apk.signature.txt" "$kit_dir/app-s24-androidTest-v22.signature.txt"
mv -- "$scratch/DEVICE_SIGNING_REPORT.json" "$kit_dir/DEVICE_SIGNING_REPORT.json"
cat "$kit_dir/DEVICE_SIGNING_REPORT.json"
