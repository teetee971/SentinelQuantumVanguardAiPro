#!/usr/bin/env python3
"""Offline source security review for Sentinel candidate; no build, APK install, or secrets."""
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
app = root / "native-android-app/app"
manifest = app / "src/main/AndroidManifest.xml"
build = app / "build.gradle"
extraction = app / "src/main/res/xml/data_extraction_rules.xml"
paths = app / "src/main/res/xml/file_paths.xml"
A = "{http://schemas.android.com/apk/res/android}"
issues = []
checks = []
def check(code, ok, detail, severity="HIGH"):
    checks.append({"id": code, "passed": bool(ok), "detail": detail})
    if not ok:
        issues.append({"id": code, "severity": severity, "detail": detail})

try:
    m = ET.parse(manifest).getroot()
    application = m.find("application")
    gradle = build.read_text(encoding="utf-8")
    check("APP_BACKUP", application is not None and application.get(A+"allowBackup") == "false", "Disable Android app backup.")
    check("CLEARTEXT", application is not None and application.get(A+"usesCleartextTraffic") == "false", "Disallow cleartext traffic.")
    check("VERSION", bool(re.search(r'(?m)^\s*versionCode\s+11\s*$', gradle)) and bool(re.search(r'(?m)^\s*versionName\s+"1\.0\.10"\s*$', gradle)), "Candidate must be 1.0.10/build 11.")
    check("R8", bool(re.search(r'(?s)release\s*\{[^{}]*minifyEnabled\s+true', gradle)), "Release build should enable R8.")
    check("BACKUP_RULES", extraction.exists() and 'domain="root" path="."' in extraction.read_text(encoding="utf-8"), "Inspect data extraction exclusions.")
    declared = list(application) if application is not None else []
    components = []
    for node in declared:
        if node.tag not in {"activity", "receiver", "service", "provider"}:
            continue
        name = node.get(A+"name", "")
        exported = node.get(A+"exported")
        permission = node.get(A+"permission")
        has_filter = node.find("intent-filter") is not None
        components.append({"type":node.tag, "name":name, "exported":exported, "permission":permission, "intent_filter":has_filter})
        check("EXPORTED_DECLARATION:"+name, exported in {"true","false"}, "Explicit exported flag for "+name)
        if node.tag == "provider":
            check("PROVIDER_NOT_PUBLIC:"+name, exported == "false", "Provider must not be publicly exported.")
        if exported == "true" and node.tag in {"receiver","service"}:
            check("EXPORTED_PRIVILEGED:"+name, bool(permission), "Exported services/receivers require declared caller permission.")
    path_ok = paths.exists()
    if path_ok:
        p = ET.parse(paths).getroot()
        path_ok = all(x.tag in {"cache-path"} for x in p)
    check("FILE_PROVIDER_SCOPE", path_ok, "FileProvider must expose only narrow cache paths.")
    check("DEBUG_DISABLED_RELEASE", "debuggable true" not in gradle.lower(), "No explicit debuggable release switch.", "MEDIUM")
except (OSError, ET.ParseError) as error:
    print("BLOCKED: source parsing failed:", error, file=sys.stderr)
    sys.exit(2)
out = {"scope":"source-only; does not inspect merged manifest, APK, running app, or backend",
       "checks":checks, "exported_components":components, "findings":issues,
       "verdict":"SOURCE_CHECKS_PASSED_NOT_SECURITY_CERTIFIED" if not issues else "SOURCE_FINDINGS_REQUIRE_REVIEW"}
print(json.dumps(out, ensure_ascii=False, indent=2))
sys.exit(1 if issues else 0)
