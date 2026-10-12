import json
import sys
from pathlib import Path

from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import Settings, create_app  # noqa: E402


class RaceRedis:
    def __init__(self):
        self.values = {}
        self.counters = {}
        self.session_writes = []

    async def ping(self):
        return True

    async def incr(self, key):
        self.counters[key] = self.counters.get(key, 0) + 1
        return self.counters[key]

    async def expire(self, key, seconds):
        return True

    async def get(self, key):
        return self.values.get(key)

    async def delete(self, key):
        self.values.pop(key, None)
        return 1

    async def set(self, key, value, ex=None, nx=False):
        if nx and key.startswith("voice:idempotency:v1:"):
            candidate = json.loads(value)
            winner = json.loads(value)
            winner["response"] = dict(candidate["response"])
            winner["response"]["session_id"] = "winner-session"
            self.values[key] = json.dumps(winner, separators=(",", ":"))
            return False
        if key.startswith("voice:session:v1:"):
            self.session_writes.append(key)
        self.values[key] = value
        return True


class AtomicRedis(RaceRedis):
    def __init__(self):
        super().__init__()
        self.eval_calls = 0

    async def eval(self, script, number_of_keys, *args):
        self.eval_calls += 1
        assert number_of_keys == 2
        idempotency_key, session_key, idempotency_value, session_value, idempotency_ttl, session_ttl = args
        assert int(idempotency_ttl) == 120
        assert int(session_ttl) == 180
        if idempotency_key in self.values:
            return 0
        self.values[idempotency_key] = idempotency_value
        self.values[session_key] = session_value
        self.session_writes.append(session_key)
        return 1

    async def set(self, key, value, ex=None, nx=False):
        if key.startswith("voice:idempotency:v1:") or key.startswith("voice:session:v1:"):
            raise AssertionError("voice reservation must use Redis EVAL atomically")
        return await super().set(key, value, ex=ex, nx=nx)


class IdentityVerifier:
    async def verify(self, authorization):
        return {
            "subject": "user-1",
            "device_id": "device-1",
            "entitled": True,
        }


class TokenIssuer:
    def issue(self, *, identity, room, ttl_seconds):
        return "ephemeral-livekit-token"


def settings():
    return Settings(
        enabled=True,
        livekit_url="wss://voice.example.test",
        redis_url="redis://voice.example.test/0",
        token_ttl_seconds=120,
    )


def payload():
    return {
        "channel": "PTT_ROOM",
        "destination": {"type": "sentinel_room", "room_id": "group-1"},
        "client": {
            "device_id": "device-1",
            "app_version": "1.0.0",
            "platform": "android",
        },
    }


def test_losing_idempotency_race_does_not_write_orphan_session():
    redis_client = RaceRedis()
    app = create_app(
        settings=settings(),
        redis_client=redis_client,
        identity_verifier=IdentityVerifier(),
        token_issuer=TokenIssuer(),
    )

    with TestClient(app) as client:
        response = client.post(
            "/v1/voice/sessions",
            headers={
                "Authorization": "Bearer valid",
                "Idempotency-Key": "concurrent-key-123456",
            },
            json=payload(),
        )

    assert response.status_code == 201
    assert response.json()["session_id"] == "winner-session"
    assert redis_client.session_writes == []


def test_session_reservation_uses_atomic_redis_primitive_when_available():
    redis_client = AtomicRedis()
    app = create_app(
        settings=settings(),
        redis_client=redis_client,
        identity_verifier=IdentityVerifier(),
        token_issuer=TokenIssuer(),
    )

    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/v1/voice/sessions",
            headers={
                "Authorization": "Bearer valid",
                "Idempotency-Key": "atomic-key-123456789",
            },
            json=payload(),
        )

    assert response.status_code == 201
    assert redis_client.eval_calls == 1
    assert len(redis_client.session_writes) == 1
