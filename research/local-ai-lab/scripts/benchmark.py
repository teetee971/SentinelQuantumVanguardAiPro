#!/usr/bin/env python3
"""Run bounded, offline llama.cpp measurements against an already verified model.

The script never downloads a model, opens a listener, invokes a shell, or exposes
model output in the report. A real model cannot run until its manifest is VERIFIED.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import resource
import selectors
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

from lablib import LabError, read_json, verify_manifest


EVAL_TIMING = re.compile(r"eval time\s*=\s*([0-9.]+)\s*ms\s*/\s*(\d+)\s*runs", re.I)


def fail(code: str) -> int:
    print(code, file=sys.stderr)
    return 2


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--prompt", required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--repeats", type=int, default=5)
    parser.add_argument("--max-tokens", type=int, default=64)
    parser.add_argument("--timeout-seconds", type=float, default=60.0)
    return parser.parse_args()


def timing_tokens_per_second(stderr: bytes) -> float | None:
    match = EVAL_TIMING.search(stderr.decode("utf-8", errors="replace"))
    if not match:
        return None
    milliseconds = float(match.group(1))
    tokens = int(match.group(2))
    if milliseconds <= 0 or tokens <= 0:
        return None
    return tokens / (milliseconds / 1000.0)


def run_once(args: argparse.Namespace) -> dict[str, Any]:
    command = [
        str(args.runtime),
        "-m", str(args.model),
        "-n", str(args.max_tokens),
        "--temp", "0",
        "--seed", "1",
        "--simple-io",
        "--no-display-prompt",
        "--log-disable",
    ]
    started = time.monotonic_ns()
    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            shell=False,
        )
    except OSError:
        return {"status": "START_FAILED", "returncode": None}

    assert process.stdin is not None
    assert process.stdout is not None
    assert process.stderr is not None
    process.stdin.write((args.prompt + "\n").encode("utf-8"))
    process.stdin.close()
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    first_output_ns: int | None = None
    deadline = time.monotonic() + args.timeout_seconds
    try:
        while first_output_ns is None and time.monotonic() < deadline:
            events = selector.select(max(0.0, deadline - time.monotonic()))
            if events and os.read(process.stdout.fileno(), 1):
                first_output_ns = time.monotonic_ns()
                break
        remaining = max(0.0, deadline - time.monotonic())
        process.wait(timeout=remaining)
        stdout_tail = process.stdout.read()
        stderr = process.stderr.read()
        finished_ns = time.monotonic_ns()
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        stderr = process.stderr.read()
        finished_ns = time.monotonic_ns()
        return {
            "status": "TIMEOUT",
            "returncode": process.returncode,
            "load_ms": None,
            "first_output_ms": None,
            "total_ms": (finished_ns - started) / 1_000_000,
            "tokens_per_second": None,
            "max_rss_kib": resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,
        }
    finally:
        selector.close()

    del stdout_tail  # Output content is intentionally never persisted.
    return {
        "status": "PASS" if process.returncode == 0 else "PROCESS_FAILED",
        "returncode": process.returncode,
        "load_ms": None,
        "first_output_ms": None if first_output_ns is None else (first_output_ns - started) / 1_000_000,
        "total_ms": (finished_ns - started) / 1_000_000,
        "tokens_per_second": timing_tokens_per_second(stderr),
        "max_rss_kib": resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,
    }


def statistics(values: list[float]) -> dict[str, float | None]:
    if not values:
        return {"min": None, "max": None, "median": None, "mean": None, "stdev": None}
    import statistics as stats
    return {
        "min": min(values),
        "max": max(values),
        "median": stats.median(values),
        "mean": stats.mean(values),
        "stdev": stats.stdev(values) if len(values) > 1 else 0.0,
    }


def main() -> int:
    args = parse_args()
    if args.repeats < 5:
        return fail("REPEATS_MINIMUM_5")
    if args.max_tokens <= 0 or args.max_tokens > 4096:
        return fail("MAX_TOKENS_OUT_OF_RANGE")
    if args.timeout_seconds <= 0 or args.timeout_seconds > 600:
        return fail("TIMEOUT_OUT_OF_RANGE")
    if not args.runtime.is_file() or not os.access(args.runtime, os.X_OK):
        return fail("RUNTIME_NOT_EXECUTABLE")
    try:
        manifest = read_json(args.manifest)
        verify_manifest(manifest, args.model)
    except LabError as error:
        return fail(str(error))
    runs = [run_once(args) for _ in range(args.repeats)]
    successful = [run for run in runs if run["status"] == "PASS"]
    report = {
        "schema_version": 1,
        "runtime": {"path": str(args.runtime), "network": "not_used", "shell": False},
        "model": {
            "model_id": manifest["model_id"],
            "revision": manifest["revision"],
            "sha256": manifest["artifact"]["sha256"],
            "path": str(args.model),
        },
        "hardware": {
            "platform": sys.platform,
            "machine": os.uname().machine if hasattr(os, "uname") else None,
            "android_device": "NOT_CAPTURED",
            "battery": "NOT_CAPTURED",
            "temperature": "NOT_CAPTURED",
        },
        "parameters": {"repeats": args.repeats, "max_tokens": args.max_tokens, "timeout_seconds": args.timeout_seconds},
        "runs": runs,
        "statistics": {
            "first_output_ms": statistics([r["first_output_ms"] for r in successful if r["first_output_ms"] is not None]),
            "total_ms": statistics([r["total_ms"] for r in successful]),
            "tokens_per_second": statistics([r["tokens_per_second"] for r in successful if r["tokens_per_second"] is not None]),
            "failure_rate": (len(runs) - len(successful)) / len(runs),
        },
    }
    encoded = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    else:
        print(encoded, end="")
    return 0 if len(successful) == len(runs) else 1


if __name__ == "__main__":
    raise SystemExit(main())
