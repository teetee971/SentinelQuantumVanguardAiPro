from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
from verify_payload import fingerprint, CURRENT

ROOT = Path(__file__).resolve().parent
INPUT = 'app-debug-androidTest-isolated.apk'
OUTPUT = 'app-local-isolated-androidTest.apk'
REPORT = 'TEST_SIGNING_REPORT.json'
EXPECTED_INPUT = '9defb9f68a40b2ce2554159e844ed32b3aab42a1aef0c1a84a9094b01924f748'

def require(condition, message):
    if not condition:
        raise RuntimeError(message)

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def invoke(args):
    completed = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(completed.returncode == 0, Path(args[0]).name + ' failed: ' + (completed.stderr or completed.stdout)[:300])
    return completed.stdout + completed.stderr

def verify(jar, apk, expected_signer, label):
    text = invoke(['java', '-jar', str(jar), 'verify', '--verbose', '--print-certs', str(apk)])
    require('Verified using v2 scheme (APK Signature Scheme v2): true' in text, label + ' v2 signature not verified')
    require('Number of signers: 1' in text, label + ' must have exactly one signer')
    signer = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})', text)
    require(signer is not None and signer[1] == expected_signer, label + ' signer mismatch')
    return text

def payload(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'duplicate APK ZIP entries')
        return {name: hashlib.sha256(archive.read(name)).hexdigest() for name in names}

def native_offsets(path):
    result = {}
    with zipfile.ZipFile(path) as archive, Path(path).open('rb') as source:
        for entry in archive.infolist():
            if entry.filename.endswith('.so') and entry.compress_type == zipfile.ZIP_STORED:
                source.seek(entry.header_offset + 26)
                name_size, extra_size = struct.unpack('<HH', source.read(4))
                result[entry.filename] = entry.header_offset + 30 + name_size + extra_size
    return result

def main():
    metadata = json.loads((ROOT / 'INPUT.json').read_text())
    require(metadata['package'] == 'com.sentinel.quantum.test' and metadata['target_package'] == 'com.sentinel.quantum', 'only the test package may be prepared')
    require(metadata['source_commit'] == '2add1102b76cd920f71e15887e7d25a088382f1b', 'unexpected test source commit')
    source = ROOT / INPUT
    reference = ROOT / 'installed-test-reference.apk'
    require(metadata['cloud_sha256'] == EXPECTED_INPUT and digest(source) == EXPECTED_INPUT, 'input APK hash mismatch')
    require(digest(reference) == metadata['installed_reference_sha256'], 'installed test reference hash mismatch')
    require(fingerprint(source) == metadata['payload_sha256'] == CURRENT, 'isolated payload mismatch')
    require(fingerprint(reference) == metadata['installed_reference_payload_sha256'], 'installed test reference payload mismatch')
    for name in (OUTPUT, REPORT):
        require(not (ROOT / name).exists() and not (ROOT / name).is_symlink(), 'output already exists: ' + name)
    signer = metadata['installed_signer_sha256']
    require(re.fullmatch(r'[0-9a-f]{64}', signer) is not None, 'invalid expected signer')
    key = Path(os.environ.get('SENTINEL_KEYSTORE', '/root/.android/debug.keystore'))
    jar_value = os.environ.get('APKSIGNER_JAR', '')
    jars = sorted((Path(os.environ.get('ANDROID_HOME', '/root/android-sdk')) / 'build-tools').glob('*/lib/apksigner.jar'))
    jar = Path(jar_value) if jar_value else (jars[0] if jars else None)
    require(key.is_file() and jar is not None and jar.is_file(), 'existing local keystore or SDK apksigner unavailable; do not generate a replacement key')
    require(shutil.which('java') is not None and shutil.which('keytool') is not None, 'Java/keytool unavailable')
    key_before = digest(key)
    verify(jar, reference, signer, 'installed reference')
    with tempfile.TemporaryDirectory(prefix='.sign-tests-', dir=ROOT) as temporary:
        scratch = Path(temporary)
        certificate = scratch / 'certificate.der'
        invoke(['keytool', '-exportcert', '-alias', 'androiddebugkey', '-storepass', 'android', '-keystore', str(key), '-file', str(certificate)])
        require(digest(certificate) == signer, 'local key does not match installed reference certificate')
        signed = scratch / 'signed.apk'
        invoke(['java', '-jar', str(jar), 'sign', '--ks', str(key), '--ks-key-alias', 'androiddebugkey',
                '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--v1-signing-enabled', 'false',
                '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
                '--out', str(signed), str(source)])
        verify(jar, signed, signer, 'prepared test APK')
        require(digest(source) == EXPECTED_INPUT, 'input APK changed during signing')
        require(payload(source) == payload(signed), 'test APK payload changed')
        offsets = native_offsets(source)
        require(offsets == native_offsets(signed), 'native offsets changed')
        require(fingerprint(signed) == CURRENT, 'prepared test payload fingerprint mismatch')
        require(digest(key) == key_before, 'local keystore changed unexpectedly')
        report = {
            'status': 'TEST_APK_SIGNED_NOT_INSTALLED', 'package': metadata['package'], 'target_package': metadata['target_package'],
            'source_commit': metadata['source_commit'], 'cloud_sha256': EXPECTED_INPUT, 'signed_sha256': digest(signed),
            'installed_reference_sha256': metadata['installed_reference_sha256'], 'signer_sha256': signer,
            'payload_sha256': CURRENT, 'payload_identical': True, 'zip_entries': len(payload(signed)),
            'native_offsets_preserved': True, 'native_entry_count': len(offsets), 'signature_v2_verified': True,
            'keystore_unchanged': True, 'main_apk_included': False, 'main_apk_installed': False,
            'device_tests': 'NOT_EXECUTED', 'allowed_test_class': metadata['allowed_test_class']
        }
        with (ROOT / OUTPUT).open('xb') as destination:
            with signed.open('rb') as original:
                shutil.copyfileobj(original, destination)
        with (ROOT / REPORT).open('x') as destination:
            json.dump(report, destination, indent=2); destination.write('\n')
        print(json.dumps(report, indent=2))

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('STOP: ' + str(error), file=sys.stderr)
        sys.exit(2)
