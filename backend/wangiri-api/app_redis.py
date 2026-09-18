"""Sentinel Wangiri risk engine.

Local-first clients may call this optional cloud enrichment service. The engine never
claims to prove caller identity: telephone numbers and network verification signals
can be spoofed. Raw phone numbers are not persisted by this module.
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import os
import secrets
import time
from contextlib import asynccontextmanager
from enum import StrEnum
from typing import Annotated, Any

import phonenumbers
import redis.asyncio as aioredis
from fastapi import FastAPI, Header, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from phonenumbers import NumberParseException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from redis.exceptions import RedisError


class Action(StrEnum):
    ALLOW = "ALLOW"
    FLAG_SUSPICIOUS = "FLAG_SUSPICIOUS"
    BLOCK = "BLOCK"


class VerificationStatus(StrEnum):
    VERIFIED = "VERIFIED"
    NOT_VERIFIED = "NOT_VERIFIED"
    FAILED = "FAILED"
    UNKNOWN = "UNKNOWN"


class ReportCategory(StrEnum):
    WANGIRI = "WANGIRI"
    SPOOFING = "SPOOFING"
    PREMIUM_RATE = "PREMIUM_RATE"
    ROBOCALL = "ROBOCALL"
    OTHER = "OTHER"


class CallMetadata(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    caller_number: Annotated[str, Field(min_length=3, max_length=32)]
    recipient_country: Annotated[str, Field(pattern=r"^[A-Za-z]{2}$")]
    ring_duration_ms: Annotated[int | None, Field(default=None, ge=0, le=300_000)]
    verification_status: VerificationStatus = VerificationStatus.UNKNOWN

    @field_validator("recipient_country")
    @classmethod
    def uppercase_country(cls, value: str) -> str:
        return value.upper()


class CallReport(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    caller_number: Annotated[str, Field(min_length=3, max_length=32)]
    recipient_country: Annotated[str, Field(pattern=r"^[A-Za-z]{2}$")]
    category: ReportCategory
    client_nonce: Annotated[str, Field(min_length=16, max_length=128)]

    @field_validator("recipient_country")
    @classmethod
    def uppercase_country(cls, value: str) -> str:
        return value.upper()


def _env_set(name: str) -> set[str]:
    return {item.strip().upper() for item in os.getenv(name, "").split(",") if item.strip()}


def _allowed_origins() -> list[str]:
    return [item.strip() for item in os.getenv("CORS_ALLOWED_ORIGINS", "").split(",") if item.strip()]


def _phone_fingerprint(e164: str) -> str | None:
    pepper = os.getenv("PHONE_HASH_PEPPER")
    if not pepper:
        return None
    return hmac.new(pepper.encode(), e164.encode(), hashlib.sha256).hexdigest()


def _public_report_secret() -> str | None:
    configured = os.getenv("PUBLIC_REPORT_PEPPER")
    base_secret = configured or os.getenv("PHONE_HASH_PEPPER")
    if not base_secret:
        return None
    return hmac.new(
        base_secret.encode(),
        b"sentinel-public-report-v1",
        hashlib.sha256,
    ).hexdigest()


def _parse_number(raw_number: str, recipient_country: str) -> tuple[Any, str, str | None]:
    parsed = phonenumbers.parse(raw_number, recipient_country)
    if not phonenumbers.is_possible_number(parsed) or not phonenumbers.is_valid_number(parsed):
        raise ValueError("invalid_phone_number")
    e164 = phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.E164)
    return parsed, e164, phonenumbers.region_code_for_number(parsed)


def _risk_decision(
    *,
    caller_country: str | None,
    recipient_country: str,
    ring_duration_ms: int | None,
    verification_status: VerificationStatus,
    signals: int,
    burst_count: int,
) -> tuple[int, Action, list[str]]:
    score = 0
    reasons: list[str] = []
    international = bool(caller_country and caller_country != recipient_country)

    if international:
        score += 15
        reasons.append("Appel international inattendu : indicatif à vérifier")

    if caller_country and caller_country in _env_set("HIGH_RISK_COUNTRIES"):
        score += 20
        reasons.append("Indicatif présent dans la liste de vigilance configurable")

    short_ring = ring_duration_ms is not None and 0 < ring_duration_ms < 2_500
    if short_ring:
        score += 35
        reasons.append(f"Sonnerie très courte ({ring_duration_ms} ms), compatible avec un Wangiri")

    if short_ring and international:
        score += 20
        reasons.append("Combinaison appel international + sonnerie unique/courte")

    if verification_status is VerificationStatus.FAILED:
        score += 30
        reasons.append("Échec du contrôle d'authenticité fourni par le réseau")
    elif verification_status is VerificationStatus.NOT_VERIFIED:
        score += 10
        reasons.append("Numéro non authentifié par le réseau")
    elif verification_status is VerificationStatus.VERIFIED:
        score = max(0, score - 10)
        reasons.append("Numéro authentifié par le réseau (ce signal ne garantit pas l'identité)")

    if signals >= 50:
        score += 45
        reasons.append(f"Réputation communautaire très dégradée ({signals} signalements)")
    elif signals >= 10:
        score += 30
        reasons.append(f"Réputation communautaire dégradée ({signals} signalements)")
    elif signals >= 3:
        score += 15
        reasons.append(f"Plusieurs signalements communautaires ({signals})")

    if burst_count >= 10:
        score += 20
        reasons.append("Vague d'appels récente observée")
    elif burst_count >= 4:
        score += 10
        reasons.append("Répétition récente observée")

    score = min(score, 100)
    action = Action.BLOCK if score >= 80 else (
        Action.FLAG_SUSPICIOUS if score >= 50 else Action.ALLOW
    )
    return score, action, reasons


async def _redis_reputation(app: FastAPI, fingerprint: str | None) -> tuple[int, int, str]:
    client = getattr(app.state, "redis", None)
    if client is None or fingerprint is None:
        return 0, 0, "disabled"

    reputation_key = f"phone:spam:v2:{fingerprint}"
    burst_key = f"phone:burst:v1:{fingerprint}:{int(time.time()) // 300}"
    try:
        spam_data = await client.hgetall(reputation_key)
        burst_count = await client.incr(burst_key)
        if burst_count == 1:
            await client.expire(burst_key, 600)
        if spam_data:
            await client.hset(reputation_key, mapping={"last_seen": int(time.time())})
        return int(spam_data.get("signals", 0) or 0), int(burst_count), "available"
    except (RedisError, TimeoutError, ValueError):
        return 0, 0, "degraded"



_REPLAY_PROBE_SUCCESS_CACHE_SECONDS = 300
_REPLAY_PROBE_FAILURE_CACHE_SECONDS = 30
_REPLAY_PROBE_TTL_MS = 15_000


async def _redis_replay_guard_status(app: FastAPI) -> str:
    """Verify Redis SET NX PX semantics with an isolated, short-lived probe key."""
    client = getattr(app.state, "redis", None)
    if client is None:
        return "disabled"

    lock = getattr(app.state, "redis_replay_probe_lock", None)
    if lock is None:
        lock = asyncio.Lock()
        app.state.redis_replay_probe_lock = lock

    async with lock:
        now = time.monotonic()
        checked_at = getattr(app.state, "redis_replay_probe_checked_at", 0.0)
        cached = getattr(app.state, "redis_replay_probe_status", None)
        cache_seconds = (
            _REPLAY_PROBE_SUCCESS_CACHE_SECONDS
            if cached == "available"
            else _REPLAY_PROBE_FAILURE_CACHE_SECONDS
        )
        if cached and now - checked_at < cache_seconds:
            return cached

        key = f"health:replay:v1:{secrets.token_hex(16)}"
        result = "degraded"
        try:
            first = await client.set(key, "1", nx=True, px=_REPLAY_PROBE_TTL_MS)
            replay = await client.set(key, "2", nx=True, px=_REPLAY_PROBE_TTL_MS)
            if first in (True, "OK") and replay in (None, False):
                result = "available"
        except (RedisError, TimeoutError, ValueError):
            result = "degraded"
        finally:
            try:
                await client.delete(key)
            except (RedisError, TimeoutError, ValueError):
                result = "degraded"

        app.state.redis_replay_probe_status = result
        app.state.redis_replay_probe_checked_at = time.monotonic()
        return result


def _positive_int_env(name: str, default: int) -> int:
    try:
        return max(0, int(os.getenv(name, str(default))))
    except ValueError:
        return default


def _client_rate_fingerprint(request: Request) -> str:
    # request.client is resolved by the ASGI server. Never persist a raw IP address.
    host = request.client.host if request.client else "unknown"
    pepper = os.getenv("RATE_LIMIT_PEPPER") or os.getenv("PHONE_HASH_PEPPER") or "ephemeral"
    return hmac.new(pepper.encode(), host.encode(), hashlib.sha256).hexdigest()[:24]


_REPORT_NONCE_TTL_SECONDS = 86_400
_REPORTER_DEDUPE_TTL_SECONDS = 7 * 86_400
_REPUTATION_TTL_SECONDS = 180 * 86_400
_PENDING_REPORT_TTL_SECONDS = 30 * 86_400
_REPORT_LUA = """
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
  return 0
end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
redis.call('HSETNX', KEYS[3], 'first_seen', ARGV[3])
redis.call('HSET', KEYS[3], 'last_seen', ARGV[3])
redis.call('HINCRBY', KEYS[3], 'signals', 1)
redis.call('HINCRBY', KEYS[3], ARGV[4], 1)
redis.call('EXPIRE', KEYS[3], ARGV[5])
return 1
"""


def _reporter_dedupe_hash(
    request: Request, *, phone_fingerprint: str, category: ReportCategory, secret: str
) -> str:
    client_fingerprint = _client_rate_fingerprint(request)
    material = f"{client_fingerprint}:{phone_fingerprint}:{category.value}"
    return hmac.new(secret.encode(), material.encode(), hashlib.sha256).hexdigest()


async def _store_report_atomically(
    client: Any,
    *,
    nonce_hash: str,
    reporter_hash: str,
    phone_fingerprint: str,
    category: ReportCategory,
    now: int,
) -> bool:
    result = await client.eval(
        _REPORT_LUA,
        3,
        f"phone:report:dedupe:v2:{nonce_hash}",
        f"phone:report:reporter:v1:{reporter_hash}",
        f"phone:spam:v2:{phone_fingerprint}",
        str(_REPORT_NONCE_TTL_SECONDS),
        str(_REPORTER_DEDUPE_TTL_SECONDS),
        str(now),
        f"category:{category.value}",
        str(_REPUTATION_TTL_SECONDS),
    )
    return int(result) == 1


_PENDING_REPORT_LUA = """
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
  return 0
end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
redis.call('HSETNX', KEYS[3], 'first_seen', ARGV[3])
redis.call('HSET', KEYS[3], 'last_seen', ARGV[3])
redis.call('HINCRBY', KEYS[3], 'signals', 1)
redis.call('HINCRBY', KEYS[3], ARGV[4], 1)
redis.call('EXPIRE', KEYS[3], ARGV[5])
redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', ARGV[6])
redis.call('ZADD', KEYS[4], ARGV[3], ARGV[7])
return 1
"""


async def _store_pending_report_atomically(
    client: Any,
    *,
    nonce_hash: str,
    reporter_hash: str,
    phone_fingerprint: str,
    category: ReportCategory,
    now: int,
) -> bool:
    cutoff = now - _PENDING_REPORT_TTL_SECONDS
    result = await client.eval(
        _PENDING_REPORT_LUA,
        4,
        f"phone:community:pending:dedupe:v1:{nonce_hash}",
        f"phone:community:pending:reporter:v1:{reporter_hash}",
        f"phone:community:pending:v1:{phone_fingerprint}",
        "phone:community:moderation:v1",
        str(_REPORT_NONCE_TTL_SECONDS),
        str(_REPORTER_DEDUPE_TTL_SECONDS),
        str(now),
        f"category:{category.value}",
        str(_PENDING_REPORT_TTL_SECONDS),
        str(cutoff),
        phone_fingerprint,
    )
    return int(result) == 1


async def _rate_limit(
    request: Request,
    *,
    endpoint: str,
    per_client_env: str,
    per_client_default: int,
) -> None:
    client = getattr(request.app.state, "redis", None)
    per_client_limit = _positive_int_env(per_client_env, per_client_default)
    global_limit = _positive_int_env("GLOBAL_RATE_LIMIT_PER_MINUTE", 120)
    if client is None or (per_client_limit <= 0 and global_limit <= 0):
        return

    now = int(time.time())
    window = now // 60
    retry_after = 60 - (now % 60)
    fingerprint = _client_rate_fingerprint(request)
    counters: list[tuple[str, int]] = []
    if per_client_limit > 0:
        counters.append((f"api:rate:v2:{endpoint}:client:{fingerprint}:{window}", per_client_limit))
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
        # Evaluation stays available if the optional cloud limiter is degraded.
        return


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis_url = os.getenv("REDIS_URL")
    app.state.redis = None
    app.state.redis_replay_probe_lock = asyncio.Lock()
    app.state.redis_replay_probe_status = None
    app.state.redis_replay_probe_checked_at = 0.0
    if redis_url:
        # rediss:// enables TLS. Certificate verification remains enabled.
        app.state.redis = aioredis.from_url(
            redis_url,
            decode_responses=True,
            socket_connect_timeout=1.5,
            socket_timeout=1.0,
            health_check_interval=30,
            max_connections=10,
        )
    try:
        yield
    finally:
        if app.state.redis is not None:
            await app.state.redis.aclose()


app = FastAPI(
    title="Sentinel Quantum Vanguard AI Pro — Wangiri Engine",
    version="1.0.0",
    description="Évaluation explicable Wangiri/spoofing. Aucun score ne prouve l'identité.",
    lifespan=lifespan,
)

origins = _allowed_origins()
if origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=False,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type", "X-Report-Key"],
        max_age=600,
    )


@app.get("/health/live", include_in_schema=False)
async def live() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready", include_in_schema=False)
async def ready(request: Request) -> dict[str, str]:
    client = getattr(request.app.state, "redis", None)
    if client is None:
        raise HTTPException(status_code=503, detail="REDIS_URL non configurée")
    try:
        await client.ping()
    except (RedisError, TimeoutError) as exc:
        raise HTTPException(status_code=503, detail="Redis indisponible") from exc
    replay_guard = await _redis_replay_guard_status(request.app)
    if replay_guard != "available":
        raise HTTPException(status_code=503, detail="Anti-rejeu Redis non vérifié")
    return {"status": "ready", "redis": "connected", "replay_guard": replay_guard}


@app.post("/v1/evaluate-call")
async def evaluate_call(meta: CallMetadata, request: Request) -> dict[str, Any]:
    await _rate_limit(
        request,
        endpoint="evaluate-call",
        per_client_env="EVALUATE_RATE_LIMIT_PER_MINUTE",
        per_client_default=30,
    )

    try:
        _, e164, caller_country = _parse_number(meta.caller_number, meta.recipient_country)
    except (NumberParseException, ValueError):
        return {
            "caller_number": meta.caller_number,
            "risk_score": 80,
            "action": Action.BLOCK,
            "flags": ["Numéro impossible ou invalide"],
            "caller_country": None,
            "is_international": None,
            "community_intelligence": "not_queried",
            "warning": "Le score est une aide à la décision, pas une preuve de fraude.",
        }

    fingerprint = _phone_fingerprint(e164)
    signals, burst_count, redis_status = await _redis_reputation(request.app, fingerprint)
    score, action, reasons = _risk_decision(
        caller_country=caller_country,
        recipient_country=meta.recipient_country,
        ring_duration_ms=meta.ring_duration_ms,
        verification_status=meta.verification_status,
        signals=signals,
        burst_count=burst_count,
    )

    return {
        "caller_number": e164,
        "caller_country": caller_country,
        "is_international": bool(caller_country and caller_country != meta.recipient_country),
        "risk_score": score,
        "action": action,
        "flags": reasons,
        "signals": signals,
        "community_intelligence": redis_status,
        "warning": (
            "L'indicatif, le drapeau et même le numéro affiché peuvent être usurpés. "
            "Ne rappelez jamais un numéro inconnu sur la seule base de cet affichage."
        ),
    }


@app.post("/v1/report-call-public", status_code=status.HTTP_202_ACCEPTED)
async def report_call_public(
    report: CallReport,
    request: Request,
) -> dict[str, str]:
    await _rate_limit(
        request,
        endpoint="report-call-public",
        per_client_env="PUBLIC_REPORT_RATE_LIMIT_PER_MINUTE",
        per_client_default=3,
    )

    pending_secret = _public_report_secret()
    if not pending_secret:
        raise HTTPException(status_code=503, detail="Signalement public non configuré")

    try:
        _, e164, _ = _parse_number(report.caller_number, report.recipient_country)
    except (NumberParseException, ValueError) as exc:
        raise HTTPException(status_code=422, detail="Numéro invalide") from exc

    fingerprint = _phone_fingerprint(e164)
    client = getattr(request.app.state, "redis", None)
    if fingerprint is None or client is None:
        raise HTTPException(status_code=503, detail="File de modération indisponible")

    nonce_hash = hmac.new(
        pending_secret.encode(), report.client_nonce.encode(), hashlib.sha256
    ).hexdigest()
    reporter_hash = _reporter_dedupe_hash(
        request,
        phone_fingerprint=fingerprint,
        category=report.category,
        secret=pending_secret,
    )
    try:
        accepted = await _store_pending_report_atomically(
            client,
            nonce_hash=nonce_hash,
            reporter_hash=reporter_hash,
            phone_fingerprint=fingerprint,
            category=report.category,
            now=int(time.time()),
        )
    except (RedisError, TimeoutError, ValueError) as exc:
        raise HTTPException(status_code=503, detail="File de modération indisponible") from exc

    return {
        "status": "pending" if accepted else "duplicate",
        "effect_on_reputation": "none_pending_moderation",
    }


@app.post("/v1/report-call", status_code=status.HTTP_202_ACCEPTED)
async def report_call(
    report: CallReport,
    request: Request,
    x_report_key: Annotated[str | None, Header()] = None,
) -> dict[str, str]:
    await _rate_limit(
        request,
        endpoint="report-call",
        per_client_env="REPORT_RATE_LIMIT_PER_MINUTE",
        per_client_default=10,
    )

    expected_key = os.getenv("REPORT_API_KEY")
    if not expected_key or not x_report_key or not hmac.compare_digest(expected_key, x_report_key):
        raise HTTPException(status_code=401, detail="Signalement non autorisé")

    try:
        _, e164, _ = _parse_number(report.caller_number, report.recipient_country)
    except (NumberParseException, ValueError) as exc:
        raise HTTPException(status_code=422, detail="Numéro invalide") from exc

    fingerprint = _phone_fingerprint(e164)
    client = getattr(request.app.state, "redis", None)
    if fingerprint is None or client is None:
        raise HTTPException(status_code=503, detail="Stockage de réputation indisponible")

    nonce_hash = hmac.new(
        expected_key.encode(), report.client_nonce.encode(), hashlib.sha256
    ).hexdigest()
    reporter_hash = _reporter_dedupe_hash(
        request,
        phone_fingerprint=fingerprint,
        category=report.category,
        secret=expected_key,
    )
    try:
        accepted = await _store_report_atomically(
            client,
            nonce_hash=nonce_hash,
            reporter_hash=reporter_hash,
            phone_fingerprint=fingerprint,
            category=report.category,
            now=int(time.time()),
        )
    except (RedisError, TimeoutError, ValueError) as exc:
        raise HTTPException(status_code=503, detail="Redis indisponible") from exc
    return {"status": "accepted" if accepted else "duplicate"}
