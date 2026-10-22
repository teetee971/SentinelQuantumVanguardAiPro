#!/usr/bin/env python3
"""Fail-closed static audit for history-clear and insert/prune ordering. Not a runtime test."""
from pathlib import Path
import re
import sys

src = (Path(__file__).resolve().parent.parent /
       "native-android-app/app/src/main/java/com/sentinel/quantum/security/CallFilterLogStore.kt")
try:
    code = src.read_text(encoding="utf-8")
except OSError:
    print("CALL_HISTORY_CLEAR_GATE=BLOCKED_SOURCE_MISSING")
    sys.exit(2)

findings = []
if re.search(r"suspend\s+fun\s+clear\s*\(\s*\)\s*=\s*dao\.clear\s*\(\s*\)", code):
    findings.append("CLEAR_BYPASSES_ASYNC_WRITER")
if re.search(r"dao\.insert\s*\(", code) and re.search(r"dao\.prune\s*\(", code):
    if not re.search(r"@Transaction|withTransaction|immediateTransaction", code):
        findings.append("INSERT_AND_PRUNE_NOT_EXPLICITLY_ATOMIC")
if "synchronized(HISTORY_LOCK)" not in code or "CLEAR_GENERATION.incrementAndGet()" not in code or "generation != CLEAR_GENERATION.get()" not in code:
    findings.append("CLEAR_WRITE_SERIALIZATION_NOT_PRESENT")
if not re.search(r"@Transaction\s+fun\s+insertAndPrune\s*\(", code):
    findings.append("ATOMIC_INSERT_PRUNE_NOT_PRESENT")
if not re.search(r"dao\.insertAndPrune\s*\(", code):
    findings.append("WRITE_DOES_NOT_USE_TRANSACTION_METHOD")
for finding in findings:
    print("CALL_HISTORY_FINDING=" + finding)
if findings:
    print("CALL_HISTORY_CLEAR_GATE=BLOCKED_DESIGN_REVIEW")
    sys.exit(1)
print("CALL_HISTORY_CLEAR_GATE=STATIC_CHECK_PASS")
print("NOTE=Static PASS does not prove actual serialization or crash safety")
