import os

os.environ.setdefault("PHONE_HASH_PEPPER", "test-pepper-not-for-production")
os.environ.setdefault("HIGH_RISK_COUNTRIES", "MV,SO,VU")

from fastapi.testclient import TestClient

from app_redis import (
    Action,
    VerificationStatus,
    _phone_fingerprint,
    _risk_decision,
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
