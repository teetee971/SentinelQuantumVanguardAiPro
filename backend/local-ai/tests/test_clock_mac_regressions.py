import asyncio
import importlib.util
from pathlib import Path
import unittest
import httpx

spec = importlib.util.spec_from_file_location('service', Path(__file__).resolve().parents[1] / 'service.py')
service = importlib.util.module_from_spec(spec)
spec.loader.exec_module(service)

class Runtime:
    async def generate(self, message):
        return {'reply': 'Réponse locale', 'action': 'NONE'}

class ServiceTests(unittest.IsolatedAsyncioTestCase):
    async def client(self, clock=lambda: 1.0):
        return httpx.AsyncClient(transport=httpx.ASGITransport(app=service.make_app(Runtime(), b'k'*32, clock)), base_url='http://local')

    async def test_non_finite_clock_rejected_and_poisoned(self):
        for invalid in (float('nan'), float('inf'), -float('inf')):
            values = iter([invalid, 1.0])
            async with await self.client(lambda: next(values)) as client:
                for _ in range(2):
                    response = await client.get('/challenge', params={'client_nonce': 'a'*64})
                    self.assertEqual(response.status_code, 503)

    async def test_non_ascii_mac_rejected(self):
        async with await self.client() as client:
            nonce = (await client.get('/challenge', params={'client_nonce': 'a'*64})).json()['nonce']
            response = await client.post('/infer', content='{"message":"test"}', headers={b'X-Sentinel-Nonce': nonce.encode(), b'X-Sentinel-Mac': b'\xff'})
            self.assertEqual(response.status_code, 401)

    async def test_response_authenticated_and_nonce_single_use(self):
        async with await self.client() as client:
            nonce = (await client.get('/challenge', params={'client_nonce': 'a'*64})).json()['nonce']
            raw = '{"message":"test"}'
            headers = {'X-Sentinel-Nonce': nonce, 'X-Sentinel-Mac': service.mac(b'k'*32, 'client\0'+nonce+'\0'+raw)}
            response = await client.post('/infer', content=raw, headers=headers)
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.headers['X-Sentinel-Mac'], service.mac(b'k'*32, 'response\0'+nonce+'\0'+response.text))
            self.assertEqual((await client.post('/infer', content=raw, headers=headers)).status_code, 409)

if __name__ == '__main__':
    unittest.main()
