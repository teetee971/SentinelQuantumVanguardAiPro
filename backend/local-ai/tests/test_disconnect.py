"""Tests only: real HTTP sockets and a sleeping child, never a fake production AI reply."""
import asyncio
import importlib.util
import json
import os
from pathlib import Path
import signal
import socket
import sys

import httpx
import uvicorn

spec = importlib.util.spec_from_file_location('disconnect_service', Path(__file__).parents[1] / 'service.py')
service = importlib.util.module_from_spec(spec)
spec.loader.exec_module(service)
KEY = bytes(range(32))  # Test fixture only.


def test_http_disconnect_kills_real_child_and_releases_busy_lock(tmp_path):
    async def run():
        pidfile = tmp_path / 'child.pid'
        binary = tmp_path / 'sleeping-fixture'
        binary.write_text(f'#!{sys.executable}\nimport os,time\nfrom pathlib import Path\n'
                          f'Path({str(pidfile)!r}).write_text(str(os.getpid()))\ntime.sleep(60)\n')
        binary.chmod(0o700)
        runtime = object.__new__(service.LlamaRuntime)
        runtime.binary = binary
        runtime.model = tmp_path / 'unused-test-model'
        runtime.busy = asyncio.Lock()
        app = service.make_app(runtime, KEY)
        listener = socket.socket()
        listener.bind(('127.0.0.1', 0));listener.listen(8);listener.setblocking(False)
        port = listener.getsockname()[1]
        server = uvicorn.Server(uvicorn.Config(app, log_level='critical', access_log=False, lifespan='off'))
        serving = asyncio.create_task(server.serve(sockets=[listener]))
        writer = None
        child_pid = None
        try:
            for _ in range(150):
                if server.started:break
                await asyncio.sleep(.02)
            assert server.started
            async with httpx.AsyncClient(base_url=f'http://127.0.0.1:{port}', trust_env=False) as client:
                baseline_fds = None
                for _attempt in range(3):
                    if pidfile.exists():pidfile.unlink()  # This test's own stale PID fixture only.
                    challenge = (await client.get('/challenge', params={'client_nonce': 'a' * 64})).json()
                    if baseline_fds is None:baseline_fds = len(os.listdir('/proc/self/fd'))
                    nonce = challenge['nonce']
                    raw = json.dumps({'message': 'test-only cancellation fixture'})
                    proof = service.mac(KEY, 'client\0' + nonce + '\0' + raw)
                    _, writer = await asyncio.open_connection('127.0.0.1', port)
                    body = raw.encode()
                    headers = (f'POST /infer HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: {len(body)}\r\n'
                               f'X-Sentinel-Nonce: {nonce}\r\nX-Sentinel-Mac: {proof}\r\n\r\n').encode()
                    writer.write(headers + body);await writer.drain()
                    for _ in range(150):
                        if pidfile.exists():break
                        await asyncio.sleep(.02)
                    assert pidfile.exists(), 'Test child was not started'
                    child_pid = int(pidfile.read_text())
                    assert Path(f'/proc/{child_pid}').exists()
                    writer.close();await writer.wait_closed()
                    for _ in range(50):
                        if not Path(f'/proc/{child_pid}').exists() and not runtime.busy.locked():break
                        await asyncio.sleep(.02)
                    assert not Path(f'/proc/{child_pid}').exists(), 'Child survived HTTP disconnect'
                    assert not runtime.busy.locked(), 'Model remained busy after disconnect'
                    replay = await client.post('/infer', content=body, headers={'X-Sentinel-Nonce':nonce, 'X-Sentinel-Mac':proof})
                    assert replay.status_code == 409
                    assert len(os.listdir('/proc/self/fd')) <= baseline_fds + 2, "Descriptors accumulated after cancellation"
        finally:
            if writer is not None:writer.close()
            if child_pid is not None:
                try:os.kill(child_pid, signal.SIGKILL)
                except ProcessLookupError:pass
            server.should_exit = True
            try:await asyncio.wait_for(serving, 3)
            except asyncio.TimeoutError:
                serving.cancel();await asyncio.gather(serving, return_exceptions=True)
            listener.close()
    asyncio.run(run())


def test_disconnect_racing_with_generation_never_returns_a_success():
    async def run():
        from fastapi.testclient import TestClient
        disconnected = asyncio.Event()
        class FixtureRuntime:
            async def generate(self, message):
                disconnected.set()
                await asyncio.sleep(0)
                return {'reply': 'test fixture only', 'action': 'NONE'}
        app = service.make_app(FixtureRuntime(), KEY)
        challenge = TestClient(app).get('/challenge', params={'client_nonce': 'a'*64}).json()
        nonce = challenge['nonce'];raw = json.dumps({'message': 'test fixture'})
        proof = service.mac(KEY, 'client\0'+nonce+'\0'+raw)
        first = True
        async def receive():
            nonlocal first
            if first:
                first = False
                return {'type':'http.request', 'body':raw.encode(), 'more_body':False}
            await disconnected.wait()
            return {'type':'http.disconnect'}
        statuses = []
        async def send(message):
            if message['type'] == 'http.response.start':statuses.append(message['status'])
        scope = {'type':'http','asgi':{'version':'3.0'},'http_version':'1.1','method':'POST',
                 'scheme':'http','path':'/infer','raw_path':b'/infer','query_string':b'',
                 'root_path':'','headers':[(b'x-sentinel-nonce',nonce.encode()),(b'x-sentinel-mac',proof.encode())],
                 'client':('127.0.0.1',12345),'server':('127.0.0.1',18080)}
        await asyncio.wait_for(app(scope, receive, send), 2)
        assert statuses == [499], 'Observed disconnect must not produce a successful ASGI response'
    asyncio.run(run())
