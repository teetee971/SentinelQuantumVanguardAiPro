#!/usr/bin/env python3
"""Black-box tests for the non-destructive P0.2 shell harness."""

from __future__ import annotations

import os
import pathlib
import stat
import subprocess
import tempfile


ROOT = pathlib.Path(__file__).resolve().parents[2]
HARNESS = ROOT / "native-android-app/tools/qualify_p02.sh"


def executable(path: pathlib.Path, content: str) -> pathlib.Path:
    path.write_text(content, encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IXUSR)
    return path


def run_case(kind: str, *, expected_sha: str | None = None) -> tuple[int, str]:
    with tempfile.TemporaryDirectory() as directory:
        temp = pathlib.Path(directory)
        adb_output = "" if kind == "no-device" else "SERIAL123\tdevice product=synthetic\n"
        adb = executable(
            temp / "adb",
            f"#!/bin/sh\nif [ \"$1\" = devices ]; then echo 'List of devices attached'; echo '{adb_output}'; fi\n",
        )
        junit_body = "".join(
            f'<testcase classname="C" name="t{i}" />' for i in range(7)
        )
        if kind == "failure":
            junit_body = junit_body[:-len('</testcase>')] + '<failure /></testcase>'
        if kind == "incomplete":
            junit_body = junit_body[: junit_body.rfind('<testcase')]
        gradle = executable(
            temp / "gradle",
            "#!/bin/sh\n"
            "mkdir -p app/build/outputs/androidTest-results/connected/p02Test\n"
            f"printf '%s' '<testsuite>{junit_body}</testsuite>' > "
            "app/build/outputs/androidTest-results/connected/p02Test/TEST-p02.xml\n"
            "echo 'BUILD SUCCESSFUL'\n"
            "exit 0\n",
        )
        report = temp / "report"
        env = os.environ.copy()
        env.update(
            ANDROID_HOME="/tmp/android-sdk-11076708",
            ANDROID_SDK_ROOT="/tmp/android-sdk-11076708",
            P02_ADB=str(adb),
            P02_GRADLE=str(gradle),
            P02_REPORT_DIR=str(report),
            P02_RUNS="1",
        )
        if expected_sha is not None:
            env["P02_EXPECTED_SHA"] = expected_sha
        result = subprocess.run(
            [str(HARNESS)], cwd=ROOT, env=env, text=True, capture_output=True, check=False
        )
        return result.returncode, result.stdout + result.stderr


success, success_output = run_case("success")
assert success == 0 and "PHYSICAL_TEST_PASS" in success_output, success_output
failure, failure_output = run_case("failure")
assert failure == 1 and "JUnit validation failed" in failure_output, failure_output
blocked, blocked_output = run_case("no-device")
assert blocked == 2 and "PHYSICAL_TEST_BLOCKED" in blocked_output, blocked_output
bad_sha, bad_sha_output = run_case("success", expected_sha="bad-sha")
assert bad_sha == 1 and "unexpected Git SHA" in bad_sha_output, bad_sha_output
incomplete, incomplete_output = run_case("incomplete")
assert incomplete == 1 and "JUnit validation failed" in incomplete_output, incomplete_output
print("harness_black_box_tests=5 passed=5 failed=0")
