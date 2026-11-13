import base64
import hashlib
import json
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

sys.path.insert(0, str(Path(__file__).parents[1]))

from sentinel_cti.crypto import P256_ORDER, encode_base64url
from sentinel_cti.envelope import (
    STIC_DOMAIN,
    EnvelopeVerificationError,
    TrustKey,
    TrustRegistry,
    _signed_bytes,
    verify_envelope,
)
from sentinel_cti.jcs import JcsError, MAX_DOCUMENT_BYTES, canonicalize_json


NOW = datetime(2026, 11, 13, 12, 0, tzinfo=timezone.utc)


class SticTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.private = ec.generate_private_key(ec.SECP256R1())
        cls.public = cls.private.public_key().public_bytes(
            encoding=serialization.Encoding.X962,
            format=serialization.PublicFormat.UncompressedPoint,
        )
        cls.registry = TrustRegistry({
            "prod-signing-1": TrustKey(
                key_id="prod-signing-1",
                environment="prod",
                flows=frozenset({"threat-intelligence"}),
                capabilities=frozenset({"cisa-kev", "malware-metadata"}),
                public_key=cls.public,
            ),
            "revoked-1": TrustKey(
                key_id="revoked-1",
                environment="prod",
                flows=frozenset({"threat-intelligence"}),
                capabilities=frozenset({"cisa-kev"}),
                public_key=cls.public,
                revoked=True,
            ),
            "staging-1": TrustKey(
                key_id="staging-1",
                environment="staging",
                flows=frozenset({"threat-intelligence"}),
                capabilities=frozenset({"cisa-kev"}),
                public_key=cls.public,
            ),
        })

    def make_envelope(self, **changes):
        payload = {"indicator": "CVE-2026-1234", "severity": "high"}
        header = {
            "version": "1.1",
            "domain": STIC_DOMAIN,
            "environment": "prod",
            "flow": "threat-intelligence",
            "capabilities": ["cisa-kev"],
            "issued_at": "2026-11-13T10:00:00Z",
            "valid_from": "2026-11-13T10:00:00Z",
            "expires_at": "2026-11-14T10:00:00Z",
            "payload_sha256": hashlib.sha256(canonicalize_json(json.dumps(payload))).hexdigest(),
            "payload_size": len(canonicalize_json(json.dumps(payload))),
            "key_id": "prod-signing-1",
        }
        envelope = {"header": header, "payload": payload, "signature": ""}
        for path, value in changes.items():
            if path.startswith("header."):
                header[path[7:]] = value
            elif path.startswith("payload."):
                payload[path[8:]] = value
            else:
                envelope[path] = value
        if "payload" in changes:
            payload = changes["payload"]
            envelope["payload"] = payload
        canonical_payload = canonicalize_json(json.dumps(payload))
        header["payload_sha256"] = hashlib.sha256(canonical_payload).hexdigest()
        header["payload_size"] = len(canonical_payload)
        der = self.private.sign(_signed_bytes(header, payload), ec.ECDSA(hashes.SHA256()))
        r, s = decode_dss_signature(der)
        if s > P256_ORDER // 2:
            s = P256_ORDER - s
        signature = r.to_bytes(32, "big") + s.to_bytes(32, "big")
        envelope["signature"] = changes.get("signature", encode_base64url(signature))
        return envelope

    def test_valid_signature_and_immutable_result(self):
        result = verify_envelope(self.make_envelope(), self.registry, now=NOW)
        self.assertEqual(result.key_id, "prod-signing-1")
        with self.assertRaises(TypeError):
            result.header["environment"] = "staging"
        with self.assertRaises(AttributeError):
            result.header["capabilities"].append("admin")
        with self.assertRaises(TypeError):
            result.payload["severity"] = "low"

    def test_signature_and_header_payload_tampering(self):
        for mutation in ("signature", "header.domain", "payload.severity"):
            envelope = self.make_envelope()
            if mutation == "signature":
                envelope["signature"] = encode_base64url(bytes.fromhex("00" * 64))
            elif mutation == "header.domain":
                envelope["header"]["domain"] = "OTHER"
            else:
                envelope["payload"]["severity"] = "low"
            with self.subTest(mutation=mutation), self.assertRaises(EnvelopeVerificationError):
                verify_envelope(envelope, self.registry, now=NOW)

    def test_high_s_bad_base64_and_wrong_signature_size(self):
        envelope = self.make_envelope()
        raw = __import__("sentinel_cti.crypto", fromlist=["decode_base64url"]).decode_base64url(envelope["signature"])
        r = int.from_bytes(raw[:32], "big")
        s = P256_ORDER - int.from_bytes(raw[32:], "big")
        envelope["signature"] = encode_base64url(r.to_bytes(32, "big") + s.to_bytes(32, "big"))
        with self.assertRaisesRegex(EnvelopeVerificationError, "signature"):
            verify_envelope(envelope, self.registry, now=NOW)
        for bad in ("not base64!", "AAAA", "AA="):
            envelope = self.make_envelope(signature=bad)
            with self.subTest(bad=bad), self.assertRaises(EnvelopeVerificationError):
                verify_envelope(envelope, self.registry, now=NOW)

    def test_registry_authorization_and_environment(self):
        for key_id, header_changes in (
            ("missing", {"header.key_id": "missing"}),
            ("revoked", {"header.key_id": "revoked-1"}),
            ("cross-environment", {"header.key_id": "staging-1"}),
            ("wrong-environment", {"header.environment": "staging"}),
            ("wrong-flow", {"header.flow": "other-flow"}),
            ("capability", {"header.capabilities": ["admin"]}),
        ):
            with self.subTest(key_id=key_id), self.assertRaises(EnvelopeVerificationError):
                verify_envelope(self.make_envelope(**header_changes), self.registry, now=NOW)

    def test_temporal_bounds_and_utc(self):
        for changes in (
            {"header.issued_at": "2026-11-13T12:00:01Z"},
            {"header.valid_from": "2026-11-13T13:00:00Z"},
            {"header.expires_at": "2026-11-13T11:00:00Z"},
            {"header.expires_at": "2026-11-13T10:00:00+00:00"},
        ):
            with self.subTest(changes=changes), self.assertRaises(EnvelopeVerificationError):
                verify_envelope(self.make_envelope(**changes), self.registry, now=NOW)

    def test_payload_size_and_digest_are_bound(self):
        envelope = self.make_envelope()
        envelope["header"]["payload_size"] += 1
        with self.assertRaisesRegex(EnvelopeVerificationError, "size"):
            verify_envelope(envelope, self.registry, now=NOW)
        envelope = self.make_envelope()
        envelope["header"]["payload_sha256"] = "0" * 64
        with self.assertRaisesRegex(EnvelopeVerificationError, "digest"):
            verify_envelope(envelope, self.registry, now=NOW)

    def test_duplicate_keys_invalid_unicode_and_unsafe_numbers(self):
        for raw in (
            b'{"header":{},"header":{},"payload":{},"signature":"AA"}',
            b'{"header":{},"payload":"\\ud800","signature":"AA"}',
            b'{"header":{},"payload":{"n":9007199254740992},"signature":"AA"}',
        ):
            with self.subTest(raw=raw), self.assertRaises(Exception):
                verify_envelope(raw, self.registry, now=NOW)

    def test_jcs_sorting_and_depth_limit(self):
        self.assertEqual(canonicalize_json('{"b":1,"a":2}'), b'{"a":2,"b":1}')
        nested = "0"
        for _ in range(34):
            nested = "[" + nested + "]"
        with self.assertRaises(JcsError):
            canonicalize_json(nested)
        with self.assertRaises(JcsError):
            canonicalize_json('{"n":9007199254740992.0}')
        with self.assertRaises(JcsError):
            canonicalize_json("\"" + ("x" * MAX_DOCUMENT_BYTES) + "\"")

    def test_registry_rejects_wrong_curve_or_point(self):
        with self.assertRaises(ValueError):
            TrustRegistry({
                "bad-point": TrustKey(
                    key_id="bad-point",
                    environment="prod",
                    flows=frozenset({"threat-intelligence"}),
                    capabilities=frozenset({"cisa-kev"}),
                    public_key=b"\x04" + (b"\x00" * 64),
                )
            })

    def test_no_durable_rollback_claim(self):
        first = verify_envelope(self.make_envelope(), self.registry, now=NOW)
        second = verify_envelope(self.make_envelope(), self.registry, now=NOW)
        self.assertEqual(first.payload_sha256, second.payload_sha256)


if __name__ == "__main__":
    unittest.main()
