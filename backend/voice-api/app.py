from __future__ import annotations

import asyncio
import hashlib
import json
import os
import re
import time
import uuid
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Protocol

import jwt
import redis.asyncio as redis
from fastapi import FastAPI, Header, HTTPException, Request, status
from pydantic import BaseModel, Field
from redis.exceptions import RedisError


MAX_TOKEN_TTL_SECONDS = 120
ROOM_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


@dataclass(frozen=True)
class Settings:
    enabled: bool = False
    livekit_url: str = ""
    livekit_api_key: str = ""
    livekit_api_secret: str = ""
    redis_url: str = ""
    auth_jwks_url: str = ""
    auth_issuer: str = ""
    auth_audience: str = ""
    entitlement_claim: str = "sentinel_voice_entitled"
    token_ttl_seconds: int = MAX_TOKEN_TTL_SECONDS
    rate_limit_per_minute: int = 30

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            enabled=os.getenv("VOICE_SERVICE_ENABLED", "false").lower() == "true",
            livekit_url=os.getenv("LIVEKIT_URL", ""),
            livekit_api_key=os.getenv("LIVEKIT_API_KEY", ""),
            livekit_api_secret=os.getenv("LIVEKIT_API_SECRET", ""),
            redis_url=os.getenv("VOICE_REDIS_URL", os.getenv("REDIS_URL", "")),
            auth_jwks_url=os.getenv("VOICE_AUTH_JWKS_URL", ""),
            auth_issuer=os.getenv("VOICE_AUTH_ISSUER", ""),
            auth_audience=os.getenv("VOICE_AUTH_AUDIENCE", ""),
            entitlement_claim=os.getenv(
                "VOICE_ENTITLEMENT_CLAIM", "sentinel_voice_entitled"
            ),
            token_ttl_seconds=int(
                os.getenv("VOICE_TOKEN_TTL_SECONDS", str(MAX_TOKEN_TTL_SECONDS))
            ),
            rate_limit_per_minute=int(
                os.getenv("VOICE_RATE_LIMIT_PER_MINUTE", "30")
            ),
        )

    def missing_configuration(
        self,
        *,
        custom_identity_verifier: bool,
        custom_token_issuer: bool,
    ) -> list[str]:
        if not self.enabled:
            return []
        missing: list[str] = []
        for name, value in (
            ("LIVEKIT_URL", self.livekit_url),
            ("VOICE_REDIS_URL", self.redis_url),
        ):
            if not value:
                missing.append(name)
        if not custom_token_issuer:
            for name, value in (
                ("LIVEKIT_API_KEY", self.livekit_api_key),
                ("LIVEKIT_API_SECRET", self.livekit_api_secret),
            ):
                if not value:
                    missing.append(name)
        if not custom_identity_verifier:
            for name, value in (
                ("VOICE_AUTH_JWKS_URL", self.auth_jwks_url),
                ("VOICE_AUTH_ISSUER", self.auth_issuer),
                ("VOICE_AUTH_AUDIENCE", self.auth_audience),
            ):
                if not value:
                    missing.append(name)
        if not self.livekit_url.startswith("wss://"):
            missing.append("LIVEKIT_URL must use wss://")
        if not 1 <= self.token_ttl_seconds <= MAX_TOKEN_TTL_SECONDS:
            missing.append("VOICE_TOKEN_TTL_SECONDS must be between 1 and 120")
        if self.rate_limit_per_minute < 1:
            missing.append("VOICE_RATE_LIMIT_PER_MINUTE must be positive")
        return missing


class SessionDestination(BaseModel):
    type: str
    room_id: str | None = Field(default=None, min_length=1, max_length=128)


class SessionClient(BaseModel):
    device_id: str = Field(min_length=1, max_length=256)
    app_version: str = Field(min_length=1, max_length=64)
    platform: str = Field(min_length=1, max_length=32)


class CreateSessionRequest(BaseModel):
    channel: str
    destination: SessionDestination
    client: SessionClient


class IdentityVerifier(Protocol):
    async def verify(self, authorization: str | None) -> dict[str, Any] | None: ...


class TokenIssuer(Protocol):
    def issue(self, *, identity: str, room: str, ttl_seconds: int) -> str: ...


class OidcIdentityVerifier:
    def __init__(self, settings: Settings):
        self._settings = settings
        self._jwks = jwt.PyJWKClient(settings.auth_jwks_url, cache_jwk_set=True)

    async def verify(self, authorization: str | None) -> dict[str, Any] | None:
        if not authorization or not authorization.startswith("Bearer "):
            return None
        token = authorization[7:].strip()
        if not token:
            return None
        try:
            signing_key = await asyncio.to_thread(
                self._jwks.get_signing_key_from_jwt, token
            )
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256", "ES256"],
                audience=self._settings.auth_audience,
                issuer=self._settings.auth_issuer,
                options={"require": ["sub", "exp", "iat"]},
            )
        except (jwt.PyJWTError, ValueError, OSError):
            return None

        subject = claims.get("sub")
        if not isinstance(subject, str) or not subject:
            return None
        device_ids = claims.get("device_ids", claims.get("device_id", []))
        if isinstance(device_ids, str):
            device_ids = [device_ids]
        if not isinstance(device_ids, list) or not all(
            isinstance(item, str) and item for item in device_ids
        ):
            device_ids = []
        return {
            "subject": subject,
            "device_ids": device_ids,
            "entitled": claims.get(self._settings.entitlement_claim) is True,
            "revoked": claims.get("sentinel_voice_revoked") is True,
        }


class LiveKitTokenIssuer:
    def __init__(self, settings: Settings):
        self._settings = settings

    def issue(self, *, identity: str, room: str, ttl_seconds: int) -> str:
        now = int(time.time())
        claims = {
            "iss": self._settings.livekit_api_key,
            "sub": identity,
            "nbf": now,
            "iat": now,
            "exp": now + ttl_seconds,
            "jti": str(uuid.uuid4()),
            "video": {
                "roomJoin": True,
                "room": room,
                "canPublish": True,
                "canSubscribe": True,
            },
        }
        return jwt.encode(
            claims,
            self._settings.livekit_api_secret,
            algorithm="HS256",
        )


def _hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _room_name(room_id: str) -> str:
    if not ROOM_ID_PATTERN.fullmatch(room_id):
        raise HTTPException(status_code=422, detail="Invalid voice destination")
    return f"sentinel-ptt-{_hash(f'v1:{room_id}')[:32]}"


def _authorization_required(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Authentication required")
    return authorization


async def _check_rate_limit(client: Any, subject: str, limit: int) -> None:
    bucket = int(time.time() // 60)
    key = f"voice:rate:v1:{_hash(subject)}:{bucket}"
    count = await client.incr(key)
    if count == 1:
        await client.expire(key, 60)
    if count > limit:
        raise HTTPException(status_code=429, detail="Voice rate limit exceeded")


async def _is_revoked(client: Any, subject: str, device_id: str) -> bool:
    subject_key = f"voice:revoked:subject:{_hash(subject)}"
    device_key = f"voice:revoked:device:{_hash(device_id)}"
    return bool(await client.get(subject_key) or await client.get(device_key))


def _principal_device_ids(principal: dict[str, Any]) -> list[str]:
    device_ids = principal.get("device_ids", principal.get("device_id", []))
    if isinstance(device_ids, str):
        return [device_ids]
    return [item for item in device_ids if isinstance(item, str)]


def _expires_at_iso(ttl_seconds: int) -> str:
    return (
        datetime.fromtimestamp(time.time() + ttl_seconds, tz=timezone.utc)
        .isoformat()
        .replace("+00:00", "Z")
    )


def create_app(
    *,
    settings: Settings | None = None,
    redis_client: Any | None = None,
    identity_verifier: IdentityVerifier | None = None,
    token_issuer: TokenIssuer | None = None,
) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    resolved_identity = identity_verifier or (
        OidcIdentityVerifier(resolved_settings)
        if resolved_settings.auth_jwks_url
        else None
    )
    resolved_issuer = token_issuer or (
        LiveKitTokenIssuer(resolved_settings)
        if resolved_settings.livekit_api_key and resolved_settings.livekit_api_secret
        else None
    )
    configuration_error = resolved_settings.missing_configuration(
        custom_identity_verifier=identity_verifier is not None,
        custom_token_issuer=token_issuer is not None,
    )

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if app.state.voice_redis is None and resolved_settings.redis_url:
            app.state.voice_redis = redis.from_url(
                resolved_settings.redis_url,
                decode_responses=True,
                socket_connect_timeout=1.5,
                socket_timeout=1.0,
                health_check_interval=30,
                max_connections=20,
            )
            app.state.voice_owned_redis = True
        try:
            yield
        finally:
            if app.state.voice_owned_redis and app.state.voice_redis is not None:
                await app.state.voice_redis.aclose()

    app = FastAPI(
        title="Sentinel Quantum Vanguard — Voice Session Service",
        version="1.0.0",
        docs_url=None if resolved_settings.enabled else "/docs",
        redoc_url=None if resolved_settings.enabled else "/redoc",
        lifespan=lifespan,
    )
    app.state.voice_settings = resolved_settings
    app.state.voice_redis = redis_client
    app.state.voice_identity = resolved_identity
    app.state.voice_issuer = resolved_issuer
    app.state.voice_configuration_error = configuration_error
    app.state.voice_owned_redis = False

    def require_runtime(request: Request) -> Any:
        if (
            not resolved_settings.enabled
            or app.state.voice_configuration_error
            or app.state.voice_identity is None
            or app.state.voice_issuer is None
        ):
            raise HTTPException(status_code=503, detail="Voice service unavailable")
        client = getattr(request.app.state, "voice_redis", None)
        if client is None:
            raise HTTPException(status_code=503, detail="Voice service unavailable")
        return client

    async def verify_principal(
        request: Request,
        authorization: str | None,
        device_id: str,
    ) -> tuple[Any, dict[str, Any]]:
        client = require_runtime(request)
        principal = await app.state.voice_identity.verify(
            _authorization_required(authorization)
        )
        if not principal:
            raise HTTPException(status_code=401, detail="Authentication required")
        subject = principal.get("subject")
        try:
            revoked = await _is_revoked(client, subject, device_id)
        except (RedisError, TimeoutError, OSError) as exc:
            raise HTTPException(
                status_code=503, detail="Voice service unavailable"
            ) from exc
        if (
            not isinstance(subject, str)
            or not subject
            or principal.get("revoked")
            or not principal.get("entitled")
            or device_id not in _principal_device_ids(principal)
            or revoked
        ):
            raise HTTPException(status_code=403, detail="Voice access denied")
        try:
            await _check_rate_limit(
                client, subject, resolved_settings.rate_limit_per_minute
            )
        except (RedisError, TimeoutError, OSError) as exc:
            raise HTTPException(
                status_code=503, detail="Voice service unavailable"
            ) from exc
        return client, principal

    @app.get("/health/live", include_in_schema=False)
    async def live() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/health/ready", include_in_schema=False)
    async def ready(request: Request) -> dict[str, str]:
        client = require_runtime(request)
        try:
            await client.ping()
        except (RedisError, TimeoutError, OSError):
            raise HTTPException(status_code=503, detail="Voice service unavailable")
        return {"status": "ready", "transport": "livekit", "ptt": "enabled"}

    @app.post("/v1/voice/sessions", status_code=status.HTTP_201_CREATED)
    async def create_session(
        body: CreateSessionRequest,
        request: Request,
        authorization: str | None = Header(default=None),
        idempotency_key: str | None = Header(default=None),
    ) -> dict[str, Any]:
        if not idempotency_key or len(idempotency_key) < 16 or len(idempotency_key) > 256:
            raise HTTPException(status_code=400, detail="Idempotency-Key required")
        if body.channel != "PTT_ROOM" or body.client.platform != "android":
            raise HTTPException(status_code=422, detail="Unsupported voice session")
        if body.destination.type != "sentinel_room" or not body.destination.room_id:
            raise HTTPException(status_code=403, detail="Destination unavailable")

        client, principal = await verify_principal(
            request, authorization, body.client.device_id
        )
        subject = principal["subject"]
        idempotency_hash = _hash(f"{subject}:{idempotency_key}")
        idempotency_record_key = f"voice:idempotency:v1:{idempotency_hash}"
        request_fingerprint = _hash(
            json.dumps(body.model_dump(mode="json"), sort_keys=True, separators=(",", ":"))
        )
        try:
            existing = await client.get(idempotency_record_key)
        except (RedisError, TimeoutError, OSError) as exc:
            raise HTTPException(status_code=503, detail="Voice service unavailable") from exc
        if existing:
            record = json.loads(existing)
            if record["request_fingerprint"] != request_fingerprint:
                raise HTTPException(status_code=409, detail="Idempotency-Key reused")
            return record["response"]

        session_id = str(uuid.uuid4())
        room = _room_name(body.destination.room_id)
        token = app.state.voice_issuer.issue(
            identity=subject,
            room=room,
            ttl_seconds=resolved_settings.token_ttl_seconds,
        )
        response = {
            "session_id": session_id,
            "transport": {
                "server_url": resolved_settings.livekit_url,
                "access_token": token,
                "expires_at": _expires_at_iso(resolved_settings.token_ttl_seconds),
            },
            "media": {"voice_transform_required": True, "ptt_required": True},
        }
        record = {
            "request_fingerprint": request_fingerprint,
            "response": response,
        }
        session_record = {
            "subject": subject,
            "device_id": body.client.device_id,
            "session_id": session_id,
            "status": "open",
            "room": room,
        }
        try:
            stored = await client.set(
                idempotency_record_key,
                json.dumps(record, separators=(",", ":")),
                ex=resolved_settings.token_ttl_seconds,
                nx=True,
            )
            session_key = f"voice:session:v1:{session_id}"
            await client.set(
                session_key,
                json.dumps(session_record, separators=(",", ":")),
                ex=resolved_settings.token_ttl_seconds + 60,
            )
        except (RedisError, TimeoutError, OSError) as exc:
            raise HTTPException(status_code=503, detail="Voice service unavailable") from exc

        if not stored:
            replay = await client.get(idempotency_record_key)
            if replay:
                replay_record = json.loads(replay)
                if replay_record["request_fingerprint"] != request_fingerprint:
                    raise HTTPException(status_code=409, detail="Idempotency-Key reused")
                return replay_record["response"]
        return response

    @app.post("/v1/voice/sessions/{session_id}/close")
    async def close_session(
        session_id: str,
        request: Request,
        authorization: str | None = Header(default=None),
        idempotency_key: str | None = Header(default=None),
    ) -> dict[str, str]:
        if not idempotency_key or len(idempotency_key) < 16 or len(idempotency_key) > 256:
            raise HTTPException(status_code=400, detail="Idempotency-Key required")
        client = require_runtime(request)
        principal = await app.state.voice_identity.verify(
            _authorization_required(authorization)
        )
        if not principal or not isinstance(principal.get("subject"), str):
            raise HTTPException(status_code=401, detail="Authentication required")
        session_key = f"voice:session:v1:{session_id}"
        closed_key = f"voice:closed:v1:{session_id}"
        try:
            record = await client.get(session_key)
            if not record:
                if await client.get(closed_key):
                    return {"session_id": session_id, "status": "closed"}
                raise HTTPException(status_code=404, detail="Voice session not found")
            session = json.loads(record)
            if (
                session["subject"] != principal["subject"]
                or session["device_id"] not in _principal_device_ids(principal)
            ):
                raise HTTPException(status_code=403, detail="Voice access denied")
            await client.set(closed_key, "1", ex=3600)
            await client.delete(session_key)
        except HTTPException:
            raise
        except (RedisError, TimeoutError, OSError) as exc:
            raise HTTPException(status_code=503, detail="Voice service unavailable") from exc
        return {"session_id": session_id, "status": "closed"}

    return app


app = create_app()
