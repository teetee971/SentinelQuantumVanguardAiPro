#!/usr/bin/env python3
"""Classify Android logcat crash evidence for the Sentinel process.

Exit 0 when the supplied log contains no attributable Sentinel fatal exception or ANR.
Exit 1 when a fatal exception is attributed to com.sentinel.quantum or a Sentinel ANR is present.
Exit 2 when the evidence file cannot be read.
"""

from __future__ import annotations

import pathlib
import re
import sys

PACKAGE = "com.sentinel.quantum"
PROCESS_RE = re.compile(r"\bProcess:\s*([^,\s]+)")
ANR_RE = re.compile(r"\bANR in com\.sentinel\.quantum(?:\s|\(|$)")
FATAL_MARKER = "FATAL EXCEPTION:"
PROCESS_LOOKAHEAD_LINES = 8


def has_attributed_failure(lines: list[str]) -> tuple[bool, str | None]:
    pending_fatal = 0

    for line in lines:
        if ANR_RE.search(line):
            return True, "ANR"

        if FATAL_MARKER in line:
            pending_fatal = PROCESS_LOOKAHEAD_LINES
            same_line_process = PROCESS_RE.search(line)
            if same_line_process:
                if same_line_process.group(1) == PACKAGE:
                    return True, "FATAL_EXCEPTION"
                pending_fatal = 0
            continue

        if pending_fatal > 0:
            process = PROCESS_RE.search(line)
            if process:
                if process.group(1) == PACKAGE:
                    return True, "FATAL_EXCEPTION"
                pending_fatal = 0
                continue
            pending_fatal -= 1

    return False, None


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: phone-core-logcat-crash-oracle.py <logcat-file>", file=sys.stderr)
        return 2

    evidence = pathlib.Path(argv[1])
    try:
        lines = evidence.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError as exc:
        print(f"unable to read logcat evidence: {exc}", file=sys.stderr)
        return 2

    failed, reason = has_attributed_failure(lines)
    if failed:
        print(f"Sentinel runtime failure detected: {reason}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
