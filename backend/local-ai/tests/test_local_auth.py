import hashlib
import hmac
import importlib.util
import json
from pathlib import Path
import pytest
from fastapi.testclient import TestClient

spec = importlib.util.spec_from_file_location('local_ai_service', Path(__file__).parents[1] / 'service.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
KEY = bytes(range(32))  # Test fixture only.
class FakeRuntime:
    calls = 0
    async def generate(self, message):
        self.calls += 1
        return {'reply': 'test fixture', 'action': 'NONE'}
def signed(client):
    challenge = client.get('/challenge', params={'client_nonce': 'a' * 64}).json()
    body = json.dumps({'message': 'test'})
    headers = {'X-Sentinel-Nonce': challenge['nonce'], 'X-Sentinel-Mac': module.mac(KEY, 'client\0'+challenge['nonce']+'\0'+body)}
    return body, headers, challenge

def test_pairing_response_is_authenticated_and_nonce_is_single_use():
    runtime = FakeRuntime(); client = TestClient(module.make_app(runtime, KEY))
    body, headers, challenge = signed(client)
    assert challenge['proof'] == module.mac(KEY, 'server\0'+'a'*64+'\0'+challenge['nonce']+'\0'+module.MODEL_SHA256)
    response = client.post('/infer', content=body, headers=headers)
    assert response.status_code == 200
    assert response.headers['X-Sentinel-Mac'] == module.mac(KEY, 'response\0'+challenge['nonce']+'\0'+response.text)
    assert client.post('/infer', content=body, headers=headers).status_code == 409
    assert runtime.calls == 1

def test_wrong_key_never_reaches_model_and_consumes_challenge():
    runtime = FakeRuntime(); client = TestClient(module.make_app(runtime, KEY))
    body, headers, _ = signed(client); good = dict(headers);headers['X-Sentinel-Mac'] = '0'*64
    assert client.post('/infer', content=body, headers=headers).status_code == 401
    assert client.post('/infer', content=body, headers=good).status_code == 409
    assert runtime.calls == 0

def test_expiration_and_clock_rollback_fail_closed():
    at = [100.0]; runtime = FakeRuntime();client = TestClient(module.make_app(runtime, KEY, lambda: at[0]))
    body, headers, _ = signed(client);at[0] = 105
    assert client.post('/infer', content=body, headers=headers).status_code == 409
    at[0] = 99
    assert client.get('/challenge', params={'client_nonce':'a'*64}).status_code == 503
    at[0] = 200
    assert client.get('/challenge', params={'client_nonce':'a'*64}).status_code == 503

def test_payload_and_challenge_capacity_are_bounded():
    client = TestClient(module.make_app(FakeRuntime(), KEY))
    assert client.post('/infer', content=b'x'*4097).status_code == 413
    for _ in range(128):assert client.get('/challenge', params={'client_nonce':'a'*64}).status_code == 200
    assert client.get('/challenge', params={'client_nonce':'a'*64}).status_code == 429

@pytest.mark.parametrize('value', [ {'reply':'ok','action':'DELETE_CALLS'}, {'reply':'ok','action':'NONE','execute':True}, {'reply':'x'*257,'action':'NONE'} ])
def test_unregistered_actions_and_extra_fields_are_rejected(value):
    with pytest.raises(ValueError):module.checked_result(value)

def test_model_must_match_approved_fingerprint(tmp_path):
    model = tmp_path/'model.gguf';model.write_bytes(b'not approved')
    with pytest.raises(ValueError):module.LlamaRuntime('/bin/true',model)

def test_prompt_uses_stdin_and_never_process_arguments(monkeypatch):
    import asyncio
    seen = {}
    class Process:
        returncode = 0
        async def communicate(self, content):
            seen['stdin'] = content
            return b'{"reply":"fixture","action":"NONE"}', b''
    async def launch(*args, **kwargs):
        seen['args'] = args
        assert kwargs['stdin'] == asyncio.subprocess.PIPE
        return Process()
    monkeypatch.setattr(module.asyncio, 'create_subprocess_exec', launch)
    runtime = object.__new__(module.LlamaRuntime)
    runtime.binary = Path('/test-only/runtime');runtime.model = Path('/test-only/model');runtime.busy = asyncio.Lock()
    assert asyncio.run(runtime.generate('private test input'))['action'] == 'NONE'
    assert 'private test input' not in ' '.join(seen['args'])
    assert b'private test input' in seen['stdin']
    assert seen['args'][3:5] == ('-f', '/dev/stdin')


def test_cancelled_generation_terminates_and_reaps_process(monkeypatch):
    import asyncio
    state = {'killed': False, 'reaped': False}
    class Process:
        returncode = None
        async def communicate(self, content):
            raise asyncio.CancelledError()
        def kill(self):
            state['killed'] = True
        async def wait(self):
            state['reaped'] = True
    async def launch(*args, **kwargs):return Process()
    monkeypatch.setattr(module.asyncio, 'create_subprocess_exec', launch)
    runtime = object.__new__(module.LlamaRuntime)
    runtime.binary = Path('/test-only/runtime');runtime.model = Path('/test-only/model');runtime.busy = asyncio.Lock()
    with pytest.raises(asyncio.CancelledError):asyncio.run(runtime.generate('test'))
    assert state == {'killed': True, 'reaped': True}
    assert not runtime.busy.locked()
