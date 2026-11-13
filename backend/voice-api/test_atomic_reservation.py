"""Fail-closed atomic session reservation tests.

Requires dependencies from backend/voice-api. Run:
python -m unittest discover -s backend/voice-api -p 'test_atomic_reservation.py'
"""
import asyncio
import importlib.util
import pathlib
import sys
import unittest

path = pathlib.Path(__file__).with_name("app.py")
spec = importlib.util.spec_from_file_location("sentinel_voice_atomic_under_test", path)
voice = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = voice
spec.loader.exec_module(voice)


class NonAtomicRedis:
    def __init__(self):
        self.writes = []

    async def set(self, *args, **kwargs):
        self.writes.append((args, kwargs))
        return True


class AtomicRedis:
    def __init__(self, result):
        self.result = result
        self.calls = []

    async def eval(self, *args):
        self.calls.append(args)
        return self.result


def reserve(client):
    return asyncio.run(voice._reserve_session(
        client,
        idempotency_key="voice:idempotency:v2:key",
        idempotency_value="idempotency-record",
        session_key="voice:session:v1:session",
        session_value="session-record",
        idempotency_ttl=120,
        session_ttl=180,
    ))


class AtomicReservationTests(unittest.TestCase):
    def test_missing_eval_refuses_without_writes(self):
        client = NonAtomicRedis()
        with self.assertRaises(voice.RedisError):
            reserve(client)
        self.assertEqual([], client.writes)

    def test_atomic_success(self):
        client = AtomicRedis(1)
        self.assertTrue(reserve(client))
        self.assertEqual(1, len(client.calls))

    def test_atomic_conflict(self):
        client = AtomicRedis(0)
        self.assertFalse(reserve(client))
        self.assertEqual(1, len(client.calls))

    def test_atomic_failure(self):
        client = AtomicRedis(-1)
        with self.assertRaises(voice.RedisError):
            reserve(client)


if __name__ == "__main__":
    unittest.main()
