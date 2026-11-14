#!/usr/bin/env python3
"""Fail-closed validator for one P0.2 instrumentation repetition."""

from __future__ import annotations

import argparse
import pathlib
import sys
import xml.etree.ElementTree as ET


def summarize(paths: list[pathlib.Path]) -> tuple[int, int, int, int]:
    tests = failures = errors = skipped = 0
    for path in paths:
        root = ET.parse(path).getroot()
        cases = list(root.iter("testcase"))
        tests += len(cases)
        for case in cases:
            failures += len(list(case.findall("failure")))
            errors += len(list(case.findall("error")))
            skipped += len(list(case.findall("skipped")))
    return tests, failures, errors, skipped


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", nargs="+", type=pathlib.Path)
    parser.add_argument("--expected-tests", type=int, default=7)
    args = parser.parse_args()
    try:
        summary = summarize(args.xml)
    except (OSError, ET.ParseError) as error:
        print(f"JUNIT_INVALID: {error}")
        return 2
    tests, failures, errors, skipped = summary
    print(
        f"tests={tests} failures={failures} errors={errors} skipped={skipped}"
    )
    if summary != (args.expected_tests, 0, 0, 0):
        print("JUNIT_INVALID: expected exactly seven passing tests")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
