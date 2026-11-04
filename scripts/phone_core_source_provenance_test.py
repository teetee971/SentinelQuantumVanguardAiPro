import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest


class ProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.git('init', '-q', '-b', 'qualification-fixture')
        self.git('config', 'user.name', 'Test fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.files = ['native-android-app/app/build.gradle', 'native-android-app/settings.gradle',
                      'native-android-app/gradle.properties', 'native-android-app/app/proguard-rules.pro',
                      'native-android-app/wearable-security/src/main/kotlin/Example.kt']
        for file in self.files:
            self.write(file, 'baseline\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], stderr=subprocess.STDOUT)

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def test_clean_tree_is_accepted(self):
        self.assertEqual([], dirty_build_inputs(self.root))

    def test_each_dependency_and_configuration_change_is_rejected(self):
        for file in self.files:
            with self.subTest(file=file):
                self.write(file, 'changed\n')
                self.assertTrue(dirty_build_inputs(self.root))
                self.write(file, 'baseline\n')

    def test_untracked_source_is_rejected(self):
        self.write('native-android-app/wearable-security/src/main/kotlin/New.kt', 'new')
        self.assertTrue(dirty_build_inputs(self.root))

    def test_generated_untracked_room_schema_is_preserved_and_allowed(self):
        self.write('native-android-app/app/schemas/example/1.json', '{}')
        self.assertEqual([], dirty_build_inputs(self.root))
        self.assertTrue((self.root / 'native-android-app/app/schemas/example/1.json').exists())

    def test_changed_tracked_schema_is_not_hidden(self):
        file = 'native-android-app/app/schemas/example/1.json'
        self.write(file, '{}')
        self.git('add', file)
        self.git('commit', '-qm', 'tracked fixture')
        self.write(file, '{"changed":true}')
        self.assertTrue(dirty_build_inputs(self.root))

    def test_uncommitted_qualification_procedure_is_rejected(self):
        self.write('scripts/qualify-phone-core-v1.py', '# modified')
        self.assertTrue(dirty_build_inputs(self.root))


if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location('provenance', Path(__file__).with_name('phone_core_source_provenance.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    dirty_build_inputs = module.dirty_build_inputs
    unittest.main()
