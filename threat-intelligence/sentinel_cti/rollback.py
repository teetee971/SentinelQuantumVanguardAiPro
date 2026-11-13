"""Durable, fail-closed STIC sequence acceptance.

The store is a local crash-safe monotonic guard, not a hardware or server trust
anchor.  A caller that can restore both the state file and its initialization
marker can restore the accepted sequence as well; a remote or hardware anchor is
required for protection against that attacker.
"""

from __future__ import annotations

import fcntl
import json
import os
import re
import stat
import tempfile
from dataclasses import dataclass
from contextlib import contextmanager
from pathlib import Path
from types import MappingProxyType
from typing import Any

from .envelope import EnvelopeVerificationError, TrustRegistry, VerifiedEnvelope, verify_envelope
from .jcs import JcsError, parse_json

STORE_SCHEMA = 1
MAX_STATE_BYTES = 256 * 1024
_MARKER = b"SENTINEL-STIC-SEQUENCE-STORE\n1\n"
_SEPARATOR = "\x1f"


class SequenceStoreError(RuntimeError):
    """The sequence store cannot safely answer or commit a request."""


class SequenceReplayError(SequenceStoreError):
    """The candidate sequence is not strictly newer than the stored value."""


class SequenceConflictError(SequenceStoreError):
    """A DELTA does not apply to the current sequence."""


@dataclass(frozen=True)
class SequenceReceipt:
    environment: str
    flow: str
    sequence_namespace: str
    sequence: int
    catalog_type: str
    payload_sha256: str


def _scope_key(environment: str, flow: str, namespace: str) -> str:
    # Environment/flow/key identifiers are already strict bounded text.  The
    # separator is not accepted by those identifiers in normal deployments, but
    # length-prefixing removes any ambiguity if a registry evolves later.
    return json.dumps([environment, flow, namespace], ensure_ascii=False, separators=(",", ":"))


def _safe_regular_file(path: Path) -> None:
    try:
        info = path.lstat()
    except FileNotFoundError:
        return
    if stat.S_ISLNK(info.st_mode) or not stat.S_ISREG(info.st_mode):
        raise SequenceStoreError(f"unsafe sequence-store path: {path}")
    if info.st_mode & 0o077:
        raise SequenceStoreError(f"sequence-store permissions are too broad: {path}")


def _fsync_directory(directory: Path) -> None:
    flags = os.O_RDONLY
    if hasattr(os, "O_DIRECTORY"):
        flags |= os.O_DIRECTORY
    fd = os.open(directory, flags)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


class SequenceStore:
    """A single-file monotonic store with an explicit initialization marker."""

    def __init__(self, path: str | os.PathLike[str]):
        self.path = Path(path)
        self.marker_path = Path(f"{self.path}.initialized")
        self.lock_path = Path(f"{self.path}.lock")
        if self.path.parent.exists() and not self.path.parent.is_dir():
            raise SequenceStoreError("sequence-store parent is not a directory")
        self.path.parent.mkdir(parents=True, exist_ok=True)
        for candidate in (self.path, self.marker_path, self.lock_path):
            _safe_regular_file(candidate)
        self._ensure_lock_file()

    def _ensure_lock_file(self) -> None:
        nofollow = getattr(os, "O_NOFOLLOW", 0)
        try:
            fd = os.open(self.lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY | nofollow, 0o600)
        except FileExistsError:
            return
        try:
            os.write(fd, b"STIC sequence lock\n")
            os.fsync(fd)
        finally:
            os.close(fd)

    @contextmanager
    def _locked(self):
        nofollow = getattr(os, "O_NOFOLLOW", 0)
        try:
            fd = os.open(self.lock_path, os.O_RDWR | nofollow)
        except OSError as exc:
            raise SequenceStoreError("cannot open sequence-store lock") from exc
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
            yield
        finally:
            fcntl.flock(fd, fcntl.LOCK_UN)
            os.close(fd)

    def _read_state(self) -> dict[str, Any]:
        state_exists = self.path.exists()
        marker_exists = self.marker_path.exists()
        if not state_exists and not marker_exists:
            return {"schema": STORE_SCHEMA, "records": {}}
        if state_exists != marker_exists:
            raise SequenceStoreError("sequence store and initialization marker disagree")
        _safe_regular_file(self.path)
        _safe_regular_file(self.marker_path)
        try:
            marker = self.marker_path.read_bytes()
            raw = self.path.read_bytes()
        except OSError as exc:
            raise SequenceStoreError("cannot read sequence store") from exc
        if marker != _MARKER or len(raw) > MAX_STATE_BYTES:
            raise SequenceStoreError("sequence store marker or size invalid")
        try:
            state = parse_json(raw, max_bytes=MAX_STATE_BYTES)
        except (JcsError, TypeError) as exc:
            raise SequenceStoreError("sequence store is corrupt") from exc
        if not isinstance(state, dict) or state.get("schema") != STORE_SCHEMA or not isinstance(state.get("records"), dict):
            raise SequenceStoreError("unsupported or corrupt sequence-store schema")
        for scope, record in state["records"].items():
            if not isinstance(scope, str) or not isinstance(record, dict):
                raise SequenceStoreError("invalid sequence-store record")
            try:
                parts = parse_json(scope)
            except (JcsError, TypeError) as exc:
                raise SequenceStoreError("invalid sequence-store scope") from exc
            if not isinstance(parts, list) or len(parts) != 3 or not all(isinstance(item, str) and item for item in parts):
                raise SequenceStoreError("invalid sequence-store scope")
            if set(record) != {"sequence", "catalog_type", "payload_sha256"}:
                raise SequenceStoreError("invalid sequence-store record fields")
            if not isinstance(record["sequence"], int) or isinstance(record["sequence"], bool) or record["sequence"] < 1:
                raise SequenceStoreError("invalid stored sequence")
            if record["catalog_type"] not in {"FULL", "DELTA"} or not isinstance(record["payload_sha256"], str) or not re.fullmatch(r"[a-f0-9]{64}", record["payload_sha256"]):
                raise SequenceStoreError("invalid stored sequence metadata")
        return state

    def _atomic_write(self, state: dict[str, Any]) -> None:
        encoded = json.dumps(state, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        if len(encoded) > MAX_STATE_BYTES:
            raise SequenceStoreError("sequence store exceeds its size limit")
        fd, temp_name = tempfile.mkstemp(prefix=f".{self.path.name}.", dir=self.path.parent, text=False)
        temp_path = Path(temp_name)
        try:
            os.fchmod(fd, 0o600)
            with os.fdopen(fd, "wb") as stream:
                stream.write(encoded)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp_path, self.path)
            _fsync_directory(self.path.parent)
        except OSError as exc:
            try:
                temp_path.unlink(missing_ok=True)
            except OSError:
                pass
            raise SequenceStoreError("atomic sequence-store write failed") from exc

    def _atomic_marker(self) -> None:
        fd, temp_name = tempfile.mkstemp(prefix=f".{self.marker_path.name}.", dir=self.marker_path.parent, text=False)
        temp_path = Path(temp_name)
        try:
            os.fchmod(fd, 0o600)
            with os.fdopen(fd, "wb") as stream:
                stream.write(_MARKER)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp_path, self.marker_path)
            _fsync_directory(self.marker_path.parent)
        except OSError as exc:
            try:
                temp_path.unlink(missing_ok=True)
            except OSError:
                pass
            raise SequenceStoreError("atomic sequence-store marker write failed") from exc

    def accept(self, verified: VerifiedEnvelope, *, sequence: int, catalog_type: str, base_sequence: int | None, sequence_namespace: str) -> SequenceReceipt:
        if not isinstance(sequence, int) or isinstance(sequence, bool) or sequence < 1:
            raise SequenceStoreError("sequence must be a positive integer")
        if catalog_type not in {"FULL", "DELTA"}:
            raise SequenceStoreError("catalog_type must be FULL or DELTA")
        if catalog_type == "DELTA" and (not isinstance(base_sequence, int) or isinstance(base_sequence, bool)):
            raise SequenceStoreError("DELTA requires base_sequence")
        if catalog_type == "FULL" and base_sequence is not None:
            raise SequenceStoreError("FULL cannot contain base_sequence")
        scope = _scope_key(verified.environment, verified.flow, sequence_namespace)
        with self._locked():
            state = self._read_state()
            previous = state["records"].get(scope)
            if previous is not None:
                if sequence <= previous["sequence"]:
                    raise SequenceReplayError("sequence is not strictly newer")
                if catalog_type == "DELTA" and base_sequence != previous["sequence"]:
                    raise SequenceConflictError("DELTA base_sequence does not match stored sequence")
            elif catalog_type == "DELTA" and base_sequence != 0:
                raise SequenceConflictError("first DELTA must declare base_sequence 0")
            state["records"][scope] = {
                "sequence": sequence,
                "catalog_type": catalog_type,
                "payload_sha256": verified.payload_sha256,
            }
            self._atomic_write(state)
            if previous is None and not self.marker_path.exists():
                self._atomic_marker()
        return SequenceReceipt(verified.environment, verified.flow, sequence_namespace, sequence, catalog_type, verified.payload_sha256)

    def snapshot(self) -> MappingProxyType:
        with self._locked():
            state = self._read_state()
        return MappingProxyType({
            key: MappingProxyType(dict(value)) for key, value in state["records"].items()
        })


def accept_envelope(
    raw: bytes | str | dict[str, Any],
    registry: TrustRegistry,
    store: SequenceStore,
    *,
    now=None,
    max_clock_skew_seconds: int = 0,
) -> SequenceReceipt:
    """Verify cryptography and policy first, then atomically accept its sequence."""
    verified = verify_envelope(raw, registry, now=now, max_clock_skew_seconds=max_clock_skew_seconds)
    try:
        sequence = verified.payload["sequence"]
        catalog_type = verified.payload["catalog_type"]
        base_sequence = verified.payload.get("base_sequence")
    except (KeyError, AttributeError) as exc:
        raise SequenceStoreError("signed payload lacks sequence metadata") from exc
    key = registry.get(verified.key_id)
    if key is None:
        raise EnvelopeVerificationError("trust key disappeared after verification")
    namespace = key.sequence_namespace or key.key_id
    return store.accept(
        verified,
        sequence=sequence,
        catalog_type=catalog_type,
        base_sequence=base_sequence,
        sequence_namespace=namespace,
    )
