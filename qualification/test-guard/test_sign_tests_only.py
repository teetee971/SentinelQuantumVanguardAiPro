import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SOURCE=Path(__file__).with_name('test-only-delivery')
CURRENT=Path('/workspace/qualification-evidence/test-guard-2add1102/androidTest.apk')
REFERENCE=Path('/workspace/toolchains/v22-kit/app-debug-androidTest-v22.apk')
KEY=Path('/home/agent/.android/debug.keystore')
JAR=Path('/workspace/toolchains/android-sdk/build-tools/36.0.0/lib/apksigner.jar')
CLOUD_CERT='fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6'

class TestOnlySigningTest(unittest.TestCase):
    def setUp(self):
        if not all(p.is_file() for p in [CURRENT,REFERENCE,KEY,JAR]):self.skipTest('existing host signing fixtures required')
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        for name in ['sign-tests-only.sh','sign-tests-only.py']:shutil.copyfile(SOURCE/name,self.root/name)
        verifier=Path(__file__).with_name('diagnostic-sentinel-readonly.sh').read_text().split("<<'SENTINEL_PAYLOAD_PY'\n",1)[1].split('\nSENTINEL_PAYLOAD_PY',1)[0]
        (self.root/'verify_payload.py').write_text(verifier)
        shutil.copyfile(CURRENT,self.root/'app-debug-androidTest-isolated.apk')
        shutil.copyfile(REFERENCE,self.root/'installed-test-reference.apk')
        self.metadata=json.loads((SOURCE/'INPUT.json').read_text())
        # Real cryptographic signing self-test, using only the EXISTING host debug key.
        # This is not the S24+ key and is never shipped as device signing evidence.
        self.metadata['installed_signer_sha256']=CLOUD_CERT
        self.metadata['installed_reference_sha256']=hashlib.sha256(REFERENCE.read_bytes()).hexdigest()
        self.key_before=hashlib.sha256(KEY.read_bytes()).hexdigest()
        self.main_sentinel=self.root/'main-do-not-touch.apk';self.main_sentinel.write_bytes(b'main sentinel')

    def run_signer(self):
        (self.root/'INPUT.json').write_text(json.dumps(self.metadata))
        env=os.environ | {'SENTINEL_KEYSTORE':str(KEY),'APKSIGNER_JAR':str(JAR)}
        result=subprocess.run(['bash',str(self.root/'sign-tests-only.sh')],env=env,capture_output=True,text=True,timeout=45)
        self.assertEqual(self.key_before,hashlib.sha256(KEY.read_bytes()).hexdigest())
        self.assertEqual(b'main sentinel',self.main_sentinel.read_bytes())
        return result

    def test_real_signing_preserves_payload_and_never_installs(self):
        result=self.run_signer();self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        report=json.loads((self.root/'TEST_SIGNING_REPORT.json').read_text())
        self.assertEqual('TEST_APK_SIGNED_NOT_INSTALLED',report['status'])
        self.assertTrue(report['payload_identical']);self.assertTrue(report['signature_v2_verified'])
        self.assertTrue(report['keystore_unchanged']);self.assertEqual('NOT_EXECUTED',report['device_tests'])
        self.assertEqual(self.metadata['payload_sha256'],report['payload_sha256'])
        self.assertTrue((self.root/'app-local-isolated-androidTest.apk').is_file())

    def test_modified_input_is_refused_before_signing(self):
        with (self.root/'app-debug-androidTest-isolated.apk').open('ab') as f:f.write(b'mutation')
        result=self.run_signer();self.assertNotEqual(0,result.returncode)
        self.assertIn('input APK hash mismatch',result.stdout+result.stderr)
        self.assertFalse((self.root/'app-local-isolated-androidTest.apk').exists())

    def test_main_package_metadata_is_refused(self):
        self.metadata['package']='com.sentinel.quantum'
        result=self.run_signer();self.assertNotEqual(0,result.returncode)
        self.assertIn('only the test package',result.stdout+result.stderr)
        self.assertFalse((self.root/'app-local-isolated-androidTest.apk').exists())

    def test_existing_outputs_are_never_overwritten(self):
        output=self.root/'app-local-isolated-androidTest.apk';output.write_bytes(b'keep')
        result=self.run_signer();self.assertNotEqual(0,result.returncode)
        self.assertIn('output already exists',result.stdout+result.stderr)
        self.assertEqual(b'keep',output.read_bytes())

    def test_reference_certificate_mismatch_is_refused(self):
        self.metadata['installed_signer_sha256']='0'*64
        result=self.run_signer();self.assertNotEqual(0,result.returncode)
        self.assertIn('installed reference signer mismatch',result.stdout+result.stderr)
        self.assertFalse((self.root/'app-local-isolated-androidTest.apk').exists())

class MainReferenceCheckTest(unittest.TestCase):
    def simulate(self, wrong=False, query_failure=False):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);adb=root/'adb';log=root/'calls.jsonl'
            adb.write_text("""#!/usr/bin/env python3
import os,sys,json
a=sys.argv[1:]
with open(os.environ['CHECK_LOG'],'a') as f:f.write(json.dumps(a)+'\\n')
if a[:2]!=['-s','192.168.1.16:42175']:sys.exit(23)
a=a[2:]
if a==['get-state']:print('device')
elif a==['shell','pm','path','com.sentinel.quantum']:
 if os.environ['CHECK_QUERY_FAILURE']=='1':sys.exit(1)
 print('package:/data/app/main/base.apk')
elif a==['shell','sha256sum','/data/app/main/base.apk']:
 print(('0'*64 if os.environ['CHECK_WRONG']=='1' else '10e70d21409daabcee0ba898703a1cb60897d127d22afffc84b6ca79f94390c8')+'  /data/app/main/base.apk')
else:sys.exit(23)
""")
            adb.chmod(0o755)
            result=subprocess.run(['bash',str(SOURCE/'verify-main-v22.sh'),'192.168.1.16:42175'],env=os.environ|{'PATH':str(root)+os.pathsep+os.environ['PATH'],'CHECK_LOG':str(log),'CHECK_WRONG':'1' if wrong else '0','CHECK_QUERY_FAILURE':'1' if query_failure else '0'},text=True,capture_output=True,timeout=10)
            calls=[json.loads(x) for x in log.read_text().splitlines()] if log.exists() else []
            allowed=[['-s','192.168.1.16:42175','get-state'],['-s','192.168.1.16:42175','shell','pm','path','com.sentinel.quantum'],['-s','192.168.1.16:42175','shell','sha256sum','/data/app/main/base.apk']]
            self.assertTrue(all(x in allowed for x in calls))
            return result

    def test_known_main_apk_identity_is_verified_without_writes(self):
        result=self.simulate();self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        self.assertIn('REFERENCE_MAIN_V22_MATCH',result.stdout)
    def test_different_main_apk_is_refused(self):
        result=self.simulate(wrong=True);self.assertNotEqual(0,result.returncode)
        self.assertIn('MAIN_APK_DIFFERENT_DO_NOT_RUN',result.stdout)
    def test_query_failure_is_not_a_positive_identity(self):
        result=self.simulate(query_failure=True);self.assertNotEqual(0,result.returncode)
        self.assertIn('MAIN_QUERY_FAILED',result.stdout)

if __name__=='__main__':unittest.main()
