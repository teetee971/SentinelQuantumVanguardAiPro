import asyncio
import os
from types import SimpleNamespace

os.environ.setdefault("INDICATOR_HASH_PEPPER", "indicator-test-pepper")
os.environ.setdefault("RATE_LIMIT_PEPPER", "rate-test-pepper")

from fastapi.testclient import TestClient

from collective_intel import (
    IndicatorType,
    IntelModerationDecision,
    IntelReportCategory,
    _confidence_tier,
    _moderate_pending,
    _read_reputation,
    _store_pending_report,
    indicator_fingerprint,
    normalize_indicator,
)
from app_redis import app


def test_indicator_normalization_is_deterministic_and_bounded():
    assert normalize_indicator(IndicatorType.DOMAIN, "Exämple.COM.") == "xn--exmple-cua.com"
    assert normalize_indicator(
        IndicatorType.URL,
        "HTTPS://Exämple.COM:443/path?q=1#fragment",
    ) == "https://xn--exmple-cua.com/path?q=1"
    assert normalize_indicator(
        IndicatorType.EMAIL,
        "Fraud.Box@Exämple.COM",
    ) == "Fraud.Box@xn--exmple-cua.com"
    assert normalize_indicator(IndicatorType.SHA256, "A" * 64) == "a" * 64


def test_invalid_or_credential_bearing_indicators_fail_closed():
    import pytest

    with pytest.raises(ValueError, match="invalid_domain"):
        normalize_indicator(IndicatorType.DOMAIN, "localhost")
    with pytest.raises(ValueError, match="userinfo_forbidden"):
        normalize_indicator(IndicatorType.URL, "https://user:pass@example.com/a")
    with pytest.raises(ValueError, match="invalid_sha256"):
        normalize_indicator(IndicatorType.SHA256, "not-a-hash")


def test_indicator_fingerprint_never_contains_raw_value():
    normalized = normalize_indicator(IndicatorType.EMAIL, "fraud@example.com")
    value = indicator_fingerprint(IndicatorType.EMAIL, normalized)
    assert value is not None
    assert normalized not in value
    assert len(value) == 64


class ReputationRedis:
    def __init__(self):
        self.read_keys = []

    async def hgetall(self, key):
        self.read_keys.append(key)
        return {
            "signals": "4",
            "last_seen": "100",
            "category:PHISHING": "3",
            "category:CREDENTIAL_THEFT": "1",
        }

    async def hset(self, *_args, **_kwargs):
        raise AssertionError("lookup must never mutate reputation")

    async def expire(self, *_args, **_kwargs):
        raise AssertionError("lookup must never extend reputation")


def test_reputation_lookup_is_read_only_and_exposes_freshness():
    redis = ReputationRedis()
    fake_app = SimpleNamespace(state=SimpleNamespace(redis=redis))
    result = asyncio.run(
        _read_reputation(fake_app, IndicatorType.DOMAIN, "a" * 64)
    )
    signals, status, categories, observed_at_ms, ttl_ms = result
    assert signals == 4
    assert status == "available"
    assert categories == ["PHISHING", "CREDENTIAL_THEFT"]
    assert observed_at_ms == 100_000
    assert ttl_ms == 180 * 86_400 * 1_000
    assert redis.read_keys == ["intel:reputation:v1:DOMAIN:" + "a" * 64]


class EvalRedis:
    def __init__(self, result):
        self.result = result
        self.calls = []

    async def eval(self, *args):
        self.calls.append(args)
        return self.result


def test_public_pending_report_never_writes_live_reputation():
    redis = EvalRedis(1)
    accepted = asyncio.run(
        _store_pending_report(
            redis,
            indicator_type=IndicatorType.DOMAIN,
            nonce_hash="a" * 64,
            reporter_hash="b" * 64,
            fingerprint="c" * 64,
            category=IntelReportCategory.PHISHING,
            now=1_789_484_000,
        )
    )
    assert accepted is True
    args = redis.calls[0]
    keys = [str(value) for value in args[2:6]]
    assert keys[0].startswith("intel:community:pending:dedupe:v1:")
    assert keys[1].startswith("intel:community:pending:reporter:v1:")
    assert keys[2].startswith("intel:community:pending:v1:DOMAIN:")
    assert keys[3] == "intel:community:moderation:v1"
    assert all("intel:reputation:" not in key for key in keys)


def test_single_observation_never_becomes_high_confidence():
    assert _confidence_tier(0) == "UNKNOWN"
    assert _confidence_tier(1) == "OBSERVED"
    assert _confidence_tier(2) == "OBSERVED"
    assert _confidence_tier(3) == "SUSPICIOUS"
    assert _confidence_tier(9) == "SUSPICIOUS"
    assert _confidence_tier(10) == "HIGH_CONFIDENCE"


def test_moderation_approval_promotes_exactly_one_pending_signal():
    redis = EvalRedis(1)
    result = asyncio.run(
        _moderate_pending(
            redis,
            indicator_type=IndicatorType.EMAIL,
            fingerprint="d" * 64,
            category=IntelReportCategory.BANK_IMPERSONATION,
            decision=IntelModerationDecision.APPROVE,
            now=1_789_484_100,
        )
    )
    assert result == "approved"
    args = redis.calls[0]
    assert args[2].startswith("intel:community:pending:v1:EMAIL:")
    assert args[3] == "intel:community:moderation:v1"
    assert args[4].startswith("intel:reputation:v1:EMAIL:")
    assert args[5] == "category:BANK_IMPERSONATION"
    assert args[6] == "APPROVE"


def test_lookup_degrades_cleanly_without_redis():
    with TestClient(app) as client:
        app.state.redis = None
        response = client.post(
            "/v1/intelligence/lookup",
            json={"indicator_type": "DOMAIN", "value": "example.com"},
        )
        assert response.status_code == 200
        payload = response.json()
        assert payload["risk_state"] == "UNKNOWN"
        assert payload["community_intelligence"] == "disabled"
        assert payload["enforcement_allowed"] is False


def test_public_report_fails_closed_without_indicator_secret(monkeypatch):
    monkeypatch.delenv("INDICATOR_HASH_PEPPER", raising=False)
    with TestClient(app) as client:
        app.state.redis = None
        response = client.post(
            "/v1/intelligence/report-public",
            json={
                "indicator_type": "DOMAIN",
                "value": "example.com",
                "category": "PHISHING",
                "client_nonce": "0123456789abcdef",
            },
        )
        assert response.status_code == 503
        assert response.json()["detail"] == "Signalement intelligence non configuré"
