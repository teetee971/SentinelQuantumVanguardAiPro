#!/usr/bin/env python3
"""Fail-closed validator for one P0.2 instrumentation repetition."""

from __future__ import annotations

import argparse
import pathlib
import sys
import xml.etree.ElementTree as ET

EXPECTED_CLASS = "com.sentinel.quantum.security.CallBlocklistStoreConcurrencyInstrumentationTest"
EXPECTED_METHODS = frozenset(
    {
        "two_store_instances_concurrently_add_prefixes_without_lost_rules",
        "concurrent_add_and_remove_preserve_the_complete_committed_state",
        "purge_and_exact_number_addition_do_not_publish_a_torn_snapshot",
        "replacement_and_snapshot_publication_never_leave_an_old_manual_set",
        "simulated_snapshot_time_does_not_publish_an_expired_cache",
        "arcep_activation_and_manual_prefix_addition_are_serialized",
        "signed_installation_is_serialized_and_replay_is_rejected",
        "concurrent_helper_executes_each_supplied_operation_once",
    }
)


def summarize(paths: list[pathlib.Path]) -> tuple[int, int, int, int]:
    tests = failures = errors = skipped = 0
    identities: list[tuple[str, str]] = []
    for path in paths:
        root = ET.parse(path).getroot()
        cases = list(root.iter("testcase"))
        tests += len(cases)
        for case in cases:
            identities.append((case.attrib.get("classname", ""), case.attrib.get("name", "")))
            failures += len(list(case.findall("failure")))
            errors += len(list(case.findall("error")))
            skipped += len(list(case.findall("skipped")))
    if len(identities) != len(set(identities)):
        raise ValueError("duplicate JUnit testcase identity")
    expected = {(EXPECTED_CLASS, method) for method in EXPECTED_METHODS}
    if set(identities) != expected:
        raise ValueError(
            f"unexpected JUnit identities: expected={sorted(expected)} actual={sorted(identities)}"
        )
    return tests, failures, errors, skipped


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", nargs="+", type=pathlib.Path)
    parser.add_argument("--expected-tests", type=int, default=8)
    args = parser.parse_args()
    try:
        summary = summarize(args.xml)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"JUNIT_INVALID: {error}")
        return 2
    tests, failures, errors, skipped = summary
    print(
        f"tests={tests} failures={failures} errors={errors} skipped={skipped}"
    )
    if summary != (args.expected_tests, 0, 0, 0):
        print("JUNIT_INVALID: expected exactly eight passing tests")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
