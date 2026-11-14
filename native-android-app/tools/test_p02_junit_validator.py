#!/usr/bin/env python3
"""Automated harness tests for success, failure, missing and incomplete JUnit data."""

from __future__ import annotations

import pathlib
import subprocess
import sys
import tempfile


ROOT = pathlib.Path(__file__).parent
VALIDATOR = ROOT / "p02_junit_validator.py"


def run(xml: str, expected: int = 7) -> tuple[int, str]:
    with tempfile.TemporaryDirectory() as directory:
        path = pathlib.Path(directory) / "results.xml"
        path.write_text(xml, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(VALIDATOR), "--expected-tests", str(expected), str(path)],
            text=True,
            capture_output=True,
            check=False,
        )
        return result.returncode, result.stdout + result.stderr


def suite(body: str) -> str:
    return f'<testsuite name="p02">{body}</testsuite>'


def cases(replacement: str = "") -> str:
    values = [f'<testcase classname="C" name="t{i}" />' for i in range(7)]
    if replacement:
        values[-1] = replacement
    return "".join(values)


def assert_case(name: str, xml: str, expected_code: int) -> None:
    code, output = run(xml)
    assert code == expected_code, f"{name}: {code}\n{output}"


assert_case("success", suite(cases()), 0)
assert_case("failure", suite(cases('<testcase classname="C" name="bad"><failure /></testcase>')), 1)
assert_case("skipped", suite(cases('<testcase classname="C" name="skip"><skipped /></testcase>')), 1)
assert_case(
    "incomplete",
    suite("".join(f'<testcase classname=\"C\" name=\"t{i}\" />' for i in range(6))),
    1,
)
assert_case("malformed", "<testsuite>", 2)
print("harness_tests=5 passed=5 failed=0")
