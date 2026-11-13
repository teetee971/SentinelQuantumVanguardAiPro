#!/usr/bin/env python3
import argparse
import sys
from pathlib import Path

from lablib import LabError, read_json, verify_manifest


parser = argparse.ArgumentParser(description="Validate one local AI lab model manifest.")
parser.add_argument("manifest", type=Path)
parser.add_argument("--model", type=Path)
args = parser.parse_args()

try:
    manifest = read_json(args.manifest)
    verify_manifest(manifest, args.model)
except LabError as error:
    print(str(error), file=sys.stderr)
    raise SystemExit(2)

print(f"MANIFEST_VALID model_id={manifest['model_id']} sha256={manifest['artifact']['sha256']}")
