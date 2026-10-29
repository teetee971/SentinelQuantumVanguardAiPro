#!/usr/bin/env python3
"""Execute the complete Bash protocol against a simulated ADB, never an Android device."""
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).with_name('run-device.sh')
FAKE_ADB = r'''#!/usr/bin/env python3
import json, os, sys, time
from pathlib import Path
root = Path(os.environ['SIMULATION_ROOT'])
args = sys.argv[3:]
with (root / 'calls.jsonl').open('a') as out:
    out.write(json.dumps(args) + '\n')
mode = os.environ.get('SIMULATION_MODE', 'success')
if args == ['get-state']:
    print('device')
elif args[0] == 'install':
    print('Success (HOST SIMULATION ONLY)')
elif args[:2] == ['shell', 'getprop']:
    print('HOST SIMULATION ONLY')
elif args[:3] == ['shell', 'am', 'force-stop']:
    (root / 'active-stage').unlink(missing_ok=True)
elif args[0] == 'shell' and len(args) == 2 and args[1].startswith('pidof '):
    print('')
elif args[0] == 'logcat':
    seen = 0
    while True:
        events = root / 'events.txt'
        lines = events.read_text().splitlines() if events.exists() else []
        for line in lines[seen:]:
            print(line, flush=True)
        seen = len(lines)
        time.sleep(0.01)
elif args[:3] == ['shell', 'am', 'instrument']:
    extras = {}
    for index, value in enumerate(args):
        if value == '-e':
            extras[args[index + 1]] = args[index + 2]
    selected = extras['class']
    phase = extras.get('vaultPhase')
    point = extras.get('vaultPoint')
    if phase in ('stageInterruptedWrite', 'interrupt'):
        (root / 'active-stage').write_text('simulation is suspended')
        run = extras['vaultRun']
        if phase == 'interrupt':
            event = f'MIGRATION_READY run={run} point={point} process=SIMULATED'
        else:
            event = f'INTERRUPT_READY run={run} process=SIMULATED'
        with (root / 'events.txt').open('a') as out:
            out.write(event + '\n')
        while (root / 'active-stage').exists():
            time.sleep(0.01)
        # This is an expected interrupted session, with NO successful JUnit footer.
        sys.exit(1)
    if phase is None:
        # Deliberately different from 24; identities, not a fixed count, are the contract.
        names = [(selected.split(',')[0], f'simulated_{i}') for i in range(27)]
        if extras.get('log') != 'true' and mode == 'wrong_identity':
            names[-1] = (names[-1][0], 'substituted_identity')
    else:
        method = ('migrationAcrossProcessDeath' if 'MigrationPersistence' in selected
                  else 'persistenceAcrossProcessDeath')
        names = [(selected, method)]
    if phase == 'verify' and point == 'required' and mode == 'failed_verification':
        print('INSTRUMENTATION_FAILED: SIMULATED verification failure')
        sys.exit(0)  # ADB exit zero must not hide instrumentation failure.
    for cls, method in names:
        for code in (1, 0):
            print(f'INSTRUMENTATION_STATUS: class={cls}')
            print(f'INSTRUMENTATION_STATUS: test={method}')
            print(f'INSTRUMENTATION_STATUS_CODE: {code}')
    print(f'OK ({len(names)} tests)')
    print('INSTRUMENTATION_CODE: -1')
else:
    print('UNEXPECTED SIMULATED COMMAND', args)
    sys.exit(2)
'''


class CompleteProtocolTests(unittest.TestCase):
    def execute_protocol(self, mode):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            adb = root / 'adb'
            adb.write_text(FAKE_ADB)
            adb.chmod(0o700)
            apk = root / 'simulated.apk'
            apk.write_bytes(b'HOST SIMULATION ONLY, NOT AN APK')
            env = dict(os.environ, SIMULATION_ROOT=temp, SIMULATION_MODE=mode,
                       APP_APK=str(apk), TEST_APK=str(apk), ADB=str(adb),
                       ANDROID_SERIAL='HOST_SIMULATION_ONLY', VAULT_RUN='b' * 32)
            process = subprocess.Popen(['bash', str(RUNNER), str(root / 'evidence')],
                                       env=env, text=True, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            try:
                output, _ = process.communicate(timeout=25)
            finally:
                # Only this test's process group, including simulated logcat clients.
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                process.wait(timeout=5)
            calls = [json.loads(line) for line in (root / 'calls.jsonl').read_text().splitlines()]
            logs = {p.name: p.read_text() for p in (root / 'evidence').glob('*.log')}
            return process.returncode, output, calls, logs

    @staticmethod
    def phases(calls):
        result = []
        for args in calls:
            if args[:3] != ['shell', 'am', 'instrument']:
                continue
            extras = {args[i + 1]: args[i + 2] for i, value in enumerate(args) if value == '-e'}
            if 'vaultPhase' in extras:
                result.append((extras.get('vaultPoint'), extras['vaultPhase']))
        return result

    def test_complete_simulated_protocol_exercises_all_points_without_fixed_count(self):
        code, output, calls, logs = self.execute_protocol('success')
        self.assertEqual(0, code, output)
        self.assertIn('PROTOCOL_COMPLETED', output)
        self.assertIn('VERIFIED 27 executed tests', output)
        expected = [(None, 'write'), (None, 'stageInterruptedWrite'), (None, 'verify'), (None, 'cleanup')]
        for point in ('prepared', 'required', 'published'):
            expected += [(point, phase) for phase in ('setup', 'interrupt', 'verify', 'cleanup')]
            self.assertNotIn('OK (', logs[f'migration-{point}-interrupted.log'])
        self.assertEqual(expected, self.phases(calls))
        self.assertNotIn('OK (', logs['interrupted.log'])
        installs = [args for args in calls if args[0] == 'install']
        self.assertEqual(2, len(installs))
        self.assertTrue(all(args[1:3] == ['-r', '-t'] for args in installs))
        self.assertTrue(all('uninstall' not in args and 'clear' not in args for args in calls))

    def test_equal_regression_count_with_wrong_identity_stops_before_data_phases(self):
        code, output, calls, _ = self.execute_protocol('wrong_identity')
        self.assertNotEqual(0, code, output)
        self.assertNotIn('PROTOCOL_COMPLETED', output)
        self.assertEqual([], self.phases(calls))

    def test_failed_verification_never_runs_cleanup_or_later_points(self):
        code, output, calls, _ = self.execute_protocol('failed_verification')
        self.assertNotEqual(0, code, output)
        self.assertNotIn('PROTOCOL_COMPLETED', output)
        phases = self.phases(calls)
        self.assertEqual(('required', 'verify'), phases[-1])
        self.assertNotIn(('required', 'cleanup'), phases)
        self.assertFalse(any(point == 'published' for point, _ in phases))


if __name__ == '__main__':
    unittest.main(verbosity=2)
