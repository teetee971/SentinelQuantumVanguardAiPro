#!/usr/bin/env python3
"""Fail-closed checks on an actual merged release manifest (not the source manifest).
Usage: python3 scripts/verify-android-release-manifest.py PATH_TO_MERGED_ANDROIDMANIFEST.xml
"""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
FORBIDDEN = (
    "androidx.compose.ui.tooling.",
    "androidx.activity.ComponentActivity",
)
def inspect(path):
    root = ET.parse(path).getroot()
    app = root.find("application")
    if app is None:
        return ["missing application"]
    issues = []
    if app.get(A + "debuggable", "false") != "false":
        issues.append("release application is debuggable")
    if app.get(A + "testOnly", "false") != "false":
        issues.append("release application is testOnly")
    if app.get(A + "allowBackup") != "false":
        issues.append("release backup must be disabled")
    if app.get(A + "usesCleartextTraffic") != "false":
        issues.append("cleartext policy is not explicitly disabled")
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for element in app.findall(tag):
            name = element.get(A + "name", "")
            if any(name.startswith(prefix) for prefix in FORBIDDEN):
                issues.append(f"debug/tooling component in release: {name}")
            if element.get(A + "exported") == "true" and not name:
                issues.append(f"exported {tag} without explicit name")
    return issues

def main(argv):
    if len(argv) != 2 or not Path(argv[1]).is_file():
        print("BLOCKED: pass an existing merged release AndroidManifest.xml")
        return 2
    # Fail closed on source manifests, even when their attributes look safe.
    candidate = Path(argv[1]).resolve()
    app_build = (Path(__file__).resolve().parent.parent / "native-android-app/app/build/intermediates").resolve()
    try:
        relative = candidate.relative_to(app_build)
    except ValueError:
        print("BLOCKED: manifest is not a generated app/build/intermediates artifact")
        return 2
    parts = relative.parts
    if (len(parts) < 4 or parts[0] not in ("merged_manifest", "merged_manifests")
            or parts[1] != "releaseUnsigned" or candidate.name != "AndroidManifest.xml"):
        print("BLOCKED: expected a generated releaseUnsigned merged AndroidManifest.xml")
        return 2
    try:
        issues = inspect(candidate)
    except (ET.ParseError, OSError, ValueError) as exc:
        print(f"BLOCKED: unreadable merged release manifest: {exc}")
        return 2
    for issue in issues:
        print("FAIL:", issue)
    if issues:
        return 1
    print("PASS: merged release manifest static safety gate")
    print("NOTE: does not certify component authorization, signing, or Google Play compliance")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv))
