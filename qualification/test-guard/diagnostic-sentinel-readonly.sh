#!/usr/bin/env bash
# Read-only device diagnostic: no install, instrumentation, role or application-data action.
set -u -o pipefail
SENTINEL_DEVICE=${1:-192.168.11.173:38703}
[[ "$SENTINEL_DEVICE" =~ ^[A-Za-z0-9._:-]+$ ]] || { echo 'Adresse ADB invalide'; exit 2; }
command -v adb >/dev/null || { echo 'adb absent de Termux'; exit 2; }
SENTINEL_DIAG_DIR=$(mktemp -d "$HOME/sentinel-diagnostic-XXXXXX") || exit 2
cat > "$SENTINEL_DIAG_DIR/verify_payload.py" <<'SENTINEL_PAYLOAD_PY'
import hashlib,json,re,sys,zipfile
from pathlib import Path,PurePosixPath
CURRENT='7240ba552a7c96aa34a167079adc48ce25742187ab11efd704df02ad6219e21a'
LEGACY='5368c3e3b8ba67e31ad56804dea532132f4b437cd46ebdeece552c8d9c2f9420'
def fingerprint(path,max_uncompressed=64*1024*1024):
    path=Path(path)
    if max_uncompressed<=0 or path.stat().st_size>16*1024*1024:
        raise ValueError('APK size or budget unsupported')
    rows=[];seen=set()
    with zipfile.ZipFile(path) as archive:
        entries=archive.infolist()
        if len(entries)>4096 or sum(i.file_size for i in entries)>max_uncompressed:
            raise ValueError('APK payload exceeds diagnostic bounds')
        for entry in entries:
            name=entry.filename
            if (not name or name in seen or name.startswith('/') or '\\' in name or '\x00' in name or
                '..' in name.split('/') or '.' in name.split('/') or ':' in name.split('/')[0] or
                ((entry.external_attr>>16)&0o170000)==0o120000):
                raise ValueError('Ambiguous or unsafe ZIP entry')
            seen.add(name)
            if entry.file_size>32*1024*1024:
                raise ValueError('APK entry exceeds diagnostic bound')
            if entry.is_dir() or re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',name,re.I):
                continue
            payload=archive.read(entry)
            rows.append([name,len(payload),hashlib.sha256(payload).hexdigest()])
    rows.sort()
    canonical=json.dumps(rows,ensure_ascii=True,separators=(',',':')).encode('ascii')
    return hashlib.sha256(b'sentinel-test-payload-v1\0'+canonical).hexdigest()
def identify(path):
    payload=fingerprint(path)
    status='MATCH_ISOLATED_2add1102' if payload==CURRENT else ('LEGACY_UNGUARDED_DO_NOT_RUN' if payload==LEGACY else 'DIFFERENT_DO_NOT_RUN')
    return {'payload_status':status,'payload_sha256':payload,'apk_sha256':hashlib.sha256(Path(path).read_bytes()).hexdigest(),'signature':'NOT_VERIFIED','tests_executed':False}
if __name__=='__main__':
    try:
        result=identify(sys.argv[1]);print(json.dumps(result,indent=2))
        sys.exit(0 if result['payload_status']=='MATCH_ISOLATED_2add1102' else 4)
    except Exception as error:
        print(json.dumps({'payload_status':'UNREADABLE_DO_NOT_RUN','error':type(error).__name__,'signature':'NOT_VERIFIED','tests_executed':False}));sys.exit(5)
SENTINEL_PAYLOAD_PY
sentinel_diagnostic() {
    echo 'Diagnostic lecture seule. Aucun test lance, aucune installation.'
    adb connect "$SENTINEL_DEVICE"
    [[ $(adb -s "$SENTINEL_DEVICE" get-state 2>/dev/null) == device ]] || { echo 'DEVICE_UNAVAILABLE'; return 2; }
    adb -s "$SENTINEL_DEVICE" shell dumpsys package com.sentinel.quantum | grep -E 'versionCode=|versionName=' || true
    adb -s "$SENTINEL_DEVICE" shell pm list instrumentation | grep 'com.sentinel.quantum' || true
    local -a sentinel_paths=()
    local sentinel_pm_output
    if ! sentinel_pm_output=$(adb -s "$SENTINEL_DEVICE" shell pm path com.sentinel.quantum.test); then
        echo 'TEST_PACKAGE_QUERY_FAILED'; return 2
    fi
    mapfile -t sentinel_paths < <(printf '%s\n' "$sentinel_pm_output" | tr -d '\r' | sed -n 's/^package://p')
    [[ ${#sentinel_paths[@]} != 0 ]] || { echo 'NO_INSTALLED_TEST_APK'; return 3; }
    [[ ${#sentinel_paths[@]} == 1 && ${sentinel_paths[0]} == /data/app/*/base.apk ]] || { echo 'UNSUPPORTED_TEST_APK_LAYOUT'; return 3; }
    adb -s "$SENTINEL_DEVICE" pull "${sentinel_paths[0]}" "$SENTINEL_DIAG_DIR/installed-tests.apk" || return 2
    if command -v python3 >/dev/null; then
        python3 "$SENTINEL_DIAG_DIR/verify_payload.py" "$SENTINEL_DIAG_DIR/installed-tests.apk"
    elif command -v python >/dev/null; then
        python "$SENTINEL_DIAG_DIR/verify_payload.py" "$SENTINEL_DIAG_DIR/installed-tests.apk"
    elif command -v proot-distro >/dev/null; then
        proot-distro login --bind "$SENTINEL_DIAG_DIR:/work/sentinel-readonly" ubuntu -- python3 /work/sentinel-readonly/verify_payload.py /work/sentinel-readonly/installed-tests.apk
    else
        echo 'PYTHON_UNAVAILABLE: APK copie, contenu non compare.'; return 3
    fi
}
sentinel_diagnostic 2>&1 | tee "$SENTINEL_DIAG_DIR/RESULTATS.txt"
SENTINEL_RESULT=${PIPESTATUS[0]}
echo "Rapport : $SENTINEL_DIAG_DIR/RESULTATS.txt"
echo 'La signature et la compatibilite Android ne sont pas validees par cette empreinte.'
exit "$SENTINEL_RESULT"
