import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('audit-sentinel-keystore.py')


class AuditReportSafetyTest(unittest.TestCase):
    def run_audit(self, store, report):
        # Nonexistent tool exercises the real report writer on a safe failure path.
        return subprocess.run([sys.executable, str(SCRIPT), '--keystore', str(store),
                               '--keytool', '/nonexistent/sentinel-audit-keytool',
                               '--debug-default-password', '--output', str(report)],
                              capture_output=True, text=True)

    def test_hard_link_cannot_overwrite_the_input_file(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'public-placeholder.txt'
            source.write_bytes(b'public fixture, not signing material')
            report = Path(directory) / 'audit.json'
            os.link(source, report)
            result = self.run_audit(source, report)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(b'public fixture, not signing material', source.read_bytes())

    def test_existing_report_is_not_overwritten_on_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'audit.json'
            report.write_bytes(b'previous evidence')
            result = self.run_audit(Path(directory) / 'missing-store', report)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(b'previous evidence', report.read_bytes())

    def test_fresh_report_records_unavailability_without_raw_error(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'audit.json'
            result = self.run_audit(Path(directory) / 'missing-store', report)
            self.assertEqual(2, result.returncode)
            document = json.loads(report.read_text())
            self.assertEqual('UNVERIFIED', document['status'])
            self.assertFalse(document['private_key_copied'])
            self.assertNotIn(directory, report.read_text())


if __name__ == '__main__':
    unittest.main()
