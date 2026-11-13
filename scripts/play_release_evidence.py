#!/usr/bin/env python3
"""Read-only Google Play release evidence gate; no credentials or secrets are read."""
import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

def inspect(root: Path, aab: Path | None) -> dict:
    build_file = root / "native-android-app/app/build.gradle"
    manifest = root / "native-android-app/app/src/main/AndroidManifest.xml"
    report = {"source": str(root), "checks": {}, "warnings": [], "release_ready": False}
    if not build_file.is_file():
        report["checks"]["build_config"] = "MISSING"
        return report
    text = build_file.read_text(encoding="utf-8")
    def value(key):
        match = re.search(r"(?m)^\s*" + re.escape(key) + r"\s+['\"]?([\w.]+)", text)
        return match.group(1) if match else None
    report["checks"]["package"] = value("applicationId")
    report["checks"]["versionCode"] = value("versionCode")
    report["checks"]["versionName"] = value("versionName")
    report["checks"]["targetSdk"] = value("targetSdk")
    report["checks"]["release_signing_gate"] = all(
        name in text for name in ("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    )
    if manifest.is_file():
        try:
            ET.parse(manifest)
            report["checks"]["source_manifest_xml"] = "PARSE_OK"
        except ET.ParseError:
            report["checks"]["source_manifest_xml"] = "INVALID"
    else:
        report["checks"]["source_manifest_xml"] = "MISSING"
    if aab is None:
        report["checks"]["signed_aab"] = "NOT_PROVIDED"
    elif not aab.is_file():
        report["checks"]["signed_aab"] = "MISSING"
    else:
        report["checks"]["aab_sha256"] = hashlib.file_digest(aab.open("rb"), "sha256").hexdigest()
        report["checks"]["signed_aab"] = "UNVERIFIED"
        report["warnings"].append("SHA-256 is not signature verification; inspect bundle with jarsigner and bundletool.")
    report["warnings"].extend([
        "Source manifest is not the merged release manifest.",
        "This script does not execute Gradle, Android lint, physical tests or Play policy checks.",
        "Auth0 email verification and backend authorization remain unverified.",
        "Do not upload releaseUnsigned artifacts to Google Play.",
    ])
    return report

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--aab", type=Path)
    args = parser.parse_args()
    result = inspect(args.repo, args.aab)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result["release_ready"] else 2

if __name__ == "__main__":
    sys.exit(main())
