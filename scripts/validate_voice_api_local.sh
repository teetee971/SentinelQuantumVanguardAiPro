#!/usr/bin/env bash
# Local, non-GitHub-Actions voice API regression gate.
# Does not deploy, sign artifacts, or require production credentials.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
PYTHON="${PYTHON:-python3}"
"$PYTHON" -m pip install -r backend/voice-api/requirements-test.txt
"$PYTHON" -m compileall -q backend/voice-api
"$PYTHON" -m unittest discover -s backend/voice-api -p 'test_*.py' -v
echo "PASS: voice-api Python test discovery and execution"
