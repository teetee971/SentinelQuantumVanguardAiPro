import tempfile
import unittest
from pathlib import Path

from render_unbound_blocklist import (
    MAX_ALLOW_RULES,
    MAX_BLOCK_RULES,
    build_policy,
    normalize_domain,
)


class RenderUnboundBlocklistTest(unittest.TestCase):
    def test_normalizes_plain_hosts_and_idn(self):
        self.assertEqual("tracker.example", normalize_domain("TRACKER.EXAMPLE."))
        self.assertEqual("ads.example", normalize_domain("0.0.0.0 ads.example"))
        self.assertEqual("xn--bcher-kva.example", normalize_domain("bücher.example"))
        self.assertIsNone(normalize_domain("https://tracker.example/path"))
        self.assertIsNone(normalize_domain("localhost"))

    def test_allowlist_removes_exact_block_and_creates_transparent_exception(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            block = root / "block.txt"
            allow = root / "allow.txt"
            block.write_text(
                "example.com\ntracker.example\n0.0.0.0 ads.example\n",
                encoding="utf-8",
            )
            allow.write_text(
                "example.com\nneeded.tracker.example\n",
                encoding="utf-8",
            )

            policy, blocked_count, allowed_count = build_policy(block, allow)

            self.assertEqual(2, blocked_count)
            self.assertEqual(2, allowed_count)
            self.assertNotIn(
                'local-zone: "example.com." always_nxdomain',
                policy,
            )
            self.assertIn(
                'local-zone: "tracker.example." always_nxdomain',
                policy,
            )
            self.assertIn(
                'local-zone: "needed.tracker.example." always_transparent',
                policy,
            )

    def test_invalid_lines_are_ignored(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            block = root / "block.txt"
            allow = root / "allow.txt"
            block.write_text(
                "# comment\n"
                "||tracker.example^\n"
                "bad host.example\n"
                "tracker.example\n",
                encoding="utf-8",
            )
            allow.write_text("", encoding="utf-8")

            policy, blocked_count, allowed_count = build_policy(block, allow)

            self.assertEqual(1, blocked_count)
            self.assertEqual(0, allowed_count)
            self.assertIn('tracker.example.', policy)
            self.assertNotIn("bad host.example", policy)

    def test_declared_limits_are_positive_and_allowlist_is_smaller(self):
        self.assertGreater(MAX_BLOCK_RULES, 0)
        self.assertGreater(MAX_ALLOW_RULES, 0)
        self.assertLess(MAX_ALLOW_RULES, MAX_BLOCK_RULES)


if __name__ == "__main__":
    unittest.main()
