#!/usr/bin/env python3
"""Static guard for the Room call-screening history contract; no Android required."""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
source_path = root / "native-android-app/app/src/main/java/com/sentinel/quantum/security/CallFilterLogStore.kt"
schema_path = root / "native-android-app/app/schemas/com.sentinel.quantum.security.SentinelLocalDatabase/1.json"
try:
    source = source_path.read_text(encoding="utf-8")
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
except (OSError, UnicodeError, ValueError) as exc:
    print(f"CALL_HISTORY_CONTRACT=BLOCKED_INPUT:{type(exc).__name__}")
    sys.exit(2)

findings = []
database = schema.get("database", {})
if database.get("version") != 1:
    findings.append("SCHEMA_VERSION_NOT_V1")
entities = database.get("entities", [])
table = next((e for e in entities if e.get("tableName") == "call_filter_decisions"), None)
expected = {"id", "occurredAtMs", "action", "reason", "source", "numberFingerprint"}
if table is None or {f.get("columnName") for f in table.get("fields", [])} != expected:
    findings.append("SCHEMA_COLUMNS_CHANGED")
if not re.search(r"@Database\s*\(.*?version\s*=\s*1\b", source, re.S):
    findings.append("SOURCE_DATABASE_VERSION_CHANGED")
if re.search(r"fallbackToDestructiveMigration\w*\s*\(", source):
    findings.append("DESTRUCTIVE_MIGRATION_FALLBACK")
if not re.search(r"MAX_HISTORY_ENTRIES\s*=\s*500\b", source):
    findings.append("HISTORY_BOUND_CHANGED")
if "numberFingerprint = decision.normalizedNumber?.let(fingerprinter::fingerprint)" not in source:
    findings.append("NUMBER_FINGERPRINT_PATH_CHANGED")
if not (re.search(r"dao\.insertAndPrune\s*\(", source) and re.search(r"@Transaction\s+fun\s+insertAndPrune\s*\(", source) and re.search(r"MAX_HISTORY_ENTRIES\s*\)", source)):
    findings.append("HISTORY_PRUNE_PATH_CHANGED")
for finding in findings:
    print(f"CALL_HISTORY_FINDING={finding}")
if findings:
    print("CALL_HISTORY_CONTRACT=FAIL_REVIEW_REQUIRED")
    sys.exit(1)
print("CALL_HISTORY_CONTRACT=PASS_STATIC_BASELINE")
print("NOTE=This is a source-level guard, not a proof of runtime privacy, migration, or concurrency safety")
