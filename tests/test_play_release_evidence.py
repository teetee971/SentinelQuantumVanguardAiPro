import tempfile
import unittest
from pathlib import Path
from scripts.play_release_evidence import inspect

class PlayReleaseEvidenceTest(unittest.TestCase):
    def test_missing_build_config_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            report = inspect(Path(temp), None)
            self.assertFalse(report["release_ready"])
            self.assertEqual("MISSING", report["checks"]["build_config"])

    def test_unsigned_or_missing_bundle_never_claims_ready(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            build = root / "native-android-app/app/build.gradle"
            build.parent.mkdir(parents=True)
            build.write_text('applicationId "com.sentinel.quantum"\nversionCode 10\nversionName "1.0.9"\n')
            report = inspect(root, None)
            self.assertEqual("NOT_PROVIDED", report["checks"]["signed_aab"])
            self.assertFalse(report["release_ready"])

if __name__ == "__main__":
    unittest.main()
