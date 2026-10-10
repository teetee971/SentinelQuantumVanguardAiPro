import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import extract_device_ids  # noqa: E402


DEVICE_CLAIM = "https://sentinel-voice.onrender.com/claims/device_ids"


def test_extract_device_ids_reads_the_namespaced_auth0_claim():
    claims = {DEVICE_CLAIM: ["phone-1", "tablet-2"]}

    assert extract_device_ids(claims, DEVICE_CLAIM) == ["phone-1", "tablet-2"]


def test_extract_device_ids_rejects_non_string_values():
    claims = {DEVICE_CLAIM: ["phone-1", 42, ""]}

    assert extract_device_ids(claims, DEVICE_CLAIM) == ["phone-1"]
