"""STIC v1.1 signed-envelope verification.

Signed bytes are:

    SENTINEL-STIC-V1.1-ENVELOPE + b"\\n" +
    canonical(header without key_id) + b"\\n" + canonical(payload)

The key identifier is deliberately not part of those bytes.  It is instead bound
by the explicit trust registry and authorization checks below.  This preserves the
existing STIC separation between cryptographic content and key authorization while
rejecting unknown, revoked, cross-environment, cross-flow, and over-capable keys.
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from types import MappingProxyType
from typing import Any, Mapping

from .crypto import CryptoVerificationError, load_p256_public_key, verify_p256_sha256_p1363
from .jcs import JcsError, canonicalize, parse_json

STIC_DOMAIN = "SENTINEL-STIC-V1.1-ENVELOPE"
MAX_PAYLOAD_BYTES = 1_048_576
_KEY_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
_UTC_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$")
_REQUIRED_HEADER = frozenset({
    "version", "domain", "environment", "flow", "capabilities", "issued_at",
    "valid_from", "expires_at", "payload_sha256", "payload_size", "key_id",
})
_ALLOWED_HEADER = _REQUIRED_HEADER


class EnvelopeVerificationError(ValueError):
    """Envelope failed a structural, authorization, temporal, or crypto check."""


def _freeze(value: Any) -> Any:
    if isinstance(value, dict):
        return MappingProxyType({key: _freeze(child) for key, child in value.items()})
    if isinstance(value, list):
        return tuple(_freeze(child) for child in value)
    return value


def _utc(value: Any, field: str) -> datetime:
    if not isinstance(value, str) or not _UTC_RE.fullmatch(value):
        raise EnvelopeVerificationError(f"{field}: UTC timestamp required")
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise EnvelopeVerificationError(f"{field}: invalid timestamp") from exc


def _require_text(value: Any, field: str, maximum: int = 256) -> str:
    if not isinstance(value, str) or not value or len(value) > maximum:
        raise EnvelopeVerificationError(f"{field}: invalid text")
    return value


@dataclass(frozen=True)
class TrustKey:
    key_id: str
    environment: str
    flows: frozenset[str]
    capabilities: frozenset[str]
    public_key: bytes
    revoked: bool = False


class TrustRegistry:
    def __init__(self, keys: Mapping[str, TrustKey]):
        normalized: dict[str, TrustKey] = {}
        for key_id, key in keys.items():
            if key_id != key.key_id or not _KEY_ID_RE.fullmatch(key_id):
                raise ValueError("invalid or inconsistent trust key id")
            if not key.environment or not key.flows or not key.capabilities:
                raise ValueError("trust key must declare environment, flows, and capabilities")
            if not isinstance(key.public_key, bytes) or len(key.public_key) != 65 or key.public_key[0] != 4:
                raise ValueError("trust key must contain an uncompressed P-256 point")
            try:
                load_p256_public_key(key.public_key)
            except CryptoVerificationError as exc:
                raise ValueError("trust key contains an invalid P-256 point") from exc
            normalized[key_id] = key
        self._keys = MappingProxyType(normalized)

    def authorize(self, key_id: str, environment: str, flow: str, capabilities: frozenset[str]) -> TrustKey:
        key = self._keys.get(key_id)
        if key is None:
            raise EnvelopeVerificationError("unknown key")
        if key.revoked:
            raise EnvelopeVerificationError("revoked key")
        if key.environment != environment:
            raise EnvelopeVerificationError("key/environment mismatch")
        if flow not in key.flows:
            raise EnvelopeVerificationError("key/flow not authorized")
        if not capabilities.issubset(key.capabilities):
            raise EnvelopeVerificationError("capability not authorized")
        return key


@dataclass(frozen=True)
class VerifiedEnvelope:
    header: Mapping[str, Any]
    payload: Mapping[str, Any]
    canonical_payload: bytes
    payload_sha256: str
    key_id: str
    environment: str
    flow: str


def _parse_envelope(raw: bytes | str | Mapping[str, Any]) -> dict[str, Any]:
    try:
        value = parse_json(raw) if isinstance(raw, (bytes, str)) else raw
    except (JcsError, TypeError) as exc:
        raise EnvelopeVerificationError("invalid envelope JSON") from exc
    if not isinstance(value, dict) or set(value) != {"header", "payload", "signature"}:
        raise EnvelopeVerificationError("envelope must contain exactly header, payload, signature")
    return value


def _validate_header(header: Any) -> dict[str, Any]:
    if not isinstance(header, dict) or set(header) != _REQUIRED_HEADER:
        raise EnvelopeVerificationError("header fields are not exact")
    if header["version"] != "1.1" or header["domain"] != STIC_DOMAIN:
        raise EnvelopeVerificationError("unsupported STIC version or domain")
    environment = _require_text(header["environment"], "environment", 64)
    flow = _require_text(header["flow"], "flow", 128)
    key_id = _require_text(header["key_id"], "key_id", 128)
    if not _KEY_ID_RE.fullmatch(key_id):
        raise EnvelopeVerificationError("invalid key_id")
    capabilities = header["capabilities"]
    if not isinstance(capabilities, list) or not capabilities or any(not isinstance(item, str) or not item for item in capabilities):
        raise EnvelopeVerificationError("invalid capabilities")
    if len(set(capabilities)) != len(capabilities) or capabilities != sorted(capabilities):
        raise EnvelopeVerificationError("capabilities must be unique and sorted")
    digest = header["payload_sha256"]
    if not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest):
        raise EnvelopeVerificationError("invalid payload_sha256")
    size = header["payload_size"]
    if not isinstance(size, int) or isinstance(size, bool) or size < 0 or size > MAX_PAYLOAD_BYTES:
        raise EnvelopeVerificationError("invalid payload_size")
    issued = _utc(header["issued_at"], "issued_at")
    valid_from = _utc(header["valid_from"], "valid_from")
    expires = _utc(header["expires_at"], "expires_at")
    if issued > valid_from or valid_from > expires or issued == expires:
        raise EnvelopeVerificationError("invalid envelope time ordering")
    return header


def _signed_bytes(header: Mapping[str, Any], payload: Any) -> bytes:
    unsigned_header = {key: value for key, value in header.items() if key != "key_id"}
    try:
        return STIC_DOMAIN.encode("ascii") + b"\n" + canonicalize(unsigned_header) + b"\n" + canonicalize(payload, max_bytes=MAX_PAYLOAD_BYTES)
    except JcsError as exc:
        raise EnvelopeVerificationError("cannot canonicalize signed content") from exc


def verify_envelope(
    raw: bytes | str | Mapping[str, Any],
    registry: TrustRegistry,
    *,
    now: datetime | None = None,
    max_clock_skew_seconds: int = 0,
) -> VerifiedEnvelope:
    """Verify one envelope; this function has no durable replay/rollback state."""
    if not isinstance(max_clock_skew_seconds, int) or isinstance(max_clock_skew_seconds, bool) or max_clock_skew_seconds < 0:
        raise EnvelopeVerificationError("max_clock_skew_seconds must be a non-negative integer")
    envelope = _parse_envelope(raw)
    header = _validate_header(envelope["header"])
    payload = envelope["payload"]
    if not isinstance(payload, dict):
        raise EnvelopeVerificationError("payload must be an object")
    try:
        canonical_payload = canonicalize(payload, max_bytes=MAX_PAYLOAD_BYTES)
    except JcsError as exc:
        raise EnvelopeVerificationError("invalid payload") from exc
    if len(canonical_payload) != header["payload_size"]:
        raise EnvelopeVerificationError("payload size mismatch")
    digest = hashlib.sha256(canonical_payload).hexdigest()
    if digest != header["payload_sha256"]:
        raise EnvelopeVerificationError("payload digest mismatch")
    capabilities = frozenset(header["capabilities"])
    key = registry.authorize(header["key_id"], header["environment"], header["flow"], capabilities)
    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None or current.utcoffset() != timezone.utc.utcoffset(current):
        raise EnvelopeVerificationError("now must be timezone-aware UTC")
    issued = _utc(header["issued_at"], "issued_at")
    valid_from = _utc(header["valid_from"], "valid_from")
    expires = _utc(header["expires_at"], "expires_at")
    from datetime import timedelta
    skew = timedelta(seconds=max_clock_skew_seconds)
    if issued > current + skew or valid_from > current + skew:
        raise EnvelopeVerificationError("envelope is from the future")
    if current < valid_from - skew or current >= expires:
        raise EnvelopeVerificationError("envelope is not currently valid")
    try:
        verify_p256_sha256_p1363(key.public_key, _signed_bytes(header, payload), envelope["signature"])
    except (CryptoVerificationError, TypeError) as exc:
        raise EnvelopeVerificationError("signature verification failed") from exc
    return VerifiedEnvelope(
        header=_freeze(header),
        payload=_freeze(payload),
        canonical_payload=canonical_payload,
        payload_sha256=digest,
        key_id=key.key_id,
        environment=header["environment"],
        flow=header["flow"],
    )
