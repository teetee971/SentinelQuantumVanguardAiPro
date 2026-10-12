import json
import sys
import time
from datetime import datetime
from pathlib import Path

import jwt
from fastapi.testclient import TestClient
from redis.exceptions import RedisError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import (  # noqa: E402
    LiveKitTokenIssuer,
    Settings,
    _hash,
    create_app,
    demo_room_id_for_device,
)


class FakeRedis:
    def __init__(self):
        self.values = {}
        self.counters = {}

    async def ping(self):
        return True

    async def incr(self, key):
        self.counters[key] = self.counters.get(key, 0) + 1
        return self.counters[key]

    async def expire(self, key, seconds):
        return True

    async def set(self, key, value, ex=None, nx=False):
        if nx and key in self.values:
            return False
        self.values[key] = value
        return True

    async def get(self, key):
        return self.values.get(key)

    async def delete(self, key):
        self.values.pop(key, None)
        return 1


class BrokenRedis(FakeRedis):
    async def get(self, key):
        raise RedisError("redis unavailable")


class FakeIdentityVerifier:
    def __init__(self, principal=None):
        self.principal = principal

    async def verify(self, authorization):
        return self.principal


class FakeTokenIssuer:
    def __init__(self):
        self.calls = []

    def issue(self, *, identity, room, ttl_seconds):
        self.calls.append(
            {"identity": identity, "room": room, "ttl_seconds": ttl_seconds}
        )
        return "ephemeral-livekit-token"


def configured_settings():
    return Settings(
        enabled=True,
        livekit_url="wss://voice.example.test",
        redis_url="redis://voice.example.test/0",
        token_ttl_seconds=120,
    )


def valid_payload(room_id=None):
    room_id = room_id or demo_room_id_for_device("device-1")
    return {
        "channel": "PTT_ROOM",
        "destination": {"type": "sentinel_room", "room_id": room_id},
        "client": {
            "device_id": "device-1",
            "app_version": "1.0.0",
            "platform": "android",
        },
    }


def entitled_principal():
    return {
        "subject": "user-1",
        "device_id": "device-1",
        "entitled": True,
    }


def make_client(principal=entitled_principal()):
    issuer = FakeTokenIssuer()
    app = create_app(
        settings=configured_settings(),
        redis_client=FakeRedis(),
        identity_verifier=FakeIdentityVerifier(principal),
        token_issuer=issuer,
    )
    return TestClient(app), issuer


def test_disabled_service_never_issues_a_session():
    app = create_app(settings=Settings(enabled=False))

    with TestClient(app) as client:
        response = client.post(
            "/v1/voice/sessions",
            headers={"Authorization": "Bearer token", "Idempotency-Key": "key-123456789012"},
            json=valid_payload(),
        )

    assert response.status_code == 503
    assert response.json()["detail"] == "Voice service unavailable"


def test_malformed_numeric_configuration_stays_fail_closed(monkeypatch):
    monkeypatch.setenv("VOICE_SERVICE_ENABLED", "true")
    monkeypatch.setenv("VOICE_TOKEN_TTL_SECONDS", "not-a-number")
    monkeypatch.setenv("VOICE_RATE_LIMIT_PER_MINUTE", "not-a-number")

    settings = Settings.from_env()

    assert settings.token_ttl_seconds == 0
    assert settings.rate_limit_per_minute == 0
    assert settings.missing_configuration(
        custom_identity_verifier=True, custom_token_issuer=True
    )


def test_demo_room_id_matches_the_android_sha256_vector():
    assert demo_room_id_for_device("android-device-1") == (
        "demo-378710ccedce6ac9623900dbafac6ea6"
    )


def test_redis_failure_returns_unavailable_instead_of_server_error():
    app = create_app(
        settings=configured_settings(),
        redis_client=BrokenRedis(),
        identity_verifier=FakeIdentityVerifier(entitled_principal()),
        token_issuer=FakeTokenIssuer(),
    )

    with TestClient(app) as client:
        response = client.post(
            "/v1/voice/sessions",
            headers={"Authorization": "Bearer valid", "Idempotency-Key": "key-723456789012"},
            json=valid_payload(),
        )

    assert response.status_code == 503


def test_session_requires_authenticated_entitled_matching_device():
    client, issuer = make_client(principal=None)

    response = client.post(
        "/v1/voice/sessions",
        headers={"Authorization": "Bearer invalid", "Idempotency-Key": "key-123456789012"},
        json=valid_payload(),
    )

    assert response.status_code == 401
    assert issuer.calls == []

    client, issuer = make_client(principal={"subject": "user-1", "device_id": "device-1", "entitled": False})
    response = client.post(
        "/v1/voice/sessions",
        headers={"Authorization": "Bearer valid", "Idempotency-Key": "key-223456789012"},
        json=valid_payload(),
    )
    assert response.status_code == 403
    assert issuer.calls == []


def test_legacy_idempotency_replay_cannot_bypass_room_authorization():
    idempotency_key = "legacy-key-123456"
    payload = valid_payload("other-users-private-room")
    redis = FakeRedis()
    redis.values[
        f"voice:idempotency:v1:{_hash(f'user-1:{idempotency_key}')}"
    ] = json.dumps(
        {
            "request_fingerprint": _hash(
                json.dumps(payload, sort_keys=True, separators=(",", ":"))
            ),
            "response": {
                "session_id": "legacy-session",
                "transport": {"access_token": "legacy-token"},
            },
        }
    )
    issuer = FakeTokenIssuer()
    app = create_app(
        settings=configured_settings(),
        redis_client=redis,
        identity_verifier=FakeIdentityVerifier(entitled_principal()),
        token_issuer=issuer,
    )

    with TestClient(app) as client:
        response = client.post(
            "/v1/voice/sessions",
            headers={
                "Authorization": "Bearer valid",
                "Idempotency-Key": idempotency_key,
            },
            json=payload,
        )

    assert response.status_code == 403
    assert response.json()["detail"] == "Voice destination unavailable"
    assert issuer.calls == []


def test_legacy_valid_room_replay_is_not_reused_after_room_binding_change():
    idempotency_key = "legacy-valid-123456"
    payload = valid_payload()
    redis = FakeRedis()
    redis.values[
        f"voice:idempotency:v1:{_hash(f'user-1:{idempotency_key}') }"
    ] = json.dumps(
        {
            "request_fingerprint": _hash(
                json.dumps(payload, sort_keys=True, separators=(",", ":"))
            ),
            "response": {
                "session_id": "legacy-session",
                "transport": {"access_token": "legacy-v1-token"},
            },
        }
    )
    issuer = FakeTokenIssuer()
    app = create_app(
        settings=configured_settings(),
        redis_client=redis,
        identity_verifier=FakeIdentityVerifier(entitled_principal()),
        token_issuer=issuer,
    )

    with TestClient(app) as client:
        response = client.post(
            "/v1/voice/sessions",
            headers={
                "Authorization": "Bearer valid",
                "Idempotency-Key": idempotency_key,
            },
            json=payload,
        )

    assert response.status_code == 201
    assert response.json()["session_id"] != "legacy-session"
    assert response.json()["transport"]["access_token"] == "ephemeral-livekit-token"
    assert issuer.calls


def test_session_creation_is_idempotent_and_room_scoped():
    client, issuer = make_client()
    headers = {
        "Authorization": "Bearer valid",
        "Idempotency-Key": "key-323456789012",
    }

    first = client.post("/v1/voice/sessions", headers=headers, json=valid_payload())
    replay = client.post("/v1/voice/sessions", headers=headers, json=valid_payload())
    mismatch = client.post(
        "/v1/voice/sessions",
        headers=headers,
        json=valid_payload(room_id="another-group"),
    )

    assert first.status_code == 201
    assert replay.status_code == 201
    assert replay.json() == first.json()
    assert mismatch.status_code == 403
    assert mismatch.json()["detail"] == "Voice destination unavailable"
    assert len(issuer.calls) == 1
    assert issuer.calls[0]["identity"] == "user-1"
    assert issuer.calls[0]["ttl_seconds"] == 120
    assert issuer.calls[0]["room"].startswith("sentinel-ptt-")


def test_session_rejects_room_not_bound_to_authenticated_device():
    client, issuer = make_client()

    response = client.post(
        "/v1/voice/sessions",
        headers={
            "Authorization": "Bearer valid",
            "Idempotency-Key": "key-923456789012",
        },
        json=valid_payload(room_id="other-users-private-room"),
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "Voice destination unavailable"
    assert issuer.calls == []


def test_close_is_idempotent_and_prevents_reuse():
    client, _ = make_client()
    headers = {
        "Authorization": "Bearer valid",
        "Idempotency-Key": "key-423456789012",
    }
    created = client.post("/v1/voice/sessions", headers=headers, json=valid_payload())
    session_id = created.json()["session_id"]

    first = client.post(
        f"/v1/voice/sessions/{session_id}/close",
        headers={"Authorization": "Bearer valid", "Idempotency-Key": "close-123456789012"},
    )
    second = client.post(
        f"/v1/voice/sessions/{session_id}/close",
        headers={"Authorization": "Bearer valid", "Idempotency-Key": "close-223456789012"},
    )

    assert first.status_code == 200
    assert first.json() == {"session_id": session_id, "status": "closed"}
    assert second.status_code == 200
    assert second.json() == {"session_id": session_id, "status": "closed"}


def test_close_requires_the_device_that_created_the_session():
    redis_client = FakeRedis()
    verifier = FakeIdentityVerifier(entitled_principal())
    app = create_app(
        settings=configured_settings(),
        redis_client=redis_client,
        identity_verifier=verifier,
        token_issuer=FakeTokenIssuer(),
    )
    with TestClient(app) as client:
        created = client.post(
            "/v1/voice/sessions",
            headers={"Authorization": "Bearer valid", "Idempotency-Key": "key-623456789012"},
            json=valid_payload(),
        )
        verifier.principal = {
            "subject": "user-1",
            "device_id": "other-device",
            "entitled": True,
        }
        closed = client.post(
            f"/v1/voice/sessions/{created.json()['session_id']}/close",
            headers={"Authorization": "Bearer valid", "Idempotency-Key": "close-323456789012"},
        )

    assert closed.status_code == 403


def test_repeated_close_keeps_account_and_device_authorization():
    redis_client = FakeRedis()
    verifier = FakeIdentityVerifier(entitled_principal())
    app = create_app(
        settings=configured_settings(),
        redis_client=redis_client,
        identity_verifier=verifier,
        token_issuer=FakeTokenIssuer(),
    )
    with TestClient(app) as client:
        created = client.post(
            "/v1/voice/sessions",
            headers={"Authorization": "Bearer valid", "Idempotency-Key": "key-823456789012"},
            json=valid_payload(),
        )
        session_id = created.json()["session_id"]
        first = client.post(
            f"/v1/voice/sessions/{session_id}/close",
            headers={"Authorization": "Bearer valid", "Idempotency-Key": "close-423456789012"},
        )
        verifier.principal = {
            "subject": "other-user",
            "device_id": "other-device",
            "entitled": True,
        }
        repeated = client.post(
            f"/v1/voice/sessions/{session_id}/close",
            headers={"Authorization": "Bearer valid", "Idempotency-Key": "close-523456789012"},
        )

    assert first.status_code == 200
    assert repeated.status_code == 403


def test_livekit_token_is_room_scoped_and_capped_at_120_seconds():
    settings = Settings(
        enabled=True,
        livekit_url="wss://voice.example.test",
        livekit_api_key="api-key",
        livekit_api_secret="api-secret-with-at-least-32-bytes-long",
        redis_url="redis://voice.example.test/0",
    )

    token = LiveKitTokenIssuer(settings).issue(
        identity="user-1", room="sentinel-ptt-room", ttl_seconds=120
    )
    claims = jwt.decode(
        token, "api-secret-with-at-least-32-bytes-long", algorithms=["HS256"]
    )

    assert claims["iss"] == "api-key"
    assert claims["sub"] == "user-1"
    assert claims["exp"] - claims["iat"] == 120
    assert claims["video"] == {
        "roomJoin": True,
        "room": "sentinel-ptt-room",
        "canPublish": True,
        "canSubscribe": True,
    }


def test_response_expiry_matches_the_ephemeral_token_window():
    client, _ = make_client()

    response = client.post(
        "/v1/voice/sessions",
        headers={"Authorization": "Bearer valid", "Idempotency-Key": "key-523456789012"},
        json=valid_payload(),
    )

    expires_at = datetime.fromisoformat(
        response.json()["transport"]["expires_at"].replace("Z", "+00:00")
    ).timestamp()
    remaining = expires_at - time.time()
    assert 100 < remaining <= 120
