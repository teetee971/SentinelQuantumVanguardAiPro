import asyncio
import os
from types import SimpleNamespace

os.environ.setdefault("INDICATOR_HASH_PEPPER", "indicator-test-pepper")
os.environ.setdefault("RATE_LIMIT_PEPPER", "rate-test-pepper")

from fastapi.testclient import TestClient

from app_redis import app
from collective_exposure import (\n    ExposureChannel,\n    _read_matches,\n    _record_fingerprint,\n    _store_exposure,\n    subject_fingerprint,\n)
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


def test_exposure_storage_uses_fingerprints_only():
    redis = EvalRedis()
    accepted = asyncio.run(
        _store_exposure(
            redis,
            record_fp="e" * 64,
            indicator_type=IndicatorType.DOMAIN,
            indicator_fp="b" * 64,
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
    assert "channel:EMAIL" in serialized


class ExposureReadPipeline:
    def __init__(self, result_map):
        self.result_map = result_map
        self.calls = []

    def hgetall(self, key):
        self.calls.append(("hgetall", key))
        return self

    def ttl(self, key):
        self.calls.append(("ttl", key))
        return self

    async def execute(self):
        results = []
        for action, key in self.calls:
            data, ttl = self.result_map.get(key, ({}, -2))
            results.append(data if action == "hgetall" else ttl)
        return results


class ExposureReadRedis:
    def __init__(self, result_map):
        self.result_map = result_map
        self.pipeline_instance = None

    def pipeline(self, transaction=False):
        assert transaction is False
        self.pipeline_instance = ExposureReadPipeline(self.result_map)
        return self.pipeline_instance


def test_exposure_lookup_is_read_only():
    subject_fp = "a" * 64
    indicator_fp = "b" * 64
    record_fp = _record_fingerprint(
        subject_fp=subject_fp,
        indicator_type=IndicatorType.DOMAIN,
        indicator_fp=indicator_fp,
    )
    assert record_fp is not None
    key = f"intel:exposure:v1:{record_fp}"
    redis = ExposureReadRedis({
        key: ({
            "indicator_type": "DOMAIN",
            "indicator_fingerprint": indicator_fp,
            "signals": "3",
            "first_seen": "100",
            "last_seen": "200",
            "channel:EMAIL": "2",
            "channel:WEB": "1",
        }, 3600)
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
    assert matches[0]["channels"] == ["EMAIL", "WEB"]
    assert matches[0]["remaining_ttl_ms"] == 3_600_000


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
    monkeypatch.setenv("REPORT_API_KEY", "trusted-report-key")
    with TestClient(app) as client:
        app.state.redis = None
        response = client.post(
            "/v1/intelligence/exposures/lookup",
            headers={"X-Report-Key": "trusted-report-key"},
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

