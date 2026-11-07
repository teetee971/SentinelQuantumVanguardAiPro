"""Execute the signing helper's real report-generation statement with controlled metadata."""
import ast
import unittest
from pathlib import Path


class SigningReportTest(unittest.TestCase):
    def test_report_uses_verified_input_version_instead_of_a_literal(self):
        script = Path(__file__).with_name('prepare-device-upgrade.sh').read_text()
        # The report is emitted by the final embedded Python block. Run that actual assignment,
        # without signing files, then check its public output against independent fixtures.
        python_block = script.split("<<'PY'\n")[-1].split('\nPY\n')[0]
        statements = ast.parse(python_block).body
        report_assignment = next(
            node for node in statements
            if isinstance(node, ast.Assign)
            and any(isinstance(target, ast.Name) and target.id == 'result' for target in node.targets)
        )
        code = compile(ast.Module(body=[report_assignment], type_ignores=[]), '<signing report>', 'exec')
        for version in (22, 23):
            with self.subTest(version=version):
                metadata = {'source_commit': 'test-source', 'candidate_versionCode': version,
                            'installed_versionCode': 21, 'installed_signer_sha256': 'test-public-certificate'}
                namespace = {'r': metadata, 'rows': []}
                exec(code, namespace)
                self.assertEqual(version, namespace['result']['candidate_versionCode'])
                self.assertEqual(21, namespace['result']['installed_versionCode'])
                self.assertEqual('SIGNED_NOT_INSTALLED', namespace['result']['status'])


if __name__ == '__main__':
    unittest.main()
