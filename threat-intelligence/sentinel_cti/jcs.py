"""Bounded, strict JSON Canonicalization for STIC v1.1.

This module accepts JSON text/bytes so duplicate object names and malformed UTF-16
surrogates can be rejected before a signature is checked.  Python mappings are
accepted only as a convenience for callers that already parsed trusted data; they
cannot provide duplicate-key detection.
"""

from __future__ import annotations

import json
import math
import re
from typing import Any

MAX_DOCUMENT_BYTES = 1_048_576
MAX_DEPTH = 32
MAX_SAFE_INTEGER = 9_007_199_254_740_991
_NUMBER_RE = re.compile(r"^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$")


class JcsError(ValueError):
    """Input is not a bounded, unambiguous JCS value."""


def _reject_constant(value: str) -> None:
    raise JcsError(f"non-finite JSON number: {value}")


def _pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise JcsError(f"duplicate object key: {key!r}")
        result[key] = value
    return result


def _parse_int(value: str) -> int:
    parsed = int(value)
    if abs(parsed) > MAX_SAFE_INTEGER:
        raise JcsError("integer outside the ECMAScript safe range")
    return parsed


def _parse_float(value: str) -> float:
    if not _NUMBER_RE.fullmatch(value):
        raise JcsError("invalid JSON number")
    parsed = float(value)
    if not math.isfinite(parsed):
        raise JcsError("non-finite JSON number")
    return parsed


def parse_json(data: bytes | str, *, max_bytes: int = MAX_DOCUMENT_BYTES) -> Any:
    if isinstance(data, str):
        encoded = data.encode("utf-8", "strict")
    elif isinstance(data, bytes):
        encoded = data
    else:
        raise TypeError("JSON input must be bytes or str")
    if len(encoded) > max_bytes:
        raise JcsError("JSON document exceeds the configured size limit")
    if encoded.startswith(b"\xef\xbb\xbf"):
        raise JcsError("UTF-8 BOM is not accepted")
    try:
        value = json.loads(
            encoded.decode("utf-8", "strict"),
            object_pairs_hook=_pairs,
            parse_int=_parse_int,
            parse_float=_parse_float,
            parse_constant=_reject_constant,
        )
    except (UnicodeDecodeError, json.JSONDecodeError, OverflowError, RecursionError) as exc:
        raise JcsError("invalid UTF-8 JSON") from exc
    try:
        _validate(value, 0)
    except RecursionError as exc:
        raise JcsError("JSON nesting exceeds the configured depth limit") from exc
    return value


def _validate(value: Any, depth: int) -> None:
    if depth > MAX_DEPTH:
        raise JcsError("JSON nesting exceeds the configured depth limit")
    if isinstance(value, str):
        if any(0xD800 <= ord(char) <= 0xDFFF for char in value):
            raise JcsError("unpaired Unicode surrogate")
    elif isinstance(value, dict):
        for key, child in value.items():
            if not isinstance(key, str):
                raise JcsError("object key is not a string")
            _validate(child, depth + 1)
    elif isinstance(value, list):
        for child in value:
            _validate(child, depth + 1)
    elif isinstance(value, float):
        if not math.isfinite(value):
            raise JcsError("non-finite JSON number")
        if value.is_integer() and abs(value) > MAX_SAFE_INTEGER:
            raise JcsError("integer-valued number outside the ECMAScript safe range")
    elif isinstance(value, int) and abs(value) > MAX_SAFE_INTEGER:
        raise JcsError("integer outside the ECMAScript safe range")
    elif value is not None and not isinstance(value, (bool, int)):
        raise JcsError("unsupported JSON value")


def _escape_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def _number(value: int | float) -> str:
    if isinstance(value, int):
        if abs(value) > MAX_SAFE_INTEGER:
            raise JcsError("integer outside the ECMAScript safe range")
        return str(value)
    if not math.isfinite(value):
        raise JcsError("non-finite JSON number")
    if value == 0.0:
        return "0"
    text = repr(value).lower()
    if text.endswith(".0") and "e" not in text:
        return text[:-2]
    if "e" not in text:
        return text
    mantissa, exponent_text = text.split("e")
    exponent = int(exponent_text)
    digits = mantissa.replace(".", "")
    sign = ""
    if digits.startswith("-"):
        sign, digits = "-", digits[1:]
    decimal_position = (mantissa.lstrip("-").find(".") if "." in mantissa else len(mantissa.lstrip("-"))) + exponent
    if -6 <= exponent < 21:
        if decimal_position <= 0:
            return f"{sign}0.{('0' * -decimal_position)}{digits}"
        if decimal_position >= len(digits):
            return f"{sign}{digits}{('0' * (decimal_position - len(digits)))}"
        return f"{sign}{digits[:decimal_position]}.{digits[decimal_position:]}"
    normalized = digits[0] + (f".{digits[1:]}" if len(digits) > 1 else "")
    return f"{sign}{normalized}e{'+' if exponent >= 0 else ''}{exponent}"


def _canonical(value: Any) -> str:
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return _number(value)
    if isinstance(value, str):
        return _escape_string(value)
    if isinstance(value, list):
        return "[" + ",".join(_canonical(item) for item in value) + "]"
    if isinstance(value, dict):
        keys = sorted(value, key=lambda key: key.encode("utf-16-be", "surrogatepass"))
        return "{" + ",".join(f"{_escape_string(key)}:{_canonical(value[key])}" for key in keys) + "}"
    raise JcsError("unsupported JSON value")


def canonicalize(value: Any, *, max_bytes: int = MAX_DOCUMENT_BYTES) -> bytes:
    """Return RFC 8785-style canonical UTF-8 bytes for a bounded JSON value."""
    _validate(value, 0)
    encoded = _canonical(value).encode("utf-8", "strict")
    if len(encoded) > max_bytes:
        raise JcsError("canonical JSON exceeds the configured size limit")
    return encoded


def canonicalize_json(data: bytes | str, *, max_bytes: int = MAX_DOCUMENT_BYTES) -> bytes:
    return canonicalize(parse_json(data, max_bytes=max_bytes), max_bytes=max_bytes)
