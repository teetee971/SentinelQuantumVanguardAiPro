"""Sentinel Wangiri risk engine.

Local-first clients may call this optional cloud enrichment service. The engine never
claims to prove caller identity: telephone numbers and network verification signals
can be spoofed. Raw phone numbers are not persisted by this module.
"""

from __future__ import annotations

import hashlib
import hmac
import os
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


async def _global_rate_limit(app: FastAPI) -> None:
    client = getattr(app.state, "redis", None)
    limit = int(os.getenv("GLOBAL_RATE_LIMIT_PER_MINUTE", "120"))
    if client is None or limit <= 0:
        return
    key = f"api:rate:v1:{int(time.time()) // 60}"
    try:
        count = await client.incr(key)
        if count == 1:
            await client.expire(key, 120)
        if count > limit:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Capacité gratuite momentanément atteinte. Réessayez dans une minute.",
            )
    except HTTPException:
        raise
    except (RedisError, TimeoutError):
        # Risk evaluation remains available without cloud reputation.
        return


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis_url = os.getenv("REDIS_URL")
    app.state.redis = None
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
    return {"status": "ready", "redis": "connected"}


@app.post("/v1/evaluate-call")
async def evaluate_call(meta: CallMetadata, request: Request) -> dict[str, Any]:
    await _global_rate_limit(request.app)

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


@app.post("/v1/report-call", status_code=status.HTTP_202_ACCEPTED)
async def report_call(
    report: CallReport,
    request: Request,
    x_report_key: Annotated[str | None, Header()] = None,
) -> dict[str, str]:
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
    dedupe_key = f"phone:report:dedupe:v1:{nonce_hash}"
    try:
        accepted = await client.set(dedupe_key, "1", ex=86_400, nx=True)
        if not accepted:
            return {"status": "duplicate"}
        key = f"phone:spam:v2:{fingerprint}"
        now = int(time.time())
        pipe = client.pipeline(transaction=True)
        pipe.hincrby(key, "signals", 1)
        pipe.hincrby(key, f"category:{report.category.value}", 1)
        pipe.hset(key, mapping={"last_seen": now})
        pipe.expire(key, 180 * 86_400)
        await pipe.execute()
    except (RedisError, TimeoutError) as exc:
        raise HTTPException(status_code=503, detail="Redis indisponible") from exc
    return {"status": "accepted"}
