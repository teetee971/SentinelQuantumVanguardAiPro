import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


LAB_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LAB_ROOT / "scripts"))

from lablib import LabError, load_corpus, verify_artifact, verify_manifest  # noqa: E402


class ManifestTests(unittest.TestCase):
    def test_unverified_manifest_is_rejected_even_when_artifact_exists(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "model.gguf"
            artifact.write_bytes(b"synthetic")
            manifest = {
                "schema_version": 1,
                "model_id": "synthetic-model",
                "status": "UNVERIFIED",
                "artifact": {"filename": artifact.name},
            }
            with self.assertRaisesRegex(LabError, "MODEL_UNVERIFIED"):
                verify_manifest(manifest, artifact)

    def test_verified_manifest_requires_exact_size_and_sha256(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "model.gguf"
            payload = b"synthetic-model-bytes"
            artifact.write_bytes(payload)
            manifest = {
                "schema_version": 1,
                "model_id": "synthetic-model",
                "status": "VERIFIED",
                "revision": "commit-or-release",
                "source": "https://example.invalid/model",
                "license": "Apache-2.0",
                "commercial_use": "REVIEW_REQUIRED",
                "tokenizer": "bundled-and-verified",
                "limits": {"max_context_tokens": 512},
                "artifact": {
                    "filename": artifact.name,
                    "size_bytes": len(payload),
                    "sha256": hashlib.sha256(payload).hexdigest(),
                },
            }
            self.assertEqual(verify_manifest(manifest, artifact), manifest)
            artifact.write_bytes(b"X" * len(payload))
            with self.assertRaisesRegex(LabError, "ARTIFACT_SHA256_MISMATCH"):
                verify_artifact(manifest, artifact)


class CorpusTests(unittest.TestCase):
    def test_corpus_rejects_missing_expected_output(self):
        with tempfile.NamedTemporaryFile("w", suffix=".jsonl", delete=False) as handle:
            handle.write(json.dumps({"id": "missing-expected", "input": "x"}) + "\n")
            path = Path(handle.name)
        try:
            with self.assertRaisesRegex(LabError, "CORPUS_EXPECTED_REQUIRED"):
                load_corpus(path)
        finally:
            path.unlink()

    def test_repository_corpus_contains_only_synthetic_cases(self):
        cases = load_corpus(LAB_ROOT / "corpus" / "sentinel_cases.jsonl")
        self.assertGreaterEqual(len(cases), 10)
        self.assertTrue(all(case["id"] and case["expected"] for case in cases))
        forbidden = ("+33", "real person", "password")
        joined = json.dumps(cases, ensure_ascii=False).lower()
        self.assertFalse(any(value.lower() in joined for value in forbidden))


class BenchmarkGuardTests(unittest.TestCase):
    def test_benchmark_refuses_missing_runtime_before_execution(self):
        command = [
            sys.executable,
            str(LAB_ROOT / "scripts" / "benchmark.py"),
            "--runtime", str(LAB_ROOT / "missing-runtime"),
            "--manifest", str(LAB_ROOT / "manifests" / "qwen2.5-1.5b-instruct-q4.json"),
            "--model", str(LAB_ROOT / "missing-model.gguf"),
            "--prompt", "synthetic prompt",
        ]
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("RUNTIME_NOT_EXECUTABLE", result.stderr)

    def test_benchmark_requires_at_least_five_repetitions(self):
        command = [
            sys.executable,
            str(LAB_ROOT / "scripts" / "benchmark.py"),
            "--runtime", sys.executable,
            "--manifest", str(LAB_ROOT / "manifests" / "qwen2.5-1.5b-instruct-q4.json"),
            "--model", str(LAB_ROOT / "missing-model.gguf"),
            "--prompt", "synthetic prompt",
            "--repeats", "4",
        ]
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("REPEATS_MINIMUM_5", result.stderr)


if __name__ == "__main__":
    unittest.main()
