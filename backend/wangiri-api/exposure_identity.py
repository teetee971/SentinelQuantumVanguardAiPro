from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import os
from dataclasses import dataclass
from typing import Any

import jwt


@dataclass(frozen=True)
class ExposureIdentitySettings:
    auth_jwks_url: str = ""
    auth_issuer: str = ""
    auth_audience: str = ""

    @classmethod
    def from_env(cls) -> "ExposureIdentitySettings":
        return cls(
            auth_jwks_url=os.getenv("EXPOSURE_AUTH_JWKS_URL", ""),
            auth_issuer=os.getenv("EXPOSURE_AUTH_ISSUER", ""),
            auth_audience=os.getenv("EXPOSURE_AUTH_AUDIENCE", ""),
        )

    def missing_configuration(self) -> list[str]:
        missing: list[str] = []
        for name, value in (
            ("EXPOSURE_AUTH_JWKS_URL", self.auth_jwks_url),
            ("EXPOSURE_AUTH_ISSUER", self.auth_issuer),
            ("EXPOSURE_AUTH_AUDIENCE", self.auth_audience),
        ):
            if not value:
                missing.append(name)
        return missing


def derive_subject_token(subject: str) -> str | None:
    """Derive a stable opaque subject token without exposing the Auth0 `sub` claim."""
    pepper = os.getenv("EXPOSURE_HASH_PEPPER")
    if not pepper or not isinstance(subject, str) or not subject:
        return None
    digest = hmac.new(
        pepper.encode("utf-8"),
        f"sentinel-exposure-auth-subject-v1\0{subject}".encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return base64.urlsafe_b64encode(digest).decode("ascii").rstrip("=")


class OidcExposureIdentityVerifier:
    def __init__(
        self,
        settings: ExposureIdentitySettings,
        *,
        jwks_client: Any | None = None,
    ) -> None:
        self._settings = settings
        self._jwks = jwks_client or jwt.PyJWKClient(
            settings.auth_jwks_url,
            cache_jwk_set=True,
        )

    async def verify(self, authorization: str | None) -> dict[str, str] | None:
        if not authorization or not authorization.startswith("Bearer "):
            return None
        token = authorization[7:].strip()
        if not token:
            return None
        try:
            signing_key = await asyncio.to_thread(
                self._jwks.get_signing_key_from_jwt,
                token,
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
        return {"subject": subject}
