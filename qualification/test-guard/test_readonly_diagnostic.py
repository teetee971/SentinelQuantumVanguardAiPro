import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import warnings
import zipfile

SCRIPT = Path(__file__).with_name('diagnostic-sentinel-readonly.sh')
CURRENT_APK = Path(os.environ.get('SENTINEL_CURRENT_TEST_APK', '/workspace/qualification-evidence/test-guard-2add1102/androidTest.apk'))
LEGACY_APK = Path(os.environ.get('SENTINEL_LEGACY_TEST_APK', '/workspace/toolchains/v22-kit/app-debug-androidTest-v22.apk'))

class ReadonlyDiagnosticTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        body = SCRIPT.read_text().split("<<'SENTINEL_PAYLOAD_PY'\n", 1)[1].split('\nSENTINEL_PAYLOAD_PY', 1)[0]
        module_path = self.root / 'payload.py'; module_path.write_text(body)
        spec = importlib.util.spec_from_file_location('payload', module_path)
        self.module = importlib.util.module_from_spec(spec); spec.loader.exec_module(self.module)

    def archive(self, name, rows):
        path = self.root / name
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(path, 'w') as z:
                for key, value in rows: z.writestr(key, value)
        return path

    def test_dex_change_changes_payload_identity(self):
        one = self.archive('one.apk', [('classes.dex', b'one')])
        two = self.archive('two.apk', [('classes.dex', b'two')])
        self.assertNotEqual(self.module.fingerprint(one), self.module.fingerprint(two))

    def test_signing_metadata_does_not_change_payload_identity(self):
        one = self.archive('one.apk', [('classes.dex', b'code'), ('AndroidManifest.xml', b'manifest')])
        two = self.archive('two.apk', [('classes.dex', b'code'), ('AndroidManifest.xml', b'manifest'), ('META-INF/TEST.SF', b'signature'), ('META-INF/TEST.RSA', b'public certificate')])
        self.assertEqual(self.module.fingerprint(one), self.module.fingerprint(two))

    def test_ambiguous_or_unsafe_zip_names_are_rejected(self):
        for rows in [[('classes.dex', b'a'), ('classes.dex', b'b')], [('../classes.dex', b'a')], [('/classes.dex', b'a')]]:
            with self.subTest(rows=rows):
                with self.assertRaises(ValueError): self.module.fingerprint(self.archive('bad.apk', rows))

    def test_uncompressed_budget_is_checked(self):
        apk = self.archive('budget.apk', [('classes.dex', b'abcd')])
        with self.assertRaises(ValueError): self.module.fingerprint(apk, max_uncompressed=2)

    def test_real_compiled_isolated_apk_is_recognized_without_claiming_signature(self):
        if not CURRENT_APK.exists(): self.skipTest('compiled isolated APK fixture required')
        result = self.module.identify(CURRENT_APK)
        self.assertEqual('MATCH_ISOLATED_2add1102', result['payload_status'])
        self.assertEqual('NOT_VERIFIED', result['signature'])
        self.assertFalse(result['tests_executed'])

    def simulate_adb(self, apk, missing=False, query_failure=False):
        if not apk.exists(): self.skipTest('known APK fixture required')
        binary = self.root / 'bin'; binary.mkdir()
        adb = binary / 'adb'
        adb.write_text('''#!/usr/bin/env python3
import json,os,shutil,sys
from pathlib import Path
a=sys.argv[1:]
with open(os.environ['FAKE_ADB_LOG'],'a') as f:f.write(json.dumps(a)+'\\n')
if a and a[0]=='connect':print('connected');sys.exit(0)
if a[:2]==['-s','192.168.11.173:38703']:a=a[2:]
if a==['get-state']:print('device')
elif a==['shell','dumpsys','package','com.sentinel.quantum']:print('versionCode=22\\nversionName=1.0.9')
elif a==['shell','pm','list','instrumentation']:print('instrumentation:com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner (target=com.sentinel.quantum)')
elif a==['shell','pm','path','com.sentinel.quantum.test']:
 if os.environ.get('FAKE_QUERY_FAILURE')=='1':sys.exit(1)
 if os.environ.get('FAKE_MISSING')!='1':print('package:/data/app/test/base.apk')
elif len(a)==3 and a[0]=='pull' and a[1]=='/data/app/test/base.apk':shutil.copyfile(os.environ['FAKE_TEST_APK'],a[2])
else:sys.exit(23)
''')
        adb.chmod(0o755)
        env = os.environ | {'HOME':str(self.root), 'PATH':str(binary)+os.pathsep+os.environ['PATH'], 'FAKE_TEST_APK':str(apk), 'FAKE_ADB_LOG':str(self.root/'calls.jsonl'), 'FAKE_MISSING':'1' if missing else '0', 'FAKE_QUERY_FAILURE':'1' if query_failure else '0'}
        result = subprocess.run(['bash',str(SCRIPT),'192.168.11.173:38703'],env=env,text=True,capture_output=True,timeout=20)
        calls=[json.loads(s) for s in (self.root/'calls.jsonl').read_text().splitlines()]
        allowed=[['connect','192.168.11.173:38703'],['-s','192.168.11.173:38703','get-state'],['-s','192.168.11.173:38703','shell','dumpsys','package','com.sentinel.quantum'],['-s','192.168.11.173:38703','shell','pm','list','instrumentation'],['-s','192.168.11.173:38703','shell','pm','path','com.sentinel.quantum.test']]
        for c in calls:
            self.assertTrue(c in allowed or (len(c)==5 and c[:4]==['-s','192.168.11.173:38703','pull','/data/app/test/base.apk']),c)
        return result,calls

    def test_script_current_apk_only_uses_readonly_adb_commands(self):
        result,_=self.simulate_adb(CURRENT_APK)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        self.assertIn('MATCH_ISOLATED_2add1102',result.stdout)
        self.assertIn('Aucun test lance',result.stdout)

    def test_script_legacy_apk_is_not_accepted_as_isolated(self):
        result,_=self.simulate_adb(LEGACY_APK)
        self.assertEqual(4,result.returncode,result.stdout+result.stderr)
        self.assertIn('LEGACY_UNGUARDED_DO_NOT_RUN',result.stdout)

    def test_missing_test_package_is_reported_without_pull(self):
        result,calls=self.simulate_adb(CURRENT_APK,missing=True)
        self.assertEqual(3,result.returncode,result.stdout+result.stderr)
        self.assertIn('NO_INSTALLED_TEST_APK',result.stdout)
        self.assertFalse(any('pull' in c for c in calls))

    def test_query_failure_is_not_reported_as_absent_package(self):
        result,calls=self.simulate_adb(CURRENT_APK,query_failure=True)
        self.assertEqual(2,result.returncode,result.stdout+result.stderr)
        self.assertIn('TEST_PACKAGE_QUERY_FAILED',result.stdout)
        self.assertNotIn('NO_INSTALLED_TEST_APK',result.stdout)
        self.assertFalse(any('pull' in c for c in calls))

if __name__=='__main__':unittest.main()
