import os

os.environ.setdefault("EXPOSURE_HASH_PEPPER", "exposure-test-pepper")

from fastapi import FastAPI
from fastapi.testclient import TestClient

from collective_exposure_user import create_user_exposure_router
from exposure_identity import ExposureIdentitySettings, derive_subject_token


class FakeIdentityVerifier:
    async def verify(self, authorization):
        if authorization == "Bearer valid-token":
            return {"subject": "auth0|user-123"}
        return None


def test_derived_subject_token_is_stable_opaque_and_schema_safe():
    first = derive_subject_token("auth0|user-123")
    second = derive_subject_token("auth0|user-123")
    assert first == second
    assert first is not None
    assert 43 <= len(first) <= 128
    assert "auth0" not in first
    assert "user-123" not in first
    assert all(ch.isalnum() or ch in "_-" for ch in first)


def test_identity_settings_fail_closed_when_oidc_metadata_is_missing(monkeypatch):
    monkeypatch.delenv("EXPOSURE_AUTH_JWKS_URL", raising=False)
    monkeypatch.delenv("EXPOSURE_AUTH_ISSUER", raising=False)
    monkeypatch.delenv("EXPOSURE_AUTH_AUDIENCE", raising=False)
    assert ExposureIdentitySettings.from_env().missing_configuration() == [
        "EXPOSURE_AUTH_JWKS_URL",
        "EXPOSURE_AUTH_ISSUER",
        "EXPOSURE_AUTH_AUDIENCE",
    ]


def test_user_lookup_requires_bearer_identity_and_never_accepts_subject_token():
    app = FastAPI()
    app.state.redis = None
    app.include_router(create_user_exposure_router(identity_verifier=FakeIdentityVerifier()))

    with TestClient(app) as client:
        missing = client.post(
            "/v1/intelligence/exposures/lookup-user",
            json={"indicators": [{"indicator_type": "DOMAIN", "value": "example.com"}]},
        )
        assert missing.status_code == 401

        forged = client.post(
            "/v1/intelligence/exposures/lookup-user",
            headers={"Authorization": "Bearer valid-token"},
            json={
                "subject_token": "A" * 43,
                "indicators": [{"indicator_type": "DOMAIN", "value": "example.com"}],
            },
        )
        assert forged.status_code == 422

        accepted = client.post(
            "/v1/intelligence/exposures/lookup-user",
            headers={"Authorization": "Bearer valid-token"},
            json={"indicators": [{"indicator_type": "DOMAIN", "value": "example.com"}]},
        )
        assert accepted.status_code == 200
        body = accepted.json()
        assert body["match_state"] == "UNAVAILABLE"
        assert body["matches"] == []
        assert body["enforcement_allowed"] is False


def test_user_report_route_exists_and_requires_auth_before_runtime_access():
    app = FastAPI()
    app.state.redis = None
    app.include_router(create_user_exposure_router(identity_verifier=FakeIdentityVerifier()))

    with TestClient(app) as client:
        response = client.post(
            "/v1/intelligence/exposures/report-user",
            json={
                "indicator": {"indicator_type": "DOMAIN", "value": "example.com"},
                "channel": "WEB",
                "client_nonce": "0123456789abcdef",
            },
        )
        assert response.status_code == 401
