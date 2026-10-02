"""Privacy-preserving Collective Defense exposure evidence.

This module provides a server-to-server foundation for retroactive exposure matching.
It deliberately does not expose per-user/device history to public clients, does not
persist raw indicator values, and does not store message bodies or other payload
content. The caller must provide a high-entropy opaque subject token; raw device
identifiers, phone numbers and email addresses are not valid subject identities.
"""

from __future__ import annotations

import hashlib
import hmac
import os
import time
from enum import StrEnum
from typing import Annotated, Any

from fastapi import APIRouter, Header, HTTPException, Request, status
from pydantic import BaseModel, ConfigDict, Field
from redis.exceptions import RedisError

from collective_intel import (
    IndicatorRef,
    IndicatorType,
    indicator_fingerprint,
    normalize_indicator,
)


class ExposureChannel(StrEnum):
    CALL = "CALL"
    SMS = "SMS"
    MMS = "MMS"
    EMAIL = "EMAIL"
    WEB = "WEB"
    SOCIAL = "SOCIAL"
    FILE = "FILE"


_SubjectToken = Annotated[
    str,
    Field(
        min_length=43,
        max_length=128,
        pattern=r"^[A-Za-z0-9_-]+$",
        description="High-entropy opaque token; callers must not use raw device/account identifiers.",
    ),
]


class ExposureObservation(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    subject_token: _SubjectToken
    indicator: IndicatorRef
    channel: ExposureChannel
    client_nonce: Annotated[str, Field(min_length=16, max_length=128)]


class ExposureLookup(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    subject_token: _SubjectToken
    indicators: Annotated[list[IndicatorRef], Field(min_length=1, max_length=50)]


_EXPOSURE_TTL_SECONDS = 30 * 86_400
_EXPOSURE_NONCE_TTL_SECONDS = 86_400
_EXPOSURE_OBSERVATION_DEDUPE_SECONDS = 3_600


def _positive_int_env(name: str, default: int) -> int:
    try:
        return max(0, int(os.getenv(name, str(default))))
    except ValueError:
        return default


def _exposure_base_secret() -> str | None:
    value = os.getenv("EXPOSURE_HASH_PEPPER")
    return value if value else None


def _exposure_subject_secret() -> bytes | None:
    base = _exposure_base_secret()
    if not base:
        return None
    return hmac.new(
        base.encode(),
        b"sentinel-exposure-subject-v1",
        hashlib.sha256,
    ).digest()


def subject_fingerprint(subject_token: str) -> str | None:
    secret = _exposure_subject_secret()
    if secret is None:
        return None
    return hmac.new(secret, subject_token.encode(), hashlib.sha256).hexdigest()


def _record_fingerprint(
    *,
    subject_fp: str,
    indicator_type: IndicatorType,
    indicator_fp: str,
) -> str | None:
    secret = _exposure_subject_secret()
    if secret is None:
        return None
    material = (
        f"sentinel-exposure-record-v1\0{subject_fp}\0"
        f"{indicator_type.value}\0{indicator_fp}"
    ).encode()
    return hmac.new(secret, material, hashlib.sha256).hexdigest()


def _event_fingerprint(
    *,
    record_fp: str,
    channel: ExposureChannel,
) -> str | None:
    secret = _exposure_subject_secret()
    if secret is None:
        return None
    material = f"sentinel-exposure-event-v1\0{record_fp}\0{channel.value}".encode()
    return hmac.new(secret, material, hashlib.sha256).hexdigest()


def _nonce_fingerprint(client_nonce: str) -> str | None:
    secret = _exposure_subject_secret()
    if secret is None:
        return None
    return hmac.new(secret, client_nonce.encode(), hashlib.sha256).hexdigest()


def _client_rate_fingerprint(request: Request) -> str:
    host = request.client.host if request.client else "unknown"
    pepper = os.getenv("RATE_LIMIT_PEPPER") or _exposure_base_secret() or "ephemeral"
    return hmac.new(pepper.encode(), host.encode(), hashlib.sha256).hexdigest()[:24]


async def _rate_limit(
    request: Request,
    *,
    endpoint: str,
    env_name: str,
    default: int,
) -> None:
    client = getattr(request.app.state, "redis", None)
    per_client_limit = _positive_int_env(env_name, default)
    global_limit = _positive_int_env("GLOBAL_RATE_LIMIT_PER_MINUTE", 120)
    if client is None or (per_client_limit <= 0 and global_limit <= 0):
        return

    now = int(time.time())
    window = now // 60
    retry_after = 60 - (now % 60)
    client_fp = _client_rate_fingerprint(request)
    counters: list[tuple[str, int]] = []
    if per_client_limit > 0:
        counters.append(
            (f"api:rate:v2:{endpoint}:client:{client_fp}:{window}", per_client_limit)
        )
    if global_limit > 0:
        counters.append((f"api:rate:v2:{endpoint}:global:{window}", global_limit))

    try:
        pipe = client.pipeline(transaction=True)
        for key, _ in counters:
            pipe.incr(key)
            pipe.expire(key, 120)
        results = await pipe.execute()
        counts = [int(results[index * 2]) for index in range(len(counters))]
        if any(count > limit for count, (_, limit) in zip(counts, counters, strict=True)):
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Limite de requêtes atteinte. Réessayez plus tard.",
                headers={"Retry-After": str(retry_after)},
            )
    except HTTPException:
        raise
    except (RedisError, TimeoutError, ValueError):
        # Exposure endpoints themselves fail closed if Redis is unavailable.
        return


_EXPOSURE_REPORT_LUA = """
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])

if redis.call('EXISTS', KEYS[2]) == 1 then
  return 0
end
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])

local cutoff = tonumber(ARGV[3]) - tonumber(ARGV[5])
redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', cutoff)
redis.call('ZADD', KEYS[3], ARGV[3], ARGV[4])
redis.call('EXPIRE', KEYS[3], ARGV[5])
return 1
"""


def _observation_member(channel: ExposureChannel, nonce_fp: str) -> str:
    return f"{channel.value}:{nonce_fp}"


async def _store_exposure(
    client: Any,
    *,
    record_fp: str,
    channel: ExposureChannel,
    nonce_fp: str,
    event_fp: str,
    now: int,
) -> bool:
    result = await client.eval(
        _EXPOSURE_REPORT_LUA,
        3,
        f"intel:exposure:dedupe:v1:{nonce_fp}",
        f"intel:exposure:window:v1:{event_fp}",
        f"intel:exposure:v1:{record_fp}",
        str(_EXPOSURE_NONCE_TTL_SECONDS),
        str(_EXPOSURE_OBSERVATION_DEDUPE_SECONDS),
        str(now),
        _observation_member(channel, nonce_fp),
        str(_EXPOSURE_TTL_SECONDS),
    )
    return int(result) == 1


def _summarize_observations(
    observations: list[tuple[str, float]],
) -> tuple[list[str], int, int, int] | None:
    if not observations:
        return None

    counts: dict[str, int] = {}
    timestamps: list[int] = []
    for member, score in observations:
        channel_name, separator, nonce_fp = member.partition(":")
        if (
            separator != ":"
            or len(nonce_fp) != 64
            or any(ch not in "0123456789abcdef" for ch in nonce_fp.lower())
        ):
            raise ValueError("invalid_exposure_member")
        channel = ExposureChannel(channel_name)
        timestamp = int(float(score))
        if timestamp <= 0:
            raise ValueError("invalid_exposure_timestamp")
        counts[channel.value] = counts.get(channel.value, 0) + 1
        timestamps.append(timestamp)

    channels = [
        name for name, _ in sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    ]
    return channels, len(observations), min(timestamps), max(timestamps)


async def _read_matches(
    app: Any,
    *,
    subject_fp: str,
    indicators: list[tuple[IndicatorType, str]],
) -> tuple[str, list[dict[str, Any]]]:
    client = getattr(app.state, "redis", None)
    if client is None:
        return "disabled", []

    now = int(time.time())
    cutoff = now - _EXPOSURE_TTL_SECONDS
    try:
        pipe = client.pipeline(transaction=False)
        for indicator_type, indicator_fp in indicators:
            record_fp = _record_fingerprint(
                subject_fp=subject_fp,
                indicator_type=indicator_type,
                indicator_fp=indicator_fp,
            )
            if not record_fp:
                return "degraded", []
            key = f"intel:exposure:v1:{record_fp}"
            pipe.zrangebyscore(key, cutoff + 1, "+inf", withscores=True)
            pipe.ttl(key)
        results = await pipe.execute()

        matches: list[dict[str, Any]] = []
        for index, (indicator_type, indicator_fp) in enumerate(indicators):
            observations = results[index * 2]
            ttl_seconds = int(results[index * 2 + 1])
            if not observations:
                if ttl_seconds == -1:
                    return "degraded", []
                continue
            if ttl_seconds <= 0:
                return "degraded", []

            summary = _summarize_observations(observations)
            if summary is None:
                continue
            channels, signals, first_seen, last_seen = summary
            if first_seen <= cutoff or last_seen > now:
                return "degraded", []

            matches.append(
                {
                    "indicator_type": indicator_type,
                    "indicator_fingerprint": indicator_fp,
                    "channels": channels,
                    "signals": signals,
                    "first_seen": first_seen,
                    "last_seen": last_seen,
                    "remaining_ttl_ms": (
                        first_seen + _EXPOSURE_TTL_SECONDS - now
                    ) * 1_000,
                }
            )
        return "available", matches
    except (RedisError, TimeoutError, ValueError, TypeError):
        return "degraded", []


def _require_exposure_key(x_exposure_key: str | None) -> None:
    expected = os.getenv("EXPOSURE_API_KEY")
    if (
        not expected
        or not x_exposure_key
        or not hmac.compare_digest(expected, x_exposure_key)
    ):
        raise HTTPException(status_code=401, detail="Exposure intelligence non autorisée")


def _normalize_ref(ref: IndicatorRef) -> tuple[IndicatorType, str]:
    try:
        normalized = normalize_indicator(ref.indicator_type, ref.value)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    fingerprint = indicator_fingerprint(ref.indicator_type, normalized)
    if not fingerprint:
        raise HTTPException(status_code=503, detail="Exposure intelligence non configurée")
    return ref.indicator_type, fingerprint


def create_collective_exposure_router() -> APIRouter:
    router = APIRouter(prefix="/v1/intelligence/exposures", tags=["collective-exposure"])

    @router.post("/report", status_code=status.HTTP_202_ACCEPTED)
    async def report_exposure(
        payload: ExposureObservation,
        request: Request,
        x_exposure_key: Annotated[str | None, Header()] = None,
    ) -> dict[str, Any]:
        _require_exposure_key(x_exposure_key)
        await _rate_limit(
            request,
            endpoint="intel-exposure-report",
            env_name="INTEL_EXPOSURE_REPORT_RATE_LIMIT_PER_MINUTE",
            default=30,
        )

        subject_fp = subject_fingerprint(payload.subject_token)
        nonce_fp = _nonce_fingerprint(payload.client_nonce)
        indicator_type, indicator_fp = _normalize_ref(payload.indicator)
        if not subject_fp or not nonce_fp:
            raise HTTPException(status_code=503, detail="Exposure intelligence non configurée")

        record_fp = _record_fingerprint(
            subject_fp=subject_fp,
            indicator_type=indicator_type,
            indicator_fp=indicator_fp,
        )
        if not record_fp:
            raise HTTPException(status_code=503, detail="Exposure intelligence non configurée")

        event_fp = _event_fingerprint(
            record_fp=record_fp,
            channel=payload.channel,
        )
        if not event_fp:
            raise HTTPException(status_code=503, detail="Exposure intelligence non configurée")

        client = getattr(request.app.state, "redis", None)
        if client is None:
            raise HTTPException(status_code=503, detail="Exposure intelligence indisponible")

        try:
            accepted = await _store_exposure(
                client,
                record_fp=record_fp,
                channel=payload.channel,
                nonce_fp=nonce_fp,
                event_fp=event_fp,
                now=int(time.time()),
            )
        except (RedisError, TimeoutError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="Exposure intelligence indisponible") from exc

        return {
            "status": "accepted" if accepted else "duplicate",
            "indicator_type": indicator_type,
            "indicator_fingerprint": indicator_fp,
            "channel": payload.channel,
            "storage": "fingerprints_only",
            "retention_ttl_ms": _EXPOSURE_TTL_SECONDS * 1_000,
            "enforcement_allowed": False,
        }

    @router.post("/lookup")
    async def lookup_exposure(
        payload: ExposureLookup,
        request: Request,
        x_exposure_key: Annotated[str | None, Header()] = None,
    ) -> dict[str, Any]:
        _require_exposure_key(x_exposure_key)
        await _rate_limit(
            request,
            endpoint="intel-exposure-lookup",
            env_name="INTEL_EXPOSURE_LOOKUP_RATE_LIMIT_PER_MINUTE",
            default=30,
        )

        subject_fp = subject_fingerprint(payload.subject_token)
        if not subject_fp:
            raise HTTPException(status_code=503, detail="Exposure intelligence non configurée")

        unique: dict[tuple[str, str], tuple[IndicatorType, str]] = {}
        for ref in payload.indicators:
            indicator_type, indicator_fp = _normalize_ref(ref)
            unique[(indicator_type.value, indicator_fp)] = (indicator_type, indicator_fp)

        exposure_status, matches = await _read_matches(
            request.app,
            subject_fp=subject_fp,
            indicators=list(unique.values()),
        )
        return {
            "exposure_intelligence": exposure_status,
            "queried_count": len(unique),
            "matches": matches,
            "match_state": (
                "UNAVAILABLE"
                if exposure_status != "available"
                else ("MATCHES" if matches else "NONE")
            ),
            "retention_ttl_ms": _EXPOSURE_TTL_SECONDS * 1_000,
            "enforcement_allowed": False,
            "warning": (
                "Une exposition observée indique seulement qu'un sujet pseudonymisé a "
                "rencontré un indicateur technique. Elle ne prouve ni compromission, "
                "ni identité, ni intention."
            ),
        }

    return router
