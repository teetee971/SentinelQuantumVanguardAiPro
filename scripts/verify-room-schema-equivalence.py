#!/usr/bin/env python3
"""Compare Room schema v1 with a local generated export, without ADB or writes."""
import json
import pathlib
import sys
import hashlib

if len(sys.argv) != 3:
    print("Usage: python3 scripts/verify-room-schema-equivalence.py <generated-local-1.json> <tracked-1.json>")
    sys.exit(2)
a, b = map(pathlib.Path, sys.argv[1:])
try:
    original_bytes, tracked_bytes = a.read_bytes(), b.read_bytes()
    original, tracked = json.loads(original_bytes), json.loads(tracked_bytes)
except (OSError, UnicodeError, ValueError) as exc:
    print(f"ROOM_SCHEMA_COMPARE=BLOCKED_INPUT_ERROR:{type(exc).__name__}")
    sys.exit(2)
for name, raw, data in (("GENERATED", original_bytes, original), ("TRACKED", tracked_bytes, tracked)):
    db = data.get("database", {})
    print(f"{name}_SHA256={hashlib.sha256(raw).hexdigest()}")
    print(f"{name}_VERSION={db.get('version')}")
    print(f"{name}_IDENTITY={db.get('identityHash')}")
if original != tracked:
    print("ROOM_SCHEMA_COMPARE=FAIL_SEMANTIC_DIFFERENCE")
    sys.exit(1)
print("ROOM_SCHEMA_COMPARE=PASS_SEMANTIC_EQUIVALENCE")
if original_bytes != tracked_bytes:
    print("ROOM_SCHEMA_BYTES=DIFFERENT_FORMAT_OR_ORDER")
    print("NOTE=Semantic equality does not prove identical source bytes")
else:
    print("ROOM_SCHEMA_BYTES=IDENTICAL")
print("NOTE=Migration and Android runtime tests are separate gates")
