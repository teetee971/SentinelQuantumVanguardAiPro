import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('capture', Path(__file__).with_name('capture-phone-core-s24.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RedactionTests(unittest.TestCase):
    def test_phone_numbers_are_not_written(self):
        for value in ('+33 6 12 34 56 78', '0612345678', '+1 (555) 123-4567'):
            with self.subTest(value=value):
                self.assertNotIn(value, module.redact('handle=' + value + ' done'))
                self.assertIn('MASQUÉ', module.redact(value))

    def test_remote_urls_and_addresses_are_removed(self):
        self.assertNotIn('example.invalid', module.redact('https://example.invalid/?number=0612345678 test@example.invalid'))

    def test_role_evidence_remains_readable(self):
        message = 'role=android.app.role.DIALER roleHeld=true status=COMPLETED_HELD'
        self.assertEqual(message, module.redact(message))

    def test_arbitrary_names_are_not_claimed_anonymous(self):
        self.assertEqual('Name Libre', module.redact('Name Libre'))


if __name__ == '__main__':
    unittest.main()
