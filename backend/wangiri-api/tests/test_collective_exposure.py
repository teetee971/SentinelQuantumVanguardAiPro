import asyncio
import os
from types import SimpleNamespace

os.environ.setdefault("INDICATOR_HASH_PEPPER", "indicator-test-pepper")
os.environ.setdefault("EXPOSURE_HASH_PEPPER", "exposure-test-pepper")
os.environ.setdefault("EXPOSURE_API_KEY", "exposure-test-key")
os.environ.setdefault("RATE_LIMIT_PEPPER", "rate-test-pepper")

from fastapi.testclient import TestClient

from app_redis import app
import collective_exposure as exposure_module
from collective_exposure import (
    _EXPOSURE_REPORT_LUA,
    _MAX_EXPOSURE_OBSERVATIONS_PER_RECORD,
    ExposureChannel,
    _read_matches,
    _record_fingerprint,
    _store_exposure,
    exposure_configuration_status,
    subject_fingerprint,
)
from collective_intel import IndicatorType


def test_subject_fingerprint_hides_opaque_token():
    token = "A" * 43
    fingerprint = subject_fingerprint(token)
    assert fingerprint is not None
    assert len(fingerprint) == 64
    assert token not in fingerprint


class EvalRedis:
    def __init__(self):
        self.calls = []

    async def eval(self, *args):
        self.calls.append(args)
        return 1


def test_record_fingerprint_is_subject_scoped_without_subject_prefix():
    indicator_fp = "b" * 64
    first = _record_fingerprint(
        subject_fp="a" * 64,
        indicator_type=IndicatorType.DOMAIN,
        indicator_fp=indicator_fp,
    )
    second = _record_fingerprint(
        subject_fp="c" * 64,
        indicator_type=IndicatorType.DOMAIN,
        indicator_fp=indicator_fp,
    )
    assert first is not None
    assert second is not None
    assert first != second
    assert "a" * 64 not in first
    assert "c" * 64 not in second


def test_exposure_storage_is_age_bounded_and_fingerprint_only():
    redis = EvalRedis()
    accepted = asyncio.run(
        _store_exposure(
            redis,
            record_fp="e" * 64,
            channel=ExposureChannel.EMAIL,
            nonce_fp="c" * 64,
            event_fp="d" * 64,
            now=1_789_484_500,
        )
    )
    assert accepted is True
    serialized = repr(redis.calls[0])
    assert "intel:exposure:v1:" in serialized
    assert "intel:exposure:index:v1:" not in serialized
    assert "a" * 64 not in serialized
    assert "EMAIL:" in serialized
    assert "ZREMRANGEBYSCORE" in _EXPOSURE_REPORT_LUA
    assert "ZREMRANGEBYRANK" in _EXPOSURE_REPORT_LUA
    assert "HSET" not in _EXPOSURE_REPORT_LUA


class ExposureReadPipeline:
    def __init__(self, result_map):
        self.result_map = result_map
        self.calls = []

    def zrangebyscore(
        self, key, minimum, maximum, start=None, num=None, withscores=False
    ):
        assert withscores is True
        assert start == 0
        assert num == _MAX_EXPOSURE_OBSERVATIONS_PER_RECORD + 1
        self.calls.append(("zrangebyscore", key, minimum, maximum))
        return self

    def ttl(self, key):
        self.calls.append(("ttl", key))
        return self

    async def execute(self):
        results = []
        for call in self.calls:
            action, key = call[0], call[1]
            observations, ttl = self.result_map.get(key, ([], -2))
            if action == "zrangebyscore":
                minimum = float(call[2])
                filtered = [
                    (member, score)
                    for member, score in observations
                    if float(score) >= minimum
                ]
                results.append(filtered)
            else:
                results.append(ttl)
        return results


class ExposureReadRedis:
    def __init__(self, result_map):
        self.result_map = result_map
        self.pipeline_instance = None

    def pipeline(self, transaction=False):
        assert transaction is False
        self.pipeline_instance = ExposureReadPipeline(self.result_map)
        return self.pipeline_instance


def _exposure_key(subject_fp, indicator_fp):
    record_fp = _record_fingerprint(
        subject_fp=subject_fp,
        indicator_type=IndicatorType.DOMAIN,
        indicator_fp=indicator_fp,
    )
    assert record_fp is not None
    return f"intel:exposure:v1:{record_fp}"


def _member(channel, observed_at, fill):
    return f"{channel}:{observed_at}:{fill * 64}"


def test_exposure_lookup_uses_only_current_retention_window(monkeypatch):
    now = 1_800_000_000
    monkeypatch.setattr(exposure_module.time, "time", lambda: now)
    subject_fp = "a" * 64
    indicator_fp = "b" * 64
    key = _exposure_key(subject_fp, indicator_fp)
    old = now - (30 * 86_400) - 1
    redis = ExposureReadRedis({
        key: ([
            (_member("SMS", old, "f"), old),
            (_member("EMAIL", now - 100, "c"), now - 100),
            (_member("EMAIL", now - 50, "d"), now - 50),
            (_member("WEB", now - 20, "e"), now - 20),
        ], 30 * 86_400 - 20)
    })
    fake_app = SimpleNamespace(state=SimpleNamespace(redis=redis))
    status_name, matches = asyncio.run(
        _read_matches(
            fake_app,
            subject_fp=subject_fp,
            indicators=[(IndicatorType.DOMAIN, indicator_fp)],
        )
    )
    assert status_name == "available"
    assert len(matches) == 1
    assert matches[0]["signals"] == 3
    assert matches[0]["channels"] == ["EMAIL", "WEB"]
    assert matches[0]["first_seen"] == now - 100
    assert matches[0]["last_seen"] == now - 20
    assert matches[0]["remaining_ttl_ms"] == (30 * 86_400 - 100) * 1_000
    assert redis.pipeline_instance.calls[0][2] == now - (30 * 86_400) + 1


def test_exposure_lookup_degrades_on_malformed_observation(monkeypatch):
    now = 1_800_000_000
    monkeypatch.setattr(exposure_module.time, "time", lambda: now)
    subject_fp = "a" * 64
    indicator_fp = "b" * 64
    key = _exposure_key(subject_fp, indicator_fp)
    redis = ExposureReadRedis({
        key: ([(_member("INVALID", now - 10, "c"), now - 10)], 3600)
    })
    status_name, matches = asyncio.run(
        _read_matches(
            SimpleNamespace(state=SimpleNamespace(redis=redis)),
            subject_fp=subject_fp,
            indicators=[(IndicatorType.DOMAIN, indicator_fp)],
        )
    )
    assert status_name == "degraded"
    assert matches == []


def test_exposure_lookup_degrades_when_record_has_no_ttl(monkeypatch):
    now = 1_800_000_000
    monkeypatch.setattr(exposure_module.time, "time", lambda: now)
    subject_fp = "a" * 64
    indicator_fp = "b" * 64
    key = _exposure_key(subject_fp, indicator_fp)
    redis = ExposureReadRedis({
        key: ([(_member("EMAIL", now - 10, "c"), now - 10)], -1)
    })
    status_name, matches = asyncio.run(
        _read_matches(
            SimpleNamespace(state=SimpleNamespace(redis=redis)),
            subject_fp=subject_fp,
            indicators=[(IndicatorType.DOMAIN, indicator_fp)],
        )
    )
    assert status_name == "degraded"
    assert matches == []



def test_exposure_lookup_degrades_when_record_ttl_is_too_short(monkeypatch):
    now = 1_800_000_000
    monkeypatch.setattr(exposure_module.time, "time", lambda: now)
    subject_fp = "a" * 64
    indicator_fp = "b" * 64
    key = _exposure_key(subject_fp, indicator_fp)
    redis = ExposureReadRedis({
        key: ([(_member("EMAIL", now - 10, "c"), now - 10)], 60)
    })
    status_name, matches = asyncio.run(
        _read_matches(
            SimpleNamespace(state=SimpleNamespace(redis=redis)),
            subject_fp=subject_fp,
            indicators=[(IndicatorType.DOMAIN, indicator_fp)],
        )
    )
    assert status_name == "degraded"
    assert matches == []

def test_exposure_report_requires_server_authentication():
    with TestClient(app) as client:
        response = client.post(
            "/v1/intelligence/exposures/report",
            json={
                "subject_token": "A" * 43,
                "indicator": {"indicator_type": "DOMAIN", "value": "example.com"},
                "channel": "EMAIL",
                "client_nonce": "0123456789abcdef",
            },
        )
        assert response.status_code == 401


def test_exposure_lookup_requires_server_authentication():
    with TestClient(app) as client:
        response = client.post(
            "/v1/intelligence/exposures/lookup",
            json={
                "subject_token": "A" * 43,
                "indicators": [{"indicator_type": "DOMAIN", "value": "example.com"}],
            },
        )
        assert response.status_code == 401


def test_exposure_lookup_preserves_unavailable_truth_state(monkeypatch):
    monkeypatch.setenv("EXPOSURE_API_KEY", "exposure-test-key")
    with TestClient(app) as client:
        app.state.redis = None
        response = client.post(
            "/v1/intelligence/exposures/lookup",
            headers={"X-Exposure-Key": "exposure-test-key"},
            json={
                "subject_token": "A" * 43,
                "indicators": [{"indicator_type": "DOMAIN", "value": "example.com"}],
            },
        )
        assert response.status_code == 200
        body = response.json()
        assert body["exposure_intelligence"] == "disabled"
        assert body["matches"] == []
        assert body["match_state"] == "UNAVAILABLE"
        assert body["enforcement_allowed"] is False

