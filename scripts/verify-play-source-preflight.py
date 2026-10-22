#!/usr/bin/env python3
"""Local, read-only preflight for Android Play bundle readiness.

A pass means only that the checked source configuration is internally consistent;
it never certifies Play policy compliance or device functionality.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
gradle_path = root / "native-android-app/app/build.gradle"
manifest_path = root / "native-android-app/app/src/main/AndroidManifest.xml"
try:
    gradle = gradle_path.read_text(encoding="utf-8")
    manifest = ET.parse(manifest_path).getroot()
except (OSError, UnicodeError, ET.ParseError) as exc:
    print(f"PLAY_PREFLIGHT=BLOCKED_INPUT:{type(exc).__name__}")
    sys.exit(2)

def property_value(name):
    match = re.search(r"(?m)^\s*" + re.escape(name) + r"\s*(?:=\s*)?(\d+)\s*$", gradle)
    return int(match.group(1)) if match else None

target = property_value("targetSdk")
compile_sdk = property_value("compileSdk")
min_sdk = property_value("minSdk")
version_code = property_value("versionCode")
print(f"TARGET_SDK={target}")
print(f"COMPILE_SDK={compile_sdk}")
print(f"MIN_SDK={min_sdk}")
print(f"VERSION_CODE={version_code}")

findings = []
if target is None or target < 36:
    findings.append("TARGET_SDK_BELOW_36_OR_UNDETERMINED")
if compile_sdk is None or target is None or compile_sdk < target:
    findings.append("COMPILE_SDK_BELOW_TARGET")
if min_sdk is None or target is None or min_sdk > target:
    findings.append("INVALID_MIN_SDK")
if version_code is None or version_code < 1:
    findings.append("INVALID_VERSION_CODE")

android = "{http://schemas.android.com/apk/res/android}"
application = manifest.find("application")
if application is None:
    findings.append("NO_APPLICATION")
else:
    if application.get(android + "allowBackup") != "false":
        findings.append("BACKUP_POLICY_REVIEW_REQUIRED")
    if application.get(android + "usesCleartextTraffic") != "false":
        findings.append("CLEARTEXT_POLICY_REVIEW_REQUIRED")

# Fail closed on missing release-build safeguards, but do not claim that
# their presence proves correctness of signing or any generated bundle.
for token, finding in [
    ("releaseUnsigned {", "NO_UNSIGNED_DRY_RUN_VARIANT"),
    ("signingConfig = null", "UNSIGNED_VARIANT_NOT_EXPLICITLY_UNSIGNED"),
    ("KEYSTORE_FILE", "RELEASE_KEYSTORE_NOT_GATED"),
    ("KEYSTORE_PASSWORD", "RELEASE_STORE_PASSWORD_NOT_GATED"),
    ("KEY_ALIAS", "RELEASE_ALIAS_NOT_GATED"),
    ("KEY_PASSWORD", "RELEASE_KEY_PASSWORD_NOT_GATED"),
]:
    if token not in gradle:
        findings.append(finding)

permissions = sorted({
    p.get(android + "name", "") for p in manifest.findall("uses-permission")
})
sensitive = sorted(set(permissions) & {
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.SEND_SMS",
    "android.permission.RECEIVE_MMS",
    "android.permission.RECEIVE_WAP_PUSH",
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.USE_FULL_SCREEN_INTENT",
})
for permission in sensitive:
    print("PLAY_POLICY_DECLARATION_REVIEW=" + permission)
print("NOTE=Restricted permissions require manual role, policy and Play Console review")
print("NOTE=No AAB generated, no signature verified, no Android tests executed")
for finding in findings:
    print("PLAY_PREFLIGHT_FINDING=" + finding)
if findings:
    print("PLAY_PREFLIGHT=FAIL_CONFIG")
    sys.exit(1)
print("PLAY_PREFLIGHT=PASS_SOURCE_CONFIG_ONLY")
