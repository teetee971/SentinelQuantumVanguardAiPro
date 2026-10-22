#!/usr/bin/env python3
"""Fail closed unless the targeted bounded-executor test was actually reported by JUnit."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
reports = root / "native-android-app/app/build/test-results/testDebugUnitTest"
target_class = "com.sentinel.quantum.security.BoundedPostResponseExecutorTest"
target_method = "productionQueueCapacityRejectsAt128PendingTasks"
files = sorted(reports.glob("TEST-*.xml"))
if not files:
    print("SATURATION_TEST_GATE=BLOCKED_NO_JUNIT_XML")
    sys.exit(2)

matches = []
for file in files:
    try:
        xml = ET.parse(file).getroot()
    except (ET.ParseError, OSError) as exc:
        print(f"SATURATION_TEST_GATE=BLOCKED_INVALID_XML:{file.name}:{type(exc).__name__}")
        sys.exit(2)
    for case in xml.iter("testcase"):
        if case.get("classname") == target_class and case.get("name") == target_method:
            matches.append((file, case))
if len(matches) != 1:
    print(f"SATURATION_TEST_GATE=BLOCKED_EXPECTED_ONE_CASE_FOUND_{len(matches)}")
    sys.exit(2)
file, case = matches[0]
fail_tags = ("failure", "error", "skipped")
if any(case.find(tag) is not None for tag in fail_tags):
    print("SATURATION_TEST_GATE=FAIL_OR_SKIPPED")
    sys.exit(1)
print(f"JUNIT_XML={file.relative_to(root)}")
print(f"TEST_CLASS={target_class}")
print(f"TEST_METHOD={target_method}")
print("SATURATION_TEST_GATE=PASS_JUNIT_EXECUTED")
print("NOTE=JVM capacity gate only; no Android instrumentation or persistence guarantee")
