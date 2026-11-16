"""A cached creation response must not resurrect a closed/missing session."""
import pytest
from test_voice_service import make_client, valid_payload


@pytest.mark.parametrize("closed", [True, False])
def test_creation_replay_requires_an_open_session(closed):
    client, issuer = make_client()
    headers = {"Authorization": "Bearer valid", "Idempotency-Key": "replay-after-close-123456789"}
    with client:
        created = client.post("/v1/voice/sessions", headers=headers, json=valid_payload())
        assert created.status_code == 201
        session_id = created.json()["session_id"]
        if closed:
            result = client.post(
                f"/v1/voice/sessions/{session_id}/close",
                headers={"Authorization": "Bearer valid", "Idempotency-Key": "close-replay-123456789"},
            )
            assert result.status_code == 200
        else:
            client.app.state.voice_redis.values.pop(f"voice:session:v1:{session_id}")
        replay = client.post("/v1/voice/sessions", headers=headers, json=valid_payload())
    assert replay.status_code == 409
    assert "transport" not in replay.json()
    assert len(issuer.calls) == 1


@pytest.mark.parametrize("field,value", [
    ("subject", "other-user"), ("device_id", "other-device"),
    ("session_id", "other-session"), ("room", "other-room"), ("status", "closed"),
])
def test_creation_replay_rejects_mismatched_session_metadata(field, value):
    import json
    client, issuer = make_client()
    headers = {"Authorization": "Bearer valid", "Idempotency-Key": "mismatched-replay-123456789"}
    with client:
        created = client.post("/v1/voice/sessions", headers=headers, json=valid_payload())
        assert created.status_code == 201
        key = f"voice:session:v1:{created.json()['session_id']}"
        data = json.loads(client.app.state.voice_redis.values[key])
        data[field] = value
        client.app.state.voice_redis.values[key] = json.dumps(data)
        replay = client.post("/v1/voice/sessions", headers=headers, json=valid_payload())
    assert replay.status_code == 503
    assert "transport" not in replay.json()
    assert len(issuer.calls) == 1


def test_disabled_service_is_live_but_not_ready():
    from fastapi.testclient import TestClient
    from app import create_app, Settings
    with TestClient(create_app(settings=Settings(enabled=False))) as client:
        assert client.get("/health/live").status_code == 200
        assert client.get("/health/ready").status_code == 503
