"""Safety-critical helpers for the standalone local AI laboratory.

This module deliberately uses only the Python standard library. It never downloads
models, starts a network listener, or invokes a model by itself.
"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any


class LabError(ValueError):
    """Expected laboratory validation failure."""


SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
REQUIRED_MANIFEST_KEYS = {
    "schema_version",
    "model_id",
    "status",
    "revision",
    "source",
    "license",
    "commercial_use",
    "tokenizer",
    "limits",
    "artifact",
}


def _object(value: Any, code: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise LabError(code)
    return value


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise LabError("JSON_INVALID") from exc
    return _object(value, "JSON_OBJECT_REQUIRED")


def verify_manifest(manifest: dict[str, Any], artifact_path: Path | None = None) -> dict[str, Any]:
    _object(manifest, "MANIFEST_OBJECT_REQUIRED")
    if manifest.get("status") != "VERIFIED":
        raise LabError("MODEL_UNVERIFIED")
    missing = REQUIRED_MANIFEST_KEYS - set(manifest)
    if missing:
        raise LabError("MANIFEST_KEYS_MISSING")
    if manifest["schema_version"] != 1:
        raise LabError("MANIFEST_SCHEMA_UNSUPPORTED")
    if not isinstance(manifest["model_id"], str) or not manifest["model_id"].strip():
        raise LabError("MODEL_ID_REQUIRED")
    for key in ("revision", "source", "license", "commercial_use", "tokenizer"):
        if not isinstance(manifest[key], str) or not manifest[key].strip():
            raise LabError(f"MANIFEST_{key.upper()}_REQUIRED")
    limits = _object(manifest["limits"], "MANIFEST_LIMITS_REQUIRED")
    if not isinstance(limits.get("max_context_tokens"), int) or limits["max_context_tokens"] <= 0:
        raise LabError("MANIFEST_CONTEXT_LIMIT_REQUIRED")
    artifact = _object(manifest["artifact"], "ARTIFACT_METADATA_REQUIRED")
    filename = artifact.get("filename")
    size_bytes = artifact.get("size_bytes")
    digest = artifact.get("sha256")
    if not isinstance(filename, str) or not filename or Path(filename).name != filename:
        raise LabError("ARTIFACT_FILENAME_INVALID")
    if not isinstance(size_bytes, int) or size_bytes <= 0:
        raise LabError("ARTIFACT_SIZE_REQUIRED")
    if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest):
        raise LabError("ARTIFACT_SHA256_REQUIRED")
    if artifact_path is not None:
        verify_artifact(manifest, artifact_path)
    return manifest


def verify_artifact(manifest: dict[str, Any], artifact_path: Path) -> dict[str, Any]:
    if manifest.get("status") != "VERIFIED":
        raise LabError("MODEL_UNVERIFIED")
    if not artifact_path.is_file():
        raise LabError("MODEL_FILE_MISSING")
    metadata = _object(manifest.get("artifact"), "ARTIFACT_METADATA_REQUIRED")
    if artifact_path.name != metadata.get("filename"):
        raise LabError("ARTIFACT_FILENAME_MISMATCH")
    actual_size = artifact_path.stat().st_size
    if actual_size != metadata.get("size_bytes"):
        raise LabError("ARTIFACT_SIZE_MISMATCH")
    digest = hashlib.sha256()
    with artifact_path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    if digest.hexdigest() != metadata.get("sha256"):
        raise LabError("ARTIFACT_SHA256_MISMATCH")
    return manifest


def load_corpus(path: Path) -> list[dict[str, Any]]:
    if not path.is_file():
        raise LabError("CORPUS_MISSING")
    cases: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as exc:
            raise LabError(f"CORPUS_JSON_INVALID_LINE_{line_number}") from exc
        item = _object(value, f"CORPUS_OBJECT_REQUIRED_LINE_{line_number}")
        if not isinstance(item.get("id"), str) or not item["id"]:
            raise LabError("CORPUS_ID_REQUIRED")
        if not isinstance(item.get("input"), str):
            raise LabError("CORPUS_INPUT_REQUIRED")
        if not isinstance(item.get("expected"), dict):
            raise LabError("CORPUS_EXPECTED_REQUIRED")
        cases.append(item)
    if not cases:
        raise LabError("CORPUS_EMPTY")
    return cases
