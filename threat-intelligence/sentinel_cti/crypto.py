"""Strict ECDSA P-256 / SHA-256 / IEEE-P1363 verification."""

from __future__ import annotations

import base64
import binascii
import re

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature

P256_ORDER = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
P256_HALF_ORDER = P256_ORDER // 2
P1363_SIGNATURE_SIZE = 64
_B64URL_RE = re.compile(r"^[A-Za-z0-9_-]+$")


class CryptoVerificationError(ValueError):
    """Cryptographic input is malformed or not verified."""


def decode_base64url(value: str, *, expected_size: int | None = None) -> bytes:
    if not isinstance(value, str) or not value or "=" in value or not _B64URL_RE.fullmatch(value):
        raise CryptoVerificationError("invalid unpadded base64url")
    if len(value) % 4 == 1:
        raise CryptoVerificationError("invalid base64url length")
    try:
        decoded = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
    except (ValueError, binascii.Error) as exc:
        raise CryptoVerificationError("invalid base64url") from exc
    if expected_size is not None and len(decoded) != expected_size:
        raise CryptoVerificationError("unexpected decoded size")
    return decoded


def encode_base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def decode_p1363_signature(value: str | bytes) -> tuple[int, int]:
    raw = decode_base64url(value, expected_size=P1363_SIGNATURE_SIZE) if isinstance(value, str) else value
    if not isinstance(raw, bytes) or len(raw) != P1363_SIGNATURE_SIZE:
        raise CryptoVerificationError("P1363 signature must be exactly 64 bytes")
    r = int.from_bytes(raw[:32], "big")
    s = int.from_bytes(raw[32:], "big")
    if not 1 <= r < P256_ORDER or not 1 <= s < P256_ORDER:
        raise CryptoVerificationError("ECDSA scalar out of range")
    if s > P256_HALF_ORDER:
        raise CryptoVerificationError("high-S ECDSA signature rejected")
    return r, s


def load_p256_public_key(raw: bytes):
    if not isinstance(raw, bytes) or len(raw) != 65 or raw[0] != 0x04:
        raise CryptoVerificationError("public key must be an uncompressed P-256 point")
    try:
        key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), raw)
    except (ValueError, TypeError) as exc:
        raise CryptoVerificationError("invalid P-256 public point") from exc
    if not isinstance(key.curve, ec.SECP256R1):
        raise CryptoVerificationError("wrong public-key curve")
    return key


def verify_p256_sha256_p1363(public_key: bytes, message: bytes, signature: str | bytes) -> None:
    key = load_p256_public_key(public_key)
    r, s = decode_p1363_signature(signature)
    try:
        key.verify(encode_dss_signature(r, s), message, ec.ECDSA(hashes.SHA256()))
    except InvalidSignature as exc:
        raise CryptoVerificationError("ECDSA signature verification failed") from exc


def public_key_from_pem(pem: bytes) -> bytes:
    try:
        key = serialization.load_pem_public_key(pem)
    except (ValueError, TypeError) as exc:
        raise CryptoVerificationError("invalid public-key PEM") from exc
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise CryptoVerificationError("PEM key is not ECDSA P-256")
    return key.public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
