#!/usr/bin/env python3
"""Host-only negative tests: adb exit 0 must never imply instrumentation success."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).with_name('run-device.sh')
FAKE_ADB = '''#!/usr/bin/env python3
import os, sys, time
args = sys.argv[3:]
if args[0] == 'get-state': print('device')
elif args[0] == 'logcat': time.sleep(30)
elif args[0] == 'install': print('Success (SIMULATED)')
elif args[:2] == ['shell', 'getprop']: print('SIMULATED DEVICE')
elif args[:3] == ['shell', 'am', 'instrument']:
    print(os.environ['FAKE_RESULT'])
    sys.exit(int(os.environ.get('FAKE_EXIT', '0')))
else: sys.exit(2)
'''

class RunnerFailureTests(unittest.TestCase):
    def assert_rejected(self, result, exit_code=0):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            adb = directory / 'adb'
            adb.write_text(FAKE_ADB)
            adb.chmod(0o700)
            artifact = directory / 'simulated.apk'
            artifact.write_bytes(b'HOST SIMULATION ONLY')
            env = dict(os.environ, APP_APK=str(artifact), TEST_APK=str(artifact), ADB=str(adb), ANDROID_SERIAL='simulated',
                       VAULT_RUN='a' * 32, FAKE_RESULT=result, FAKE_EXIT=str(exit_code))
            completed = subprocess.run(['bash', str(RUNNER), str(directory / 'logs')],
                                       env=env, text=True, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, timeout=15)
            self.assertNotEqual(0, completed.returncode, completed.stdout)
            self.assertNotIn('PROTOCOL_COMPLETED', completed.stdout)

    def test_zero_exit_instrumentation_failure(self):
        self.assert_rejected('INSTRUMENTATION_FAILED: runner unavailable')

    def test_skipped_test_with_ok_footer(self):
        self.assert_rejected('INSTRUMENTATION_STATUS_CODE: -3\nOK (21 tests)')

    def test_incomplete_test_count(self):
        self.assert_rejected('OK (1 test)')

    def test_adb_failure_with_ok_footer(self):
        self.assert_rejected('OK (21 tests)', 1)

if __name__ == '__main__':
    unittest.main(verbosity=2)
