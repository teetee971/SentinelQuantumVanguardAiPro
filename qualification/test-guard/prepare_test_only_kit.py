"""Build a reproducible test-only delivery ZIP; no signing/device command is invoked."""
from pathlib import Path
import hashlib
import shutil
import sys
import tempfile
import zipfile

root=Path(__file__).resolve().parent
apk=Path(sys.argv[1]);output=Path(sys.argv[2])
assert hashlib.sha256(apk.read_bytes()).hexdigest()=='9defb9f68a40b2ce2554159e844ed32b3aab42a1aef0c1a84a9094b01924f748'
assert not output.exists(), 'Never overwrite a delivery ZIP'
verifier=(root/'diagnostic-sentinel-readonly.sh').read_text().split("<<'SENTINEL_PAYLOAD_PY'\n",1)[1].split('\nSENTINEL_PAYLOAD_PY',1)[0]
with tempfile.TemporaryDirectory() as folder:
    staging=Path(folder)
    for name in ['README.txt','INPUT.json','sign-tests-only.sh','sign-tests-only.py','verify-main-v22.sh']:
        shutil.copyfile(root/'test-only-delivery'/name,staging/name)
    shutil.copyfile(apk,staging/'app-debug-androidTest-isolated.apk')
    (staging/'verify_payload.py').write_text(verifier)
    checks=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(staging.iterdir()))
    (staging/'SHA256SUMS').write_text(checks)
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(staging.iterdir()):
            entry=zipfile.ZipInfo(path.name,date_time=(2026,11,7,0,0,0))
            entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16
            archive.writestr(entry,path.read_bytes())
print(output,output.stat().st_size,hashlib.sha256(output.read_bytes()).hexdigest())
