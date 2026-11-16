from pathlib import Path
import sys

from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import Settings, _hash, create_app, demo_room_id_for_device  # noqa: E402


class FakeRedis:
    def __init__(self, values=None):
        self.values = dict(values or {})
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


class FakeIdentityVerifier:
    async def verify(self, authorization):
        return {
            "subject": "user-1",
            "device_id": "device-1",
            "entitled": True,
        }


class FakeTokenIssuer:
    def issue(self, *, identity, room, ttl_seconds):
        return "ephemeral-livekit-token"


def configured_settings():
    return Settings(
        enabled=True,
        livekit_url="wss://voice.example.test",
        redis_url="redis://voice.example.test/0",
        token_ttl_seconds=120,
    )


def close_headers():
    return {
        "Authorization": "Bearer valid",
        "Idempotency-Key": "close-123456789012",
    }


def valid_payload():
    return {
        "channel": "PTT_ROOM",
        "destination": {
            "type": "sentinel_room",
            "room_id": demo_room_id_for_device("device-1"),
        },
        "client": {
            "device_id": "device-1",
            "app_version": "1.0.0",
            "platform": "android",
        },
    }


def test_create_fails_closed_when_idempotency_record_is_corrupt():
    idempotency_key = "create-123456789012"
    redis_key = f"voice:idempotency:v2:{_hash(f'user-1:{idempotency_key}')}"
    redis_client = FakeRedis({redis_key: "{"})
    app = create_app(
        settings=configured_settings(),
        redis_client=redis_client,
        identity_verifier=FakeIdentityVerifier(),
        token_issuer=FakeTokenIssuer(),
    )

    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/v1/voice/sessions",
            headers={
                "Authorization": "Bearer valid",
                "Idempotency-Key": idempotency_key,
            },
            json=valid_payload(),
        )

    assert response.status_code == 503
    assert response.json()["detail"] == "Voice service unavailable"


def test_close_fails_closed_when_open_session_record_is_corrupt():
    session_id = "session-corrupt-open"
    redis_client = FakeRedis({f"voice:session:v1:{session_id}": "{"})
    app = create_app(
        settings=configured_settings(),
        redis_client=redis_client,
        identity_verifier=FakeIdentityVerifier(),
        token_issuer=FakeTokenIssuer(),
    )

    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            f"/v1/voice/sessions/{session_id}/close",
            headers=close_headers(),
        )

    assert response.status_code == 503
    assert response.json()["detail"] == "Voice service unavailable"


def test_close_fails_closed_when_replay_record_is_corrupt():
    session_id = "session-corrupt-replay"
    redis_client = FakeRedis({f"voice:closed:v1:{session_id}": "{"})
    app = create_app(
        settings=configured_settings(),
        redis_client=redis_client,
        identity_verifier=FakeIdentityVerifier(),
        token_issuer=FakeTokenIssuer(),
    )

    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            f"/v1/voice/sessions/{session_id}/close",
            headers=close_headers(),
        )

    assert response.status_code == 503
    assert response.json()["detail"] == "Voice service unavailable"
