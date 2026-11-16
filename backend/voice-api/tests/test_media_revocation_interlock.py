"""Provider-independent confinement. Fakes are NOT evidence of LiveKit revocation."""
import asyncio
import json
from concurrent.futures import ThreadPoolExecutor
from threading import Event

from fastapi.testclient import TestClient
from app import create_app
from test_voice_service import (
    make_client, valid_payload, configured_settings, FakeRedis,
    FakeIdentityVerifier, entitled_principal, FakeTokenIssuer,
)
from revocation_fixture import FakeMediaRevoker

HEADERS = {"Authorization": "Bearer valid", "Idempotency-Key": "revocation-test-create-123456"}
CLOSE_HEADERS = {"Authorization": "Bearer valid", "Idempotency-Key": "revocation-test-close-123456"}


def test_real_service_cannot_issue_without_a_qualified_media_revocation_adapter():
    issuer = FakeTokenIssuer()
    app = create_app(settings=configured_settings(), redis_client=FakeRedis(),
                     identity_verifier=FakeIdentityVerifier(entitled_principal()), token_issuer=issuer)
    with TestClient(app) as client:
        assert client.get('/health/live').status_code == 200
        assert client.get('/health/ready').status_code == 503
        assert client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload()).status_code == 503
    assert issuer.calls == []


def test_unconfirmed_revocation_is_not_reported_as_closed_and_can_be_retried():
    client, issuer = make_client()
    provider = FakeMediaRevoker(False)
    client.app.state.voice_media_revoker = provider
    with client:
        created = client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload())
        assert created.status_code == 201
        sid = created.json()['session_id']
        close = client.post(f'/v1/voice/sessions/{sid}/close', headers=CLOSE_HEADERS)
        assert close.status_code == 503
        state = json.loads(client.app.state.voice_redis.values[f'voice:closed:v1:{sid}'])
        assert state['revocation'] == 'pending'
        assert f'voice:session:v1:{sid}' in client.app.state.voice_redis.values
        assert client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload()).status_code == 409
        provider.result = True
        closed = client.post(f'/v1/voice/sessions/{sid}/close', headers=CLOSE_HEADERS)
        assert closed.status_code == 200
        assert closed.json() == {'session_id': sid, 'status': 'closed'}
        assert provider.calls[0] == provider.calls[1]
        assert provider.calls[0]['identity'] == issuer.calls[0]['identity']
        assert provider.calls[0]['room'] == issuer.calls[0]['room']
        assert provider.calls[0]['session_id'] == sid


def test_provider_failure_retains_tombstone_and_does_not_return_success():
    class FailingProvider:
        async def revoke(self, **_):
            raise OSError('test provider unavailable')
    client, _ = make_client()
    client.app.state.voice_media_revoker = FailingProvider()
    with client:
        sid = client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload()).json()['session_id']
        result = client.post(f'/v1/voice/sessions/{sid}/close', headers=CLOSE_HEADERS)
        assert result.status_code == 503
        assert result.json()['detail'] == 'Media revocation unconfirmed'
        assert client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload()).status_code == 409


def test_new_sessions_use_distinct_media_rooms_and_identities():
    client, issuer = make_client()
    with client:
        first = client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload())
        other_headers = dict(HEADERS, **{'Idempotency-Key': 'distinct-create-key-123456789'})
        second = client.post('/v1/voice/sessions', headers=other_headers, json=valid_payload())
    assert first.status_code == second.status_code == 201
    assert issuer.calls[0]['room'] != issuer.calls[1]['room']
    assert issuer.calls[0]['identity'] != issuer.calls[1]['identity']
    assert issuer.calls[0]['room'].endswith(first.json()['session_id'])
    assert issuer.calls[1]['room'].endswith(second.json()['session_id'])


def test_creation_replay_is_denied_while_the_provider_is_still_revoking():
    entered, release = Event(), Event()
    class BlockingProvider:
        async def revoke(self, **_):
            entered.set()
            assert await asyncio.to_thread(release.wait, 2)
            return True
    client, _ = make_client()
    client.app.state.voice_media_revoker = BlockingProvider()
    with client, ThreadPoolExecutor(max_workers=1) as pool:
        sid = client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload()).json()['session_id']
        close = pool.submit(client.post, f'/v1/voice/sessions/{sid}/close', headers=CLOSE_HEADERS)
        try:
            assert entered.wait(2)
            assert client.post(f'/v1/voice/sessions/{sid}/close', headers=CLOSE_HEADERS).status_code == 409
            assert client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload()).status_code == 409
        finally:
            release.set()
        assert close.result(timeout=3).status_code == 200


def test_pending_tombstone_without_session_never_claims_confirmed_closure():
    client, _ = make_client()
    with client:
        sid = client.post('/v1/voice/sessions', headers=HEADERS, json=valid_payload()).json()['session_id']
        values = client.app.state.voice_redis.values
        values[f'voice:closed:v1:{sid}'] = json.dumps({'subject': 'user-1', 'device_id': 'device-1', 'revocation': 'pending'})
        values.pop(f'voice:session:v1:{sid}')
        assert client.post(f'/v1/voice/sessions/{sid}/close', headers=CLOSE_HEADERS).status_code == 503


def test_invalid_session_identifiers_are_bounded_before_store_access():
    client, _ = make_client()
    with client:
        for sid in ["x" * 129, "invalid!session"]:
            assert client.post(f'/v1/voice/sessions/{sid}/close', headers=CLOSE_HEADERS).status_code == 422
