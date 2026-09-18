import os
from types import SimpleNamespace

os.environ.setdefault("PHONE_HASH_PEPPER", "test-pepper-not-for-production")
os.environ.setdefault("HIGH_RISK_COUNTRIES", "MV,SO,VU")

from fastapi.testclient import TestClient

from app_redis import (
    Action,
    VerificationStatus,
    _client_rate_fingerprint,
    _phone_fingerprint,
    _rate_limit,
    _redis_replay_guard_status,
    _reporter_dedupe_hash,
    _risk_decision,
    _store_report_atomically,
    _store_pending_report_atomically,
    ReportCategory,
    app,
)


def test_wangiri_combination_is_blocked():
    score, action, reasons = _risk_decision(
        caller_country="MV",
        recipient_country="FR",
        ring_duration_ms=900,
        verification_status=VerificationStatus.FAILED,
        signals=0,
        burst_count=1,
    )
    assert score == 100
    assert action is Action.BLOCK
    assert any("Wangiri" in reason for reason in reasons)


def test_country_alone_never_blocks():
    score, action, _ = _risk_decision(
        caller_country="MV",
        recipient_country="FR",
        ring_duration_ms=None,
        verification_status=VerificationStatus.UNKNOWN,
        signals=0,
        burst_count=1,
    )
    assert score == 35
    assert action is Action.ALLOW


def test_verified_domestic_call_is_allowed():
    score, action, _ = _risk_decision(
        caller_country="FR",
        recipient_country="FR",
        ring_duration_ms=10_000,
        verification_status=VerificationStatus.VERIFIED,
        signals=0,
        burst_count=1,
    )
    assert score == 0
    assert action is Action.ALLOW


def test_phone_fingerprint_does_not_contain_number():
    value = _phone_fingerprint("+33612345678")
    assert value
    assert "+33612345678" not in value
    assert len(value) == 64


def test_evaluation_degrades_without_redis():
    with TestClient(app) as client:
        app.state.redis = None
        response = client.post(
            "/v1/evaluate-call",
            json={
                "caller_number": "+33612345678",
                "recipient_country": "fr",
                "ring_duration_ms": 10_000,
                "verification_status": "UNKNOWN",
            },
        )
        assert response.status_code == 200
        payload = response.json()
        assert payload["action"] == "ALLOW"
        assert payload["community_intelligence"] == "disabled"


def test_invalid_number_is_rejected_by_risk_engine():
    with TestClient(app) as client:
        app.state.redis = None
        response = client.post(
            "/v1/evaluate-call",
            json={
                "caller_number": "not-a-phone",
                "recipient_country": "FR",
                "ring_duration_ms": 500,
            },
        )
        assert response.status_code == 200
        assert response.json()["action"] == "BLOCK"
        assert response.json()["risk_score"] == 80


class FakePipeline:
    def __init__(self, counts):
        self.counts = counts

    def incr(self, _key):
        return self

    def expire(self, _key, _ttl):
        return self

    async def execute(self):
        results = []
        for count in self.counts:
            results.extend([count, True])
        return results


class FakeRedis:
    def __init__(self, counts):
        self.counts = counts

    def pipeline(self, transaction=True):
        assert transaction is True
        return FakePipeline(self.counts)


def test_rate_limit_is_scoped_and_returns_retry_after(monkeypatch):
    monkeypatch.setenv("EVALUATE_RATE_LIMIT_PER_MINUTE", "1")
    monkeypatch.setenv("GLOBAL_RATE_LIMIT_PER_MINUTE", "100")
    request = SimpleNamespace(
        app=SimpleNamespace(state=SimpleNamespace(redis=FakeRedis([2, 2]))),
        client=SimpleNamespace(host="203.0.113.7"),
    )

    import asyncio
    from fastapi import HTTPException

    try:
        asyncio.run(
            _rate_limit(
                request,
                endpoint="evaluate-call",
                per_client_env="EVALUATE_RATE_LIMIT_PER_MINUTE",
                per_client_default=30,
            )
        )
        assert False, "rate limiter should reject the request"
    except HTTPException as exc:
        assert exc.status_code == 429
        assert 1 <= int(exc.headers["Retry-After"]) <= 60


def test_rate_limit_fingerprint_never_contains_raw_ip():
    request = SimpleNamespace(client=SimpleNamespace(host="203.0.113.8"))
    fingerprint = _client_rate_fingerprint(request)
    assert "203.0.113.8" not in fingerprint
    assert len(fingerprint) == 24


class ReplayProbeRedis:
    def __init__(self, responses, *, delete_error=False):
        self.responses = list(responses)
        self.delete_error = delete_error
        self.set_calls = []
        self.deleted = []

    async def set(self, key, value, *, nx, px):
        self.set_calls.append((key, value, nx, px))
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    async def delete(self, key):
        self.deleted.append(key)
        if self.delete_error:
            from redis.exceptions import RedisError
            raise RedisError("delete failed")
        return 1


def _probe_app(redis):
    return SimpleNamespace(
        state=SimpleNamespace(
            redis=redis,
            redis_replay_probe_status=None,
            redis_replay_probe_checked_at=0.0,
        )
    )


def test_replay_probe_requires_atomic_set_nx_px_and_caches_success():
    import asyncio

    redis = ReplayProbeRedis([True, None])
    probe_app = _probe_app(redis)
    first = asyncio.run(_redis_replay_guard_status(probe_app))
    second = asyncio.run(_redis_replay_guard_status(probe_app))

    assert first == second == "available"
    assert len(redis.set_calls) == 2
    key, value, nx, px = redis.set_calls[0]
    assert key.startswith("health:replay:v1:")
    assert value == "1"
    assert nx is True
    assert px == 15_000
    assert redis.set_calls[1][0] == key
    assert redis.deleted == [key]


def test_replay_probe_fails_closed_when_second_set_is_accepted():
    import asyncio

    redis = ReplayProbeRedis(["OK", "OK"])
    result = asyncio.run(_redis_replay_guard_status(_probe_app(redis)))

    assert result == "degraded"
    assert len(redis.deleted) == 1


def test_replay_probe_fails_closed_on_redis_or_cleanup_error():
    import asyncio
    from redis.exceptions import RedisError

    unavailable = ReplayProbeRedis([RedisError("offline")])
    assert asyncio.run(_redis_replay_guard_status(_probe_app(unavailable))) == "degraded"

    cleanup_failure = ReplayProbeRedis([True, None], delete_error=True)
    assert asyncio.run(_redis_replay_guard_status(_probe_app(cleanup_failure))) == "degraded"


class AtomicReportRedis:
    def __init__(self, result):
        self.result = result
        self.calls = []

    async def eval(self, *args):
        self.calls.append(args)
        return self.result


def test_reporter_dedupe_hash_is_scoped_and_contains_no_raw_identifier():
    request = SimpleNamespace(client=SimpleNamespace(host="203.0.113.19"))
    phone_fingerprint = "f" * 64
    first = _reporter_dedupe_hash(
        request,
        phone_fingerprint=phone_fingerprint,
        category=ReportCategory.WANGIRI,
        secret="test-report-key",
    )
    second = _reporter_dedupe_hash(
        request,
        phone_fingerprint=phone_fingerprint,
        category=ReportCategory.SPOOFING,
        secret="test-report-key",
    )
    assert first != second
    assert "203.0.113.19" not in first
    assert phone_fingerprint not in first
    assert len(first) == 64


def test_atomic_report_script_deduplicates_and_uses_only_hashed_keys():
    import asyncio

    redis = AtomicReportRedis(1)
    accepted = asyncio.run(
        _store_report_atomically(
            redis,
            nonce_hash="a" * 64,
            reporter_hash="b" * 64,
            phone_fingerprint="c" * 64,
            category=ReportCategory.WANGIRI,
            now=1_789_484_000,
        )
    )
    assert accepted is True
    assert len(redis.calls) == 1
    args = redis.calls[0]
    assert args[1] == 3
    assert args[2].startswith("phone:report:dedupe:v2:")
    assert args[3].startswith("phone:report:reporter:v1:")
    assert args[4].startswith("phone:spam:v2:")
    assert args[-2] == "category:WANGIRI"
    assert "+33" not in ":".join(map(str, args))

    duplicate = AtomicReportRedis(0)
    assert asyncio.run(
        _store_report_atomically(
            duplicate,
            nonce_hash="a" * 64,
            reporter_hash="b" * 64,
            phone_fingerprint="c" * 64,
            category=ReportCategory.WANGIRI,
            now=1_789_484_000,
        )
    ) is False


def test_pending_public_report_never_writes_reputation_key():
    import asyncio

    redis = AtomicReportRedis(1)
    accepted = asyncio.run(
        _store_pending_report_atomically(
            redis,
            nonce_hash="d" * 64,
            reporter_hash="e" * 64,
            phone_fingerprint="f" * 64,
            category=ReportCategory.SPOOFING,
            now=1_789_484_100,
        )
    )

    assert accepted is True
    assert len(redis.calls) == 1
    args = redis.calls[0]
    assert args[1] == 4
    keys = [str(value) for value in args[2:6]]
    assert keys[0].startswith("phone:community:pending:dedupe:v1:")
    assert keys[1].startswith("phone:community:pending:reporter:v1:")
    assert keys[2].startswith("phone:community:pending:v1:")
    assert keys[3] == "phone:community:moderation:v1"
    assert all("phone:spam:" not in key for key in keys)
    assert "+33" not in ":".join(map(str, args))


def test_public_report_endpoint_fails_closed_without_server_secret(monkeypatch):
    monkeypatch.delenv("PUBLIC_REPORT_PEPPER", raising=False)
    monkeypatch.delenv("PHONE_HASH_PEPPER", raising=False)
    monkeypatch.delenv("REDIS_URL", raising=False)
    with TestClient(app) as client:
        response = client.post(
            "/v1/report-call-public",
            json={
                "caller_number": "+33612345678",
                "recipient_country": "FR",
                "category": "WANGIRI",
                "client_nonce": "0123456789abcdef",
            },
        )
        assert response.status_code == 503
        assert response.json()["detail"] == "Signalement public non configuré"
