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
CLASS = "com.sentinel.quantum.security.CallBlocklistStoreConcurrencyInstrumentationTest"
METHODS = [
    "two_store_instances_concurrently_add_prefixes_without_lost_rules",
    "concurrent_add_and_remove_preserve_the_complete_committed_state",
    "purge_and_exact_number_addition_do_not_publish_a_torn_snapshot",
    "replacement_and_snapshot_publication_never_leave_an_old_manual_set",
    "simulated_snapshot_time_does_not_publish_an_expired_cache",
    "arcep_activation_and_manual_prefix_addition_are_serialized",
    "signed_installation_is_serialized_and_replay_is_rejected",
]


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
            f'<testcase classname="{CLASS}" name="{method}" />' for method in METHODS
        )
        if kind == "failure":
            junit_body = junit_body.replace(
                f'classname="{CLASS}" name="{METHODS[-1]}" />',
                f'classname="{CLASS}" name="{METHODS[-1]}"><failure /></testcase>',
            )
        if kind == "incomplete":
            junit_body = junit_body[: junit_body.rfind('<testcase')]
        results_dir = temp / "results"
        gradle_write_results = kind != "stale"
        gradle_script = "#!/bin/sh\n"
        if gradle_write_results:
            gradle_script += (
                f"mkdir -p '{results_dir}'\n"
                f"printf '%s' '<testsuite>{junit_body}</testsuite>' > '{results_dir}/TEST-p02.xml'\n"
            )
        gradle_script += "echo 'BUILD SUCCESSFUL'\nexit 0\n"
        gradle = executable(
            temp / "gradle",
            gradle_script,
        )
        if kind == "stale":
            results_dir.mkdir(parents=True, exist_ok=True)
            (results_dir / "TEST-stale.xml").write_text(
                f'<testsuite>{junit_body}</testsuite>', encoding="utf-8"
            )
        report = temp / "report"
        env = os.environ.copy()
        env.update(
            ANDROID_HOME="/tmp/android-sdk-11076708",
            ANDROID_SDK_ROOT="/tmp/android-sdk-11076708",
            P02_ADB=str(adb),
            P02_GRADLE=str(gradle),
            P02_REPORT_DIR=str(report),
            P02_RESULTS_ROOT=str(results_dir),
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
stale, stale_output = run_case("stale")
assert stale == 1 and "no fresh JUnit report" in stale_output, stale_output
print("harness_black_box_tests=5 passed=5 failed=0")
