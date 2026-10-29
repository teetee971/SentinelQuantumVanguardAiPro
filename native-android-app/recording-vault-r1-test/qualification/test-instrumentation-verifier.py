#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest
import json
import subprocess
import sys
import tempfile

spec = importlib.util.spec_from_file_location('verifier', Path(__file__).with_name('verify-instrumentation.py'))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


def result(names):
    lines = []
    for name in names:
        for status in (1, 0):
            lines += [f'INSTRUMENTATION_STATUS: class=example.Tests',
                      f'INSTRUMENTATION_STATUS: test={name}',
                      f'INSTRUMENTATION_STATUS_CODE: {status}']
    return '\n'.join(lines) + f'\nOK ({len(names)} tests)\nINSTRUMENTATION_CODE: -1\n'


class VerificationTests(unittest.TestCase):
    def test_count_is_discovered_instead_of_fixed_at_24(self):
        for count in (1, 24, 25, 73):
            self.assertEqual(count, len(verifier.identities(result([f'test{i}' for i in range(count)]))))

    def test_forged_ok_without_execution_is_rejected(self):
        with self.assertRaises(ValueError): verifier.identities('OK (24 tests)')

    def test_skipped_or_failed_test_is_rejected(self):
        for status in (-1, -2, -3, -4):
            with self.assertRaises(ValueError):
                verifier.identities(result(['one']).replace('STATUS_CODE: 0', f'STATUS_CODE: {status}'))

    def test_missing_wrong_duplicate_or_trailing_terminal_is_rejected(self):
        good = result(['one'])
        for text in (good.replace('INSTRUMENTATION_CODE: -1', ''),
                     good.replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0'),
                     good + 'INSTRUMENTATION_CODE: -1\n',
                     good + 'INSTRUMENTATION_STATUS_CODE: 0\n'):
            with self.assertRaises(ValueError): verifier.identities(text)

    def test_expected_identities_accept_matching_execution(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            log = directory / 'execution.txt'
            expected = directory / 'expected.json'
            log.write_text(result(['one', 'two']))
            expected.write_text(json.dumps(['example.Tests#two', 'example.Tests#one']))
            completed = subprocess.run([sys.executable, str(Path(verifier.__file__)), str(log),
                                        '--expected', str(expected)], capture_output=True, text=True)
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertIn('VERIFIED 2 executed tests', completed.stdout)

    def test_equal_counts_with_wrong_identity_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            log = directory / 'execution.txt'
            expected = directory / 'expected.json'
            log.write_text(result(['one']))
            expected.write_text(json.dumps(['example.Tests#missing']))
            completed = subprocess.run([sys.executable, str(Path(verifier.__file__)), str(log),
                                        '--expected', str(expected)], capture_output=True, text=True)
            self.assertNotEqual(0, completed.returncode)
            self.assertNotIn('VERIFIED', completed.stdout)

    def test_duplicate_is_rejected(self):
        with self.assertRaises(ValueError): verifier.identities(result(['one', 'one']))

    def test_incomplete_and_incorrect_footer_are_rejected(self):
        for text in (result(['one']).replace('STATUS_CODE: 0', 'STATUS_CODE: 1'),
                     result(['one']).replace('OK (1 tests)', 'OK (2 tests)')):
            with self.assertRaises(ValueError): verifier.identities(text)


if __name__ == '__main__':
    unittest.main(verbosity=2)
