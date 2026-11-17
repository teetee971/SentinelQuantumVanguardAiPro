"""Offline-only, paired loopback bridge to llama.cpp. No OpenAI/cloud client exists here."""
import argparse
import asyncio
import hashlib
import hmac
import json
import math
import os
import secrets
import time
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response

MODEL_SHA256 = '74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db'
SCHEMA = {'type': 'object', 'properties': {
    'reply': {'type': 'string', 'minLength': 1, 'maxLength': 256},
    'action': {'type': 'string', 'enum': ['NONE', 'CREATE_AUDIT_CHECKPOINT']}},
    'required': ['reply', 'action'], 'additionalProperties': False}


def mac(key, value):
    return hmac.new(key, value.encode('utf-8'), hashlib.sha256).hexdigest()


def checked_result(data):
    if (not isinstance(data, dict) or set(data) != {'reply', 'action'}
            or not isinstance(data['reply'], str) or not 1 <= len(data['reply']) <= 256
            or '\x00' in data['reply'] or data['action'] not in ('NONE', 'CREATE_AUDIT_CHECKPOINT')):
        raise ValueError('Model output rejected')
    return data


async def _communicate_bounded(process, timeout_seconds=30):
    """Drain both streams concurrently, limiting retained bytes before extending."""
    async def read(stream, limit):
        data = bytearray()
        while True:
            chunk = await stream.read(min(4096, limit - len(data) + 1))
            if not chunk:
                return bytes(data)
            if len(data) + len(chunk) > limit:
                raise HTTPException(502, 'Local generation output limit exceeded')
            data.extend(chunk)

    tasks = [asyncio.create_task(read(process.stdout, 8192)),
             asyncio.create_task(read(process.stderr, 65536)),
             asyncio.create_task(process.wait())]
    try:
        stdout, stderr, _ = await asyncio.wait_for(asyncio.gather(*tasks), timeout_seconds)
        return stdout, stderr
    except BaseException:
        # Cancellation is acknowledged only after the direct model child is reaped.
        # Shield cleanup even against repeated cancellation from a disconnect/timeout race.
        async def cleanup():
            if process.returncode is None:
                try:
                    process.kill()
                except ProcessLookupError:
                    pass
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            # Discard pipe buffers after killing the child. Otherwise a paused
            # StreamReader transport can prevent process.wait() from completing.
            async def discard(stream):
                while await stream.read(4096):
                    pass
            await asyncio.gather(discard(process.stdout), discard(process.stderr), process.wait())
        pending = asyncio.create_task(cleanup())
        while not pending.done():
            try:
                await asyncio.shield(pending)
            except asyncio.CancelledError:
                continue
        pending.result()
        raise


class LlamaRuntime:
    def __init__(self, binary, model):
        self.binary, self.model = Path(binary).resolve(), Path(model).resolve()
        if not self.binary.is_file() or not os.access(self.binary, os.X_OK):
            raise ValueError('Local engine binary unavailable')
        with self.model.open('rb') as source:
            digest = hashlib.file_digest(source, 'sha256').hexdigest()
        if digest != MODEL_SHA256:
            raise ValueError('Model fingerprint not approved')
        self.busy = asyncio.Lock()

    async def generate(self, message):
        if self.busy.locked():
            raise HTTPException(429, 'Local engine busy')
        async with self.busy:
            prompt = ('<|im_start|>system\nRéponds brièvement en français avec un objet JSON contenant reply et action. '
                      'action vaut NONE sauf si la demande exige un point de contrôle du journal IA, alors '
                      'CREATE_AUDIT_CHECKPOINT. Tu proposes seulement. Tu ne peux ni approuver ni exécuter.'
                      '<|im_end|>\n<|im_start|>user\n' + message + '<|im_end|>\n<|im_start|>assistant\n')
            args = [str(self.binary), '-m', str(self.model), '-f', '/dev/stdin', '-n', '128', '-c', '1024', '-t', '2',
                    '--temp', '0', '--seed', '42', '--no-display-prompt', '--no-conversation',
                    '--json-schema', json.dumps(SCHEMA)]
            env = {'PATH': os.environ.get('PATH', '/usr/bin:/bin'), 'LLAMA_OFFLINE': '1', 'HF_HUB_OFFLINE': '1'}
            # uvloop uses a socket for stdin=PIPE; llama.cpp reopens /dev/stdin,
            # which cannot reopen that socket. Supply a real, private OS pipe instead.
            encoded = prompt.encode('utf-8')
            if len(encoded) > 4096:
                raise HTTPException(422, 'Prompt too large')
            read_fd, write_fd = os.pipe()
            with os.fdopen(read_fd, 'rb') as source:
                with os.fdopen(write_fd, 'wb') as sink:
                    sink.write(encoded)
                process = await asyncio.create_subprocess_exec(*args, stdin=source, stdout=asyncio.subprocess.PIPE,
                                                              stderr=asyncio.subprocess.PIPE, env=env)
            try:
                stdout, _ = await _communicate_bounded(process)
            except BaseException:
                if process.returncode is None:
                    process.kill()
                    await process.wait()
                raise
            if process.returncode or len(stdout) > 8192:
                raise HTTPException(502, 'Local generation failed')
            try:
                text = stdout.decode('utf-8').strip()
                data, end = json.JSONDecoder().raw_decode(text)
                if text[end:].strip() not in ('', '[end of text]'):
                    raise ValueError('Unexpected model suffix')
                return checked_result(data)
            except (ValueError, UnicodeError, TypeError) as exc:
                raise HTTPException(502, 'Local model output rejected') from exc



async def _read_bounded_body(request):
    data = bytearray()
    async for chunk in request.stream():
        if len(data) + len(chunk) > 4096:
            raise HTTPException(413, 'Request too large')
        data.extend(chunk)
    return data


async def _generate_while_connected(runtime, message, request):
    """After the bounded body is consumed, only disconnect events are expected.
    Never let an abandoned HTTP request keep the single inference slot occupied.
    """
    generation = asyncio.create_task(runtime.generate(message))
    async def disconnected():
        event = await request.receive()
        return event.get('type') == 'http.disconnect'
    gone = asyncio.create_task(disconnected())
    tasks = (generation, gone)
    try:
        done, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        if gone in done:
            try:
                confirmed = gone.result()
            except Exception:
                confirmed = False
            raise HTTPException(499 if confirmed else 503, 'Client disconnected' if confirmed else 'Connection unavailable')
        return generation.result()
    finally:
        for task in tasks:
            if not task.done():
                task.cancel()
        # LlamaRuntime cancellation kills and reaps its subprocess before releasing busy.
        await asyncio.gather(*tasks, return_exceptions=True)


def make_app(runtime, key, clock=time.monotonic):
    if not isinstance(key, bytes) or len(key) != 32:
        raise ValueError('Pairing key must be 256 bits')
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
    pending = {}
    last_time = -1.0
    poisoned = False

    def now():
        nonlocal last_time, poisoned
        value = clock()
        if poisoned or not math.isfinite(value) or value < 0 or value < last_time:
            poisoned = True
            pending.clear()
            raise HTTPException(503, 'Clock unavailable')
        last_time = value
        return value

    @app.get('/challenge')
    async def challenge(client_nonce: str):
        if len(client_nonce) != 64 or any(c not in '0123456789abcdef' for c in client_nonce):
            raise HTTPException(422, 'Invalid challenge')
        at = now()
        for nonce in list(pending):
            if pending[nonce] <= at:
                del pending[nonce]
        if len(pending) >= 128:
            raise HTTPException(429, 'Too many challenges')
        nonce = secrets.token_hex(32)
        pending[nonce] = at + 5
        return {'nonce': nonce, 'model_sha256': MODEL_SHA256,
                'proof': mac(key, 'server\0' + client_nonce + '\0' + nonce + '\0' + MODEL_SHA256)}

    @app.post('/infer')
    async def infer(request: Request):
        try:
            data = await asyncio.wait_for(_read_bounded_body(request), 2)
        except asyncio.TimeoutError as exc:
            raise HTTPException(408, 'Request body timeout') from exc
        nonce = request.headers.get('X-Sentinel-Nonce', '')
        expiry = pending.pop(nonce, None)  # Every attempt consumes its server-issued capability.
        if expiry is None or now() >= expiry:
            raise HTTPException(409, 'Challenge expired or consumed')
        try:
            text = data.decode('utf-8')
        except UnicodeError as exc:
            raise HTTPException(422, 'Invalid request') from exc
        expected = mac(key, 'client\0' + nonce + '\0' + text)
        supplied_mac = request.headers.get('X-Sentinel-Mac', '')
        if (len(supplied_mac) != 64
                or any(c not in '0123456789abcdef' for c in supplied_mac)
                or not hmac.compare_digest(expected, supplied_mac)):
            raise HTTPException(401, 'Pairing authentication failed')
        try:
            body = json.loads(text)
            if (not isinstance(body, dict) or set(body) != {'message'} or
                    not isinstance(body['message'], str) or not 1 <= len(body['message']) <= 512 or
                    '\x00' in body['message']):
                raise ValueError('Invalid message')
            result = checked_result(await _generate_while_connected(runtime, body['message'], request))
        except (ValueError, TypeError) as exc:
            raise HTTPException(422, 'Invalid message or recommendation') from exc
        raw = json.dumps(result, ensure_ascii=False, separators=(',', ':'))
        return Response(raw, media_type='application/json', headers={
            'X-Sentinel-Mac': mac(key, 'response\0' + nonce + '\0' + raw)})
    return app


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', required=True)
    parser.add_argument('--model', required=True)
    parser.add_argument('--pairing-file', required=True, help='NEW private file; contents are never printed')
    parser.add_argument('--reuse-pairing-file', action='store_true')
    args = parser.parse_args()
    runtime = LlamaRuntime(args.binary, args.model)
    if args.reuse_pairing_file:
        import stat
        flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
        fd = os.open(args.pairing_file, flags)
        with os.fdopen(fd, 'rb') as source:
            info = os.fstat(source.fileno())
            if (not stat.S_ISREG(info.st_mode)
                    or info.st_uid != os.getuid()
                    or stat.S_IMODE(info.st_mode) != 0o600):
                raise ValueError('Pairing file must be private, owned, and mode 0600')
            raw = source.read(66)
        if (len(raw) != 65 or raw[-1:] != b'\n'
                or any(c not in b'0123456789abcdef' for c in raw[:64])):
            raise ValueError('Invalid pairing file')
        key = bytes.fromhex(raw[:64].decode('ascii'))
    else:
        key = secrets.token_bytes(32)
        fd = os.open(args.pairing_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as output:
            output.write(key.hex() + '\n')
    import uvicorn
    # Fixed loopback binding. No public host, remote model endpoint or billing setting.
    uvicorn.run(make_app(runtime, key), host='127.0.0.1', port=18080, access_log=False)
