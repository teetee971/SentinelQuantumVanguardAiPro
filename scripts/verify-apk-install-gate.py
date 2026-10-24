#!/usr/bin/env python3
"""Fail closed until the TOCTOU-safe APK handoff has a reviewed replacement."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
manager = (root / "native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelApkManager.kt").read_text()
screen = (root / "native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SecurityLabProScreen.kt").read_text()

checks = {
    "unsafe install API explicitly rejects": bool(re.search(
        r'fun\s+installUserSelected\s*\([^)]*\)\s*:\s*Nothing\s*\{\s*throw\s+UnsupportedOperationException',
        manager,
        re.S
    )),
    "no install API called from UI": "installUserSelected(" not in screen,
    "no direct APK install intent in manager": "ACTION_INSTALL_PACKAGE" not in manager,
    "no pending install UI": re.search(r"\binstallPending\b", screen) is None,
}
for description, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {description}")
if not all(checks.values()):
    print("BLOCKED: Unsafe APK installation route or stale protection contract")
    sys.exit(1)
print("APK install fail-closed static gate passed (not a dynamic security proof)")
