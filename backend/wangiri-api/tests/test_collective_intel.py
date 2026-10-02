import asyncio
import os
from types import SimpleNamespace

os.environ.setdefault("INDICATOR_HASH_PEPPER", "indicator-test-pepper")
os.environ.setdefault("RATE_LIMIT_PEPPER", "rate-test-pepper")

from fastapi.testclient import TestClient

from collective_intel import (
    EvidenceStrength,
    IndicatorType,
    IntelModerationDecision,
    IntelReportCategory,
    RelationshipType,
    _campaign_candidate_fingerprint,
    _confidence_tier,
    _moderate_pending,
    _read_graph,
    _read_reputation,
    _relationship_edge_id,
    _store_pending_report,
    _store_trusted_relationship,
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


def test_symmetric_relationship_edge_id_is_order_independent():
    left = "a" * 64
    right = "b" * 64
    forward = _relationship_edge_id(
        IndicatorType.DOMAIN,
        left,
        IndicatorType.URL,
        right,
        RelationshipType.SHARES_INFRASTRUCTURE,
    )
    reverse = _relationship_edge_id(
        IndicatorType.URL,
        right,
        IndicatorType.DOMAIN,
        left,
        RelationshipType.SHARES_INFRASTRUCTURE,
    )
    assert forward == reverse


def test_directed_relationship_edge_id_preserves_direction():
    left = "a" * 64
    right = "b" * 64
    forward = _relationship_edge_id(
        IndicatorType.URL,
        left,
        IndicatorType.URL,
        right,
        RelationshipType.REDIRECTS_TO,
    )
    reverse = _relationship_edge_id(
        IndicatorType.URL,
        right,
        IndicatorType.URL,
        left,
        RelationshipType.REDIRECTS_TO,
    )
    assert forward != reverse


def test_campaign_candidate_fingerprint_is_deterministic_not_a_verdict():
    nodes = {"DOMAIN:" + "a" * 64, "URL:" + "b" * 64}
    first = _campaign_candidate_fingerprint(nodes)
    second = _campaign_candidate_fingerprint(set(reversed(sorted(nodes))))
    assert first == second
    assert first is not None
    assert len(first) == 64


def test_trusted_relationship_storage_uses_fingerprints_only():
    redis = EvalRedis(1)
    accepted = asyncio.run(
        _store_trusted_relationship(
            redis,
            indicator_source_type=IndicatorType.DOMAIN,
            source_fingerprint="a" * 64,
            indicator_target_type=IndicatorType.URL,
            target_fingerprint="b" * 64,
            relationship_type=RelationshipType.REFERENCES,
            evidence_strength=EvidenceStrength.E2,
            nonce_hash="c" * 64,
            reporter_hash="d" * 64,
            edge_id="e" * 64,
            now=1_789_484_200,
        )
    )
    assert accepted is True
    serialized = repr(redis.calls[0])
    assert "example.com" not in serialized
    assert "https://" not in serialized
    assert "intel:graph:edge:v1:" in serialized
    assert "intel:graph:adj:v1:DOMAIN:" in serialized
    assert "intel:graph:adj:v1:URL:" in serialized


class GraphRedis:
    def __init__(self):
        self.pruned = []
        self.ranges = []

    async def zremrangebyscore(self, key, minimum, maximum):
        self.pruned.append((key, minimum, maximum))
        return 0

    async def zrange(self, key, start, stop):
        self.ranges.append((key, start, stop))
        return ["edge-candidate", "edge-context"]

    async def hgetall(self, key):
        if key.endswith("edge-candidate"):
            return {
                "source_type": "DOMAIN",
                "source_fingerprint": "a" * 64,
                "target_type": "URL",
                "target_fingerprint": "b" * 64,
                "relationship_type": "SAME_CAMPAIGN_CANDIDATE",
                "evidence_strength": "E2",
                "evidence_rank": "2",
                "signals": "2",
                "first_seen": "100",
                "last_seen": "200",
            }
        return {
            "source_type": "DOMAIN",
            "source_fingerprint": "a" * 64,
            "target_type": "SHA256",
            "target_fingerprint": "c" * 64,
            "relationship_type": "DELIVERS_FILE",
            "evidence_strength": "E4",
            "evidence_rank": "4",
            "signals": "1",
            "first_seen": "150",
            "last_seen": "250",
        }


def test_graph_lookup_builds_only_candidate_cluster_from_explicit_candidate_edges():
    redis = GraphRedis()
    fake_app = SimpleNamespace(state=SimpleNamespace(redis=redis))
    status_name, neighbors, candidate = asyncio.run(
        _read_graph(
            fake_app,
            indicator_type=IndicatorType.DOMAIN,
            fingerprint="a" * 64,
            max_neighbors=25,
        )
    )
    assert status_name == "available"
    assert len(neighbors) == 2
    assert candidate == _campaign_candidate_fingerprint(
        {"DOMAIN:" + "a" * 64, "URL:" + "b" * 64}
    )
    assert all("value" not in neighbor for neighbor in neighbors)
    assert len(redis.pruned) == 1
    assert redis.pruned[0][0].startswith("intel:graph:adj:v1:DOMAIN:")
    assert redis.pruned[0][1] == "-inf"
    assert redis.ranges == [
        (redis.pruned[0][0], 0, 24)
    ]


def test_graph_lookup_requires_server_authentication():
    with TestClient(app) as client:
        response = client.post(
            "/v1/intelligence/graph/lookup",
            json={
                "indicator_type": "DOMAIN",
                "value": "example.com",
                "max_neighbors": 5,
            },
        )
        assert response.status_code == 401


def test_self_relationship_is_rejected_before_graph_write(monkeypatch):
    monkeypatch.setenv("REPORT_API_KEY", "trusted-report-key")
    with TestClient(app) as client:
        app.state.redis = None
        response = client.post(
            "/v1/intelligence/relationships/report",
            headers={"X-Report-Key": "trusted-report-key"},
            json={
                "source": {
                    "indicator_type": "DOMAIN",
                    "value": "example.com",
                },
                "target": {
                    "indicator_type": "DOMAIN",
                    "value": "example.com",
                },
                "relationship_type": "SAME_CAMPAIGN_CANDIDATE",
                "evidence_strength": "E2",
                "client_nonce": "0123456789abcdef",
            },
        )
        assert response.status_code == 422
        assert response.json()["detail"] == "self_relationship_forbidden"
