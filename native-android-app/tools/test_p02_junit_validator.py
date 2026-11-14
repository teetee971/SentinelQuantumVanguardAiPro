#!/usr/bin/env python3
"""Automated harness tests for success, failure, missing and incomplete JUnit data."""

from __future__ import annotations

import pathlib
import subprocess
import sys
import tempfile


ROOT = pathlib.Path(__file__).parent
VALIDATOR = ROOT / "p02_junit_validator.py"
CLASS = "com.sentinel.quantum.security.CallBlocklistStoreConcurrencyInstrumentationTest"
METHODS = [
    "two_store_instances_concurrently_add_prefixes_without_lost_rules",
    "concurrent_add_and_remove_preserve_the_complete_committed_state",
    "purge_and_exact_number_addition_do_not_publish_a_torn_snapshot",
    "replacement_and_snapshot_publication_never_leave_an_old_manual_set",
    "simulated_snapshot_time_does_not_publish_an_expired_cache",
    "arcep_activation_and_manual_prefix_addition_are_serialized",
    "signed_installation_is_serialized_and_replay_is_rejected",
    "concurrent_helper_executes_each_supplied_operation_once",
]


def run(xml: str, expected: int = 8) -> tuple[int, str]:
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
    values = [f'<testcase classname="{CLASS}" name="{method}" />' for method in METHODS]
    if replacement:
        values[-1] = replacement
    return "".join(values)


def assert_case(name: str, xml: str, expected_code: int) -> None:
    code, output = run(xml)
    assert code == expected_code, f"{name}: {code}\n{output}"


assert_case("success", suite(cases()), 0)
assert_case(
    "failure",
    suite(cases(f'<testcase classname="{CLASS}" name="{METHODS[-1]}"><failure /></testcase>')),
    1,
)
assert_case(
    "skipped",
    suite(cases(f'<testcase classname="{CLASS}" name="{METHODS[-1]}"><skipped /></testcase>')),
    1,
)
assert_case(
    "incomplete",
    suite("".join(f'<testcase classname=\"{CLASS}\" name=\"{method}\" />' for method in METHODS[:7])),
    2,
)
assert_case("malformed", "<testsuite>", 2)
print("harness_tests=5 passed=5 failed=0")
