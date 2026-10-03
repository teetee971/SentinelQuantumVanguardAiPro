"""Exercises the real Lua scripts against an isolated local Redis test database."""
import os
from uuid import uuid4
from urllib.parse import urlparse

import pytest
import redis

import app_redis
import collective_intel


@pytest.fixture
def client():
    url = os.getenv("SENTINEL_TEST_REDIS_URL", "redis://127.0.0.1:6379/15")
    parsed = urlparse(url)
    assert parsed.hostname in {"127.0.0.1", "localhost"} and parsed.path == "/15"
    connection = redis.Redis.from_url(url, decode_responses=True, socket_timeout=3)
    assert connection.ping()
    yield connection
    connection.close()


@pytest.mark.parametrize("script", [collective_intel._TRUSTED_REPORT_LUA, app_redis._REPORT_LUA])
def test_new_contributions_do_not_renew_the_existing_reputation_window(client, script):
    prefix = "sentinel-test:" + uuid4().hex
    keys = [prefix + suffix for suffix in [":nonce1", ":reporter1", ":reputation", ":nonce2", ":reporter2"]]
    try:
        assert client.eval(script, 3, *keys[:3], 60, 60, 100, "category:PHISHING", 500) == 1
        assert client.expire(keys[2], 120)
        assert client.eval(script, 3, keys[3], keys[4], keys[2], 60, 60, 110, "category:PHISHING", 500) == 1
        assert 0 < client.ttl(keys[2]) <= 120
        assert client.hget(keys[2], "signals") == "2"
    finally:
        client.delete(*keys)


@pytest.mark.parametrize("script", [collective_intel._MODERATE_LUA, app_redis._MODERATE_PENDING_LUA])
def test_moderation_does_not_refresh_the_age_of_already_published_contributions(client, script):
    prefix = "sentinel-test:" + uuid4().hex
    keys = [prefix + suffix for suffix in [":pending", ":queue", ":reputation"]]
    try:
        client.hset(keys[0], mapping={"signals": 2, "category:PHISHING": 2})
        assert client.eval(script, 3, *keys, "category:PHISHING", "APPROVE", 100, 500, "fixture") == 1
        assert client.expire(keys[2], 120)
        assert client.eval(script, 3, *keys, "category:PHISHING", "APPROVE", 110, 500, "fixture") == 1
        assert 0 < client.ttl(keys[2]) <= 120
        assert client.hget(keys[2], "signals") == "2"
    finally:
        client.delete(*keys)
