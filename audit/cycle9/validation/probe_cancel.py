#!/usr/bin/env python3
"""Real local Qwen HTTP cancellation probe. Never certifies Android or human authentication."""
import argparse
import asyncio
import hashlib
import hmac
import json
import platform
from pathlib import Path
import secrets
import time
import httpx

MODEL_SHA = '74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db'

def mac(key, text):return hmac.new(key,text.encode(),hashlib.sha256).hexdigest()

def children(parent):
    path=Path(f'/proc/{parent}/task/{parent}/children')
    if path.exists():return [int(v) for v in path.read_text().split()]
    # Some Android/container kernels omit task/children. PPid is an observation fallback.
    own_uid=next(line.split()[1] for line in Path('/proc/self/status').read_text().splitlines() if line.startswith('Uid:'))
    found=[]
    for item in Path('/proc').iterdir():
        if not item.name.isdigit():continue
        try:
            values={line.split(':',1)[0]:line.split(':',1)[1].strip() for line in (item/'status').read_text().splitlines() if ':' in line}
            if values.get('PPid')==str(parent) and values.get('Uid','').split()[0]==own_uid:found.append(int(item.name))
        except (OSError,IndexError):continue
    return found

async def run(args):
    key=bytes.fromhex(args.pairing_file.read_text().strip())
    if len(key)!=32:raise ValueError('Pairing key length rejected')
    with args.binary.open('rb') as source:binary_sha=hashlib.file_digest(source,'sha256').hexdigest()
    def models():
        found=[]
        for pid in children(args.service_pid):
            try:
                path=Path(f'/proc/{pid}/exe')
                if path.readlink().name!=args.binary.name:continue
                with path.open('rb') as source:digest=hashlib.file_digest(source,'sha256').hexdigest()
                if digest==binary_sha:found.append(pid)
            except FileNotFoundError:pass
        return found
    if models():raise RuntimeError('Service must be idle; do not interrupt another request')
    async with httpx.AsyncClient(base_url='http://127.0.0.1:18080',trust_env=False,timeout=40) as client:
        async def signed(message):
            nonce_client=secrets.token_hex(32)
            response=await client.get('/challenge',params={'client_nonce':nonce_client});response.raise_for_status()
            challenge=response.json();nonce=challenge['nonce']
            assert challenge['model_sha256']==MODEL_SHA
            assert hmac.compare_digest(challenge['proof'],mac(key,'server\0'+nonce_client+'\0'+nonce+'\0'+MODEL_SHA))
            raw=json.dumps({'message':message},ensure_ascii=False)
            return raw.encode(),{'X-Sentinel-Nonce':nonce,'X-Sentinel-Mac':mac(key,'client\0'+nonce+'\0'+raw)},nonce
        body,headers,_=await signed('Donne environ 200 caractères de conseils de sécurité numérique, sans action.')
        request=asyncio.create_task(client.post('/infer',content=body,headers=headers))
        running=[];start=time.monotonic()
        try:
            while not request.done() and time.monotonic()-start<5:
                running=models()
                if running:break
                await asyncio.sleep(.02)
            assert running and not request.done(), 'No active model process observed; cancellation is inconclusive'
            await asyncio.sleep(.1)
            assert not request.done(), 'Model completed before cancellation; inconclusive'
            cancelled=time.monotonic();request.cancel();await asyncio.gather(request,return_exceptions=True)
            while time.monotonic()-cancelled<2 and any(Path(f'/proc/{pid}').exists() for pid in running):await asyncio.sleep(.02)
            assert not any(Path(f'/proc/{pid}').exists() for pid in running), 'Model process survived cancellation'
            released=time.monotonic()-cancelled
            replay=await client.post('/infer',content=body,headers=headers)
            assert replay.status_code==409
            follow,auth,nonce=await signed('Combien font 2 + 2 ?')
            response=await client.post('/infer',content=follow,headers=auth);response.raise_for_status()
            assert hmac.compare_digest(response.headers['X-Sentinel-Mac'],mac(key,'response\0'+nonce+'\0'+response.text))
            answer=response.json();assert '4' in answer['reply'] and answer['action']=='NONE'
            result={'machine':platform.machine(),'binary_sha256':binary_sha,'model_sha256':MODEL_SHA,
                    'observed_model_pids':running,'stopped_after_cancel_s':released,'replay_http':409,
                    'authenticated_followup':answer,'android_device_evidence':'REQUIRED_SEPARATELY',
                    'human_authentication_tested':False,'production_go':False}
            with args.output.open('x') as output:json.dump(result,output,ensure_ascii=False,indent=2)
            print(json.dumps(result,ensure_ascii=False))
        finally:
            if not request.done():request.cancel()
            await asyncio.gather(request,return_exceptions=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--service-pid',type=int,required=True)
    parser.add_argument('--binary',type=Path,required=True)
    parser.add_argument('--pairing-file',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    asyncio.run(run(parser.parse_args()))
