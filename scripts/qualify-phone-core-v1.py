#!/usr/bin/env python3
"""Build and package local Phone Core evidence. Never dispatches CI, signs release or uploads."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'release/phone-core-v1'
LOGS = OUT / 'logs'
LOGS.mkdir(parents=True, exist_ok=True)
os.chdir(ROOT)
records = []

def run(name, command):
    with (LOGS / (name + '.log')).open('w') as f:
        result = subprocess.run([str(c) for c in command], stdout=f, stderr=subprocess.STDOUT)
    records.append({'name': name, 'argv': [str(c) for c in command], 'exit_code': result.returncode,
                    'log': 'logs/' + name + '.log'})
    (LOGS / (name + '.exit')).write_text(str(result.returncode) + '\n')
    (OUT / 'COMMAND_RESULTS.json').write_text(json.dumps(records, indent=2) + '\n')
    print(f'{name}: exit={result.returncode}', flush=True)
    if result.returncode:
        raise SystemExit(f'{name} failed; inspect {LOGS / (name + ".log")}')

jdk = Path(os.environ['JAVA_HOME'])
sdk = Path(os.environ['ANDROID_HOME'])
fixture = Path(os.environ['PHONE_CORE_SIGNED_FIXTURE']).resolve()
assert fixture.is_file(), 'Provide an existing signed APK as cryptographic test fixture'
assert os.environ.get('BUNDLETOOL_JAR'), 'Set BUNDLETOOL_JAR'
gradle = ([os.environ['GRADLE_EXECUTABLE']] if os.environ.get('GRADLE_EXECUTABLE') else
          ['bash', str(ROOT / 'native-android-app/gradlew')])
source_commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
# Require source/config changes to be committed; allow delivery documents and untracked Room output.
from phone_core_source_provenance import dirty_build_inputs
assert not dirty_build_inputs(ROOT), 'Commit all Android modules, build configuration and qualification tools before building'
run('java-version', [jdk / 'bin/java', '-version'])
run('gradle-build', gradle + ['-p', 'native-android-app', ':app:compileDebugKotlin',
    ':app:testDebugUnitTest', ':app:assembleDebug', ':app:assembleDebugAndroidTest',
    ':app:bundleReleaseUnsigned', '-PsentinelSignatureFixture=' + str(fixture),
    '--no-daemon', '--console=plain', '--max-workers=2'])
run('source-manifest', ['node', 'scripts/check-android-manifest.js'])
manifest = ROOT / 'native-android-app/app/build/intermediates/merged_manifests/releaseUnsigned/processReleaseUnsignedManifest/AndroidManifest.xml'
run('release-manifest', [sys.executable, 'scripts/verify-phone-core-v1-release.py', manifest])
run('android-product-truth', ['npm', 'run', 'test:android-product-truth'])
run('release-policy', ['node', '--test', 'scripts/android-release-policy.test.js', 'scripts/android-aab-artifact-policy.test.js'])
# Version fixed by AGP-selected Build Tools in this qualified configuration.
bt = sdk / 'build-tools/36.0.0'
artifacts = [('native-android-app/app/build/outputs/apk/debug/app-debug.apk', 'Sentinel-PhoneCore-V1-debug.apk', ':app:assembleDebug'),
 ('native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', 'Sentinel-PhoneCore-V1-androidTest.apk', ':app:assembleDebugAndroidTest'),
 ('native-android-app/app/build/outputs/bundle/releaseUnsigned/app-releaseUnsigned.aab', 'Sentinel-PhoneCore-V1-release-unsigned.aab', ':app:bundleReleaseUnsigned')]
for source, name, task in artifacts:
    shutil.copy2(ROOT / source, OUT / name)
for label, name, _ in [('apk', artifacts[0][1], ''), ('android-test-apk', artifacts[1][1], '')]:
    run(label + '-signature', [bt / 'apksigner', 'verify', '--verbose', '--print-certs', OUT / name])
    run(label + '-zipalign', [bt / 'zipalign', '-c', '-P', '16', '-v', '4', OUT / name])
run('apk-badging', [bt / 'aapt2', 'dump', 'badging', OUT / artifacts[0][1]])
java = jdk / 'bin/java'
bundletool = Path(os.environ['BUNDLETOOL_JAR'])
bundlearg = '--bundle=' + str(OUT / artifacts[2][1])
run('aab-validate', [java, '-jar', bundletool, 'validate', bundlearg])
run('aab-config', [java, '-jar', bundletool, 'dump', 'config', bundlearg])
run('aab-signature-status', [jdk / 'bin/jarsigner', '-verify', OUT / artifacts[2][1]])
assert 'jar is unsigned' in (LOGS / 'aab-signature-status.log').read_text(), 'Unexpected signed release input'
# JAVA_TOOL_OPTIONS is printed on stderr; parse JSON from the first opening brace.
configtext = (LOGS / 'aab-config.log').read_text()
config = json.loads(configtext[configtext.index('{'):])
assert config['optimizations']['uncompressNativeLibraries']['alignment'] == 'PAGE_ALIGNMENT_16K'
import zipfile
with zipfile.ZipFile(OUT / artifacts[0][1]) as apk, zipfile.ZipFile(OUT / artifacts[2][1]) as aab:
    native = []
    for name in apk.namelist():
        if not (name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) and name.endswith('.so')):
            continue
        b = apk.read(name)
        assert b == aab.read('base/' + name), name
        endian = '<' if b[5] == 1 else '>'
        offset = struct.unpack_from(endian + 'Q', b, 32)[0]
        size, count = struct.unpack_from(endian + 'HH', b, 54)
        aligns = [struct.unpack_from(endian + 'Q', b, offset + i * size + 48)[0]
                  for i in range(count) if struct.unpack_from(endian + 'I', b, offset + i * size)[0] == 1]
        assert aligns and min(aligns) >= 16384, name
        native.append({'name': name, 'segment_alignment': aligns})
    assert native
(LOGS / 'native-16k.json').write_text(json.dumps(native, indent=2) + '\n')
summary = {k: 0 for k in ('tests', 'failures', 'errors', 'skipped')}
xmls = sorted((ROOT / 'native-android-app/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
for xml in xmls:
    suite = ET.parse(xml).getroot()
    for k in summary:
        summary[k] += int(suite.get(k, '0'))
assert summary['tests'] > 0 and summary['failures'] == 0 and summary['errors'] == 0 and summary['skipped'] == 0, summary
(OUT / 'UNIT_TEST_SUMMARY.json').write_text(json.dumps(summary, indent=2) + '\n')
with zipfile.ZipFile(OUT / 'JUNIT_RESULTS.zip', 'w', compression=zipfile.ZIP_DEFLATED) as z:
    for xml in xmls:
        z.write(xml, xml.name)
rows = []
for source, name, task in artifacts:
    p = OUT / name
    rows.append({'file': name, 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest(),
                 'source_commit': source_commit, 'build_task': task, 'path': str(p),
                 'source_artifact_path': str(ROOT / source), 'download_url': None})
(OUT / 'ARTIFACTS.json').write_text(json.dumps({'generated_at_utc': datetime.now(timezone.utc).isoformat(),
    'source_commit': source_commit, 'configuration': {'applicationId': 'com.sentinel.quantum',
    'versionCode': 10, 'versionName': '1.0.9', 'minSdk': 24, 'targetSdk': 36, 'compileSdk': 37,
    'release_signing': 'unsigned', 'debug_signing': 'Android Debug'}, 'artifacts': rows}, indent=2) + '\n')
(OUT / 'SHA256SUMS.txt').write_text(''.join(r['sha256'] + '  ' + r['file'] + '\n' for r in rows))
run('adb-devices', [sdk / 'platform-tools/adb', 'devices', '-l'])
print(json.dumps({'unit_tests': summary, 'artifacts': rows}, indent=2))
