#!/usr/bin/env python3
"""Fail closed unless the targeted bounded-executor test was actually reported by JUnit."""
from pathlib import Path
import argparse
import sys
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description="Check the bounded executor JUnit case")
parser.add_argument("--require-fresh", action="store_true", help="Reject XML older than the sources it validates")
args = parser.parse_args()

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
if args.require_fresh:
    sources = [
        root / "native-android-app/app/src/main/java/com/sentinel/quantum/security/CallFilterLogStore.kt",
        root / "native-android-app/app/src/test/java/com/sentinel/quantum/security/BoundedPostResponseExecutorTest.kt",
    ]
    missing = [str(p.relative_to(root)) for p in sources if not p.is_file()]
    if missing:
        print(f"SATURATION_TEST_GATE=BLOCKED_MISSING_SOURCE:{','.join(missing)}")
        sys.exit(2)
    if file.stat().st_mtime_ns < max(p.stat().st_mtime_ns for p in sources):
        print("SATURATION_TEST_GATE=BLOCKED_STALE_JUNIT_XML")
        print("NOTE=Rerun :app:testDebugUnitTest to regenerate evidence after source changes")
        sys.exit(2)
fail_tags = ("failure", "error", "skipped")
if any(case.find(tag) is not None for tag in fail_tags):
    print("SATURATION_TEST_GATE=FAIL_OR_SKIPPED")
    sys.exit(1)
print(f"JUNIT_XML={file.relative_to(root)}")
print(f"TEST_CLASS={target_class}")
print(f"TEST_METHOD={target_method}")
print("SATURATION_TEST_GATE=PASS_JUNIT_EXECUTED")
print(f"FRESHNESS_CHECK={'PASS_MTIME' if args.require_fresh else 'NOT_REQUESTED'}")
print("NOTE=JVM capacity gate only; no Android instrumentation or persistence guarantee")
