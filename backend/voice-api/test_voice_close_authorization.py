"""Endpoint-level voice close authorization regression tests.

Run from repository root after installing backend dependencies:
python -m unittest discover -s backend/voice-api -p 'test_voice_close_authorization.py'
"""
import importlib.util
import json
import pathlib
import sys
import unittest

from fastapi.testclient import TestClient

path = pathlib.Path(__file__).with_name("app.py")
spec = importlib.util.spec_from_file_location("sentinel_voice_close_under_test", path)
voice = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = voice
spec.loader.exec_module(voice)


class MemoryRedis:
    def __init__(self, record):
        self.records = {"voice:session:v1:test-session": json.dumps(record)}

    async def get(self, key):
        return self.records.get(key)

    async def set(self, key, value, **kwargs):
        self.records[key] = value
        return True

    async def delete(self, key):
        return self.records.pop(key, None) is not None


class Identity:
    def __init__(self, principal):
        self.principal = principal

    async def verify(self, authorization):
        return self.principal


class Issuer:
    def issue(self, **kwargs):
        return "not-used"


class CloseAuthorizationTests(unittest.TestCase):
    def make_client(self, principal):
        store = MemoryRedis({
            "subject": "owner",
            "device_id": "device-a",
            "session_id": "test-session",
            "status": "open",
            "room": "test-room",
        })
        settings = voice.Settings(enabled=True, livekit_url="wss://example.invalid", redis_url="redis://example.invalid")
        app = voice.create_app(
            settings=settings,
            redis_client=store,
            identity_verifier=Identity(principal),
            token_issuer=Issuer(),
        )
        return TestClient(app), store

    def close(self, client):
        return client.post(
            "/v1/voice/sessions/test-session/close",
            headers={
                "Authorization": "Bearer test-token",
                "Idempotency-Key": "1234567890abcdef",
            },
        )

    def test_owner_can_close_after_email_unverified(self):
        client, store = self.make_client({
            "subject": "owner",
            "device_ids": ["device-a"],
            "email_verified": False,
        })
        response = self.close(client)
        self.assertEqual(200, response.status_code, response.text)
        self.assertNotIn("voice:session:v1:test-session", store.records)

    def test_other_user_cannot_close(self):
        client, store = self.make_client({
            "subject": "intruder",
            "device_ids": ["device-a"],
            "email_verified": True,
        })
        self.assertEqual(403, self.close(client).status_code)
        self.assertIn("voice:session:v1:test-session", store.records)

    def test_other_device_cannot_close(self):
        client, store = self.make_client({
            "subject": "owner",
            "device_ids": ["device-b"],
            "email_verified": True,
        })
        self.assertEqual(403, self.close(client).status_code)
        self.assertIn("voice:session:v1:test-session", store.records)

    def test_malformed_device_claim_is_denied_without_server_error(self):
        for malformed in (None, 123, {"device": "device-a"}, [None]):
            with self.subTest(malformed=malformed):
                client, store = self.make_client({
                    "subject": "owner",
                    "device_ids": malformed,
                    "email_verified": True,
                })
                self.assertEqual(403, self.close(client).status_code)
                self.assertIn("voice:session:v1:test-session", store.records)

    def test_empty_subject_cannot_close(self):
        client, store = self.make_client({
            "subject": "",
            "device_ids": ["device-a"],
            "email_verified": True,
        })
        self.assertEqual(401, self.close(client).status_code)
        self.assertIn("voice:session:v1:test-session", store.records)


if __name__ == "__main__":
    unittest.main()
