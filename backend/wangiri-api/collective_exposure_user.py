from __future__ import annotations

import time
from typing import Annotated, Any, Protocol

from fastapi import APIRouter, Header, HTTPException, Request, status
from pydantic import BaseModel, ConfigDict, Field
from redis.exceptions import RedisError

from collective_exposure import (
    ExposureChannel,
    _EXPOSURE_TTL_SECONDS,
    _event_fingerprint,
    _nonce_fingerprint,
    _normalize_ref,
    _rate_limit,
    _read_matches,
    _record_fingerprint,
    _store_exposure,
    subject_fingerprint,
)
from collective_intel import IndicatorRef
from exposure_identity import (
    ExposureIdentitySettings,
    OidcExposureIdentityVerifier,
    derive_subject_token,
)


class ExposureIdentityVerifier(Protocol):
    async def verify(self, authorization: str | None) -> dict[str, Any] | None: ...


class UserExposureObservation(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    indicator: IndicatorRef
    channel: ExposureChannel
    client_nonce: Annotated[str, Field(min_length=16, max_length=128)]


class UserExposureLookup(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    indicators: Annotated[list[IndicatorRef], Field(min_length=1, max_length=50)]


def _default_identity_verifier() -> ExposureIdentityVerifier | None:
    settings = ExposureIdentitySettings.from_env()
    if settings.missing_configuration():
        return None
    return OidcExposureIdentityVerifier(settings)


async def _authenticated_subject_token(
    identity_verifier: ExposureIdentityVerifier | None,
    authorization: str | None,
) -> str:
    if identity_verifier is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Exposure identity unavailable",
        )
    principal = await identity_verifier.verify(authorization)
    if not principal:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
        )
    subject = principal.get("subject")
    if not isinstance(subject, str) or not subject:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
        )
    subject_token = derive_subject_token(subject)
    if not subject_token:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Exposure identity unavailable",
        )
    return subject_token


def create_user_exposure_router(
    *,
    identity_verifier: ExposureIdentityVerifier | None = None,
) -> APIRouter:
    resolved_identity = identity_verifier or _default_identity_verifier()
    router = APIRouter(
        prefix="/v1/intelligence/exposures",
        tags=["collective-exposure-user"],
    )

    @router.post("/report-user", status_code=status.HTTP_202_ACCEPTED)
    async def report_user_exposure(
        payload: UserExposureObservation,
        request: Request,
        authorization: Annotated[str | None, Header()] = None,
    ) -> dict[str, Any]:
        subject_token = await _authenticated_subject_token(
            resolved_identity,
            authorization,
        )
        await _rate_limit(
            request,
            endpoint="intel-exposure-report-user",
            env_name="INTEL_EXPOSURE_REPORT_RATE_LIMIT_PER_MINUTE",
            default=30,
        )

        subject_fp = subject_fingerprint(subject_token)
        nonce_fp = _nonce_fingerprint(payload.client_nonce)
        indicator_type, indicator_fp = _normalize_ref(payload.indicator)
        if not subject_fp or not nonce_fp:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Exposure intelligence non configurée",
            )

        record_fp = _record_fingerprint(
            subject_fp=subject_fp,
            indicator_type=indicator_type,
            indicator_fp=indicator_fp,
        )
        if not record_fp:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Exposure intelligence non configurée",
            )
        event_fp = _event_fingerprint(
            record_fp=record_fp,
            channel=payload.channel,
        )
        if not event_fp:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Exposure intelligence non configurée",
            )

        client = getattr(request.app.state, "redis", None)
        if client is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Exposure intelligence indisponible",
            )
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
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Exposure intelligence indisponible",
            ) from exc

        return {
            "status": "accepted" if accepted else "duplicate",
            "indicator_type": indicator_type,
            "indicator_fingerprint": indicator_fp,
            "channel": payload.channel,
            "storage": "fingerprints_only",
            "retention_ttl_ms": _EXPOSURE_TTL_SECONDS * 1_000,
            "enforcement_allowed": False,
        }

    @router.post("/lookup-user")
    async def lookup_user_exposure(
        payload: UserExposureLookup,
        request: Request,
        authorization: Annotated[str | None, Header()] = None,
    ) -> dict[str, Any]:
        subject_token = await _authenticated_subject_token(
            resolved_identity,
            authorization,
        )
        await _rate_limit(
            request,
            endpoint="intel-exposure-lookup-user",
            env_name="INTEL_EXPOSURE_LOOKUP_RATE_LIMIT_PER_MINUTE",
            default=30,
        )

        subject_fp = subject_fingerprint(subject_token)
        if not subject_fp:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Exposure intelligence non configurée",
            )
        indicators = [_normalize_ref(ref) for ref in payload.indicators]
        exposure_status, matches = await _read_matches(
            request.app,
            subject_fp=subject_fp,
            indicators=indicators,
        )
        if exposure_status != "available":
            match_state = "UNAVAILABLE"
        elif matches:
            match_state = "MATCH"
        else:
            match_state = "NO_MATCH"

        return {
            "exposure_intelligence": exposure_status,
            "matches": matches,
            "match_state": match_state,
            "retention_ttl_ms": _EXPOSURE_TTL_SECONDS * 1_000,
            "enforcement_allowed": False,
        }

    return router
