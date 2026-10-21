#!/usr/bin/env python3
"""Read-only Sentinel 1.0.10 security triage; findings never print secret values."""
import hashlib, json, pathlib, re, sys, zipfile
ROOT = pathlib.Path.home() / "sentinel-qa-release-110"
APP = ROOT / "native-android-app/app"
APK = APP / "build/outputs/apk/debug/app-debug.apk"
EXPECTED = "f97c7cc024a806c401d9e16641f1306ef8ec32fb267d70d132bf223f9d86a82d"
EXT = {".kt", ".java", ".xml", ".json", ".gradle", ".properties", ".js", ".ts"}
SKIP = {"build", ".git", ".gradle", "node_modules", "test", "androidTest", "schemas"}
PATTERNS = {
    "PRIVATE_KEY_HEADER": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    "GITHUB_TOKEN": re.compile(r"\b(?:ghp_|gho_|ghu_|ghs_|ghr_)[A-Za-z0-9]{20,}\b"),
    "GITHUB_FINE_GRAINED": re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
    "AWS_ACCESS_KEY": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "GOOGLE_API_KEY": re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
    "HARDCODED_BEARER": re.compile(r"(?i)authorization.{0,35}bearer [A-Za-z0-9._-]{24,}"),
}
def emit(status, **fields):
    print(json.dumps({"status": status, **fields}, ensure_ascii=False, indent=2))
if not APK.is_file():
    emit("BLOCKED", reason="Candidate APK absent")
    sys.exit(2)
sha = hashlib.file_digest(APK.open("rb"), "sha256").hexdigest()
if sha != EXPECTED:
    emit("BLOCKED", reason="APK hash mismatch")
    sys.exit(2)
findings, unreadable = [], []
count = 0
for base in (APP / "src/main", APP / "proguard-rules.pro", ROOT / "native-android-app/gradle.properties"):
    paths = base.rglob("*") if base.is_dir() else [base]
    for path in paths:
        if not path.is_file() or path.suffix not in EXT or any(part in SKIP for part in path.parts):
            continue
        if path.stat().st_size > 2_000_000:
            continue
        try:
            data = path.read_text("utf-8")
        except (UnicodeDecodeError, OSError):
            unreadable.append(str(path.relative_to(ROOT)))
            continue
        count += 1
        for name, pattern in PATTERNS.items():
            for match in pattern.finditer(data):
                findings.append({"rule": name, "path": str(path.relative_to(ROOT)), "line": data.count("\n", 0, match.start())+1, "value": "[REDACTED]"})
try:
    with zipfile.ZipFile(APK) as z:
        bad = z.testzip()
        names = z.namelist()
        native = sum(n.startswith("lib/") and n.endswith(".so") for n in names)
        dex = sum(re.fullmatch(r"classes\d*\.dex", n) is not None for n in names)
except (OSError, zipfile.BadZipFile):
    emit("BLOCKED", reason="APK ZIP invalid")
    sys.exit(2)
if bad:
    emit("BLOCKED", reason="APK entry CRC failed", path=bad)
    sys.exit(2)
emit("TRIAGE_COMPLETE_NOT_SECURITY_CERTIFIED", scanned_files=count,
     unreadable_files=unreadable, apk_sha256=sha, dex_files=dex,
     native_libraries=native, findings=findings,
     limitations="No runtime testing, no merged manifest validation, regex scan cannot exclude all leaks; no signing key accessed.")
sys.exit(1 if findings or unreadable else 0)
