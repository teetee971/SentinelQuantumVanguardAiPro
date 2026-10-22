#!/usr/bin/env python3
"""Fail-closed source-order audit for history clear/write invariants.

This is intentionally NOT a concurrency, Room integration or Android runtime test.
"""
from pathlib import Path
import sys

root = Path(__file__).resolve().parent.parent
source = root / "native-android-app/app/src/main/java/com/sentinel/quantum/security/CallFilterLogStore.kt"
try:
    code = source.read_text(encoding="utf-8")
except (OSError, UnicodeError):
    print("CALL_HISTORY_ORDER_GATE=BLOCKED_SOURCE_MISSING")
    sys.exit(2)

def within(start: str, end: str) -> str | None:
    a = code.find(start)
    if a < 0:
        return None
    b = code.find(end, a + len(start))
    if b < 0:
        return None
    return code[a:b]

write = within("fun recordAsync(", "suspend fun history(")
clear = within("suspend fun clear(", "private fun sanitize(")
findings = []
if write is None or clear is None:
    findings.append("METHOD_BOUNDARIES_CHANGED")
else:
    required_write_order = (
        "val generation = CLEAR_GENERATION.get()",
        "WRITER.execute",
        "synchronized(HISTORY_LOCK)",
        "if (generation != CLEAR_GENERATION.get()) return@execute",
        "dao.insertAndPrune(",
    )
    positions = [write.find(item) for item in required_write_order]
    if -1 in positions or positions != sorted(positions):
        findings.append("WRITE_GENERATION_AND_LOCK_ORDER_CHANGED")

    required_clear_order = (
        "withContext(Dispatchers.IO)",
        "synchronized(HISTORY_LOCK)",
        "CLEAR_GENERATION.incrementAndGet()",
        "dao.clearBlocking()",
    )
    positions = [clear.find(item) for item in required_clear_order]
    if -1 in positions or positions != sorted(positions):
        findings.append("CLEAR_LOCK_AND_GENERATION_ORDER_CHANGED")

if "MAX_PENDING_WRITES = 128" not in code:
    findings.append("QUEUE_CAPACITY_CHANGED")
if "@Transaction\n    fun insertAndPrune(" not in code:
    findings.append("TRANSACTION_SIGNATURE_CHANGED")
for finding in findings:
    print("CALL_HISTORY_ORDER_FINDING=" + finding)
if findings:
    print("CALL_HISTORY_ORDER_GATE=REVIEW_REQUIRED")
    sys.exit(1)
print("CALL_HISTORY_ORDER_GATE=PASS_STATIC_ORDER")
print("NOTE=Source ordering only; race, disk persistence and crash recovery remain untested")
