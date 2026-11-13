import json
import tempfile
import threading
import unittest
from datetime import datetime, timezone
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

from sentinel_cti.crypto import P256_ORDER, encode_base64url
from sentinel_cti.envelope import STIC_DOMAIN, TrustKey, TrustRegistry, _signed_bytes
from sentinel_cti.jcs import canonicalize_json
from sentinel_cti.rollback import (
    SequenceConflictError,
    SequenceReplayError,
    SequenceStore,
    SequenceStoreError,
    accept_envelope,
)
NOW = datetime(2026, 11, 13, 12, 0, tzinfo=timezone.utc)


class EnvelopeBuilder:
    @classmethod
    def setUpClass(cls):
        cls.private = ec.generate_private_key(ec.SECP256R1())
        cls.public = cls.private.public_key().public_bytes(
            encoding=serialization.Encoding.X962,
            format=serialization.PublicFormat.UncompressedPoint,
        )
        cls.registry = TrustRegistry({
            "prod-signing-1": TrustKey(
                key_id="prod-signing-1", environment="prod",
                flows=frozenset({"threat-intelligence"}),
                capabilities=frozenset({"cisa-kev", "malware-metadata"}),
                public_key=cls.public,
            ),
        })

    def make_envelope(self, **changes):
        payload = {"indicator": "CVE-2026-1234", "severity": "high"}
        header = {
            "version": "1.1", "domain": STIC_DOMAIN, "environment": "prod",
            "flow": "threat-intelligence", "capabilities": ["cisa-kev"],
            "issued_at": "2026-11-13T10:00:00Z", "valid_from": "2026-11-13T10:00:00Z",
            "expires_at": "2026-11-14T10:00:00Z", "payload_sha256": "", "payload_size": 0,
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
        header["payload_sha256"] = __import__("hashlib").sha256(canonical_payload).hexdigest()
        header["payload_size"] = len(canonical_payload)
        der = self.private.sign(_signed_bytes(header, payload), ec.ECDSA(hashes.SHA256()))
        r, s = decode_dss_signature(der)
        if s > P256_ORDER // 2:
            s = P256_ORDER - s
        generated = encode_base64url(r.to_bytes(32, "big") + s.to_bytes(32, "big"))
        envelope["signature"] = changes.get("signature", generated)
        return envelope


class RollbackStoreTest(EnvelopeBuilder, unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        EnvelopeBuilder.setUpClass.__func__(cls)

    def envelope(self, sequence, catalog_type="FULL", base_sequence=None, **changes):
        payload = {
            "indicator": "CVE-2026-1234",
            "severity": "high",
            "sequence": sequence,
            "catalog_type": catalog_type,
        }
        if base_sequence is not None:
            payload["base_sequence"] = base_sequence
        payload.update(changes)
        return self.make_envelope(payload=payload)

    def test_first_acceptance_persists_across_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sequences.json"
            first = accept_envelope(self.envelope(1), self.registry, SequenceStore(path), now=NOW)
            restarted = SequenceStore(path)
            self.assertEqual(first.sequence, 1)
            self.assertEqual(restarted.snapshot()[next(iter(restarted.snapshot()))]["sequence"], 1)
            with self.assertRaises(SequenceReplayError):
                accept_envelope(self.envelope(1), self.registry, restarted, now=NOW)

    def test_delta_requires_current_base_and_full_rejects_base(self):
        with tempfile.TemporaryDirectory() as directory:
            store = SequenceStore(Path(directory) / "sequences.json")
            accept_envelope(self.envelope(1), self.registry, store, now=NOW)
            with self.assertRaises(SequenceConflictError):
                accept_envelope(self.envelope(2, "DELTA", base_sequence=0), self.registry, store, now=NOW)
            accept_envelope(self.envelope(2, "DELTA", base_sequence=1), self.registry, store, now=NOW)
            with self.assertRaises(SequenceStoreError):
                accept_envelope(self.envelope(3, "FULL", base_sequence=2), self.registry, store, now=NOW)

    def test_concurrent_duplicate_acceptance_has_one_winner(self):
        with tempfile.TemporaryDirectory() as directory:
            store = SequenceStore(Path(directory) / "sequences.json")
            candidate = self.envelope(1)
            outcomes = []
            barrier = threading.Barrier(2)

            def worker():
                barrier.wait()
                try:
                    outcomes.append(accept_envelope(candidate, self.registry, store, now=NOW).sequence)
                except Exception as exc:  # assertions below classify the expected loser
                    outcomes.append(type(exc))

            threads = [threading.Thread(target=worker) for _ in range(2)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            self.assertEqual(outcomes.count(1), 1)
            self.assertEqual(outcomes.count(SequenceReplayError), 1)

    def test_write_failure_does_not_create_state_and_marker_failure_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sequences.json"
            store = SequenceStore(path)
            store._atomic_write = lambda state: (_ for _ in ()).throw(SequenceStoreError("simulated write failure"))
            with self.assertRaises(SequenceStoreError):
                accept_envelope(self.envelope(1), self.registry, store, now=NOW)
            self.assertFalse(path.exists())
            store = SequenceStore(path)
            original_marker = store._atomic_marker
            store._atomic_marker = lambda: (_ for _ in ()).throw(SequenceStoreError("simulated marker crash"))
            with self.assertRaises(SequenceStoreError):
                accept_envelope(self.envelope(1), self.registry, store, now=NOW)
            self.assertTrue(path.exists())
            with self.assertRaises(SequenceStoreError):
                SequenceStore(path).snapshot()

    def test_corruption_and_missing_state_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sequences.json"
            store = SequenceStore(path)
            accept_envelope(self.envelope(1), self.registry, store, now=NOW)
            marker = Path(f"{path}.initialized")
            path.write_text("not json", encoding="utf-8")
            with self.assertRaises(SequenceStoreError):
                SequenceStore(path).snapshot()
            path.write_text(json.dumps({"schema": 1, "records": {}}), encoding="utf-8")
            path.unlink()
            with self.assertRaises(SequenceStoreError):
                SequenceStore(path).snapshot()
            self.assertTrue(marker.exists())

    def test_unknown_schema_is_rejected_without_reset(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sequences.json"
            store = SequenceStore(path)
            accept_envelope(self.envelope(1), self.registry, store, now=NOW)
            path.write_text(json.dumps({"schema": 99, "records": {}}), encoding="utf-8")
            with self.assertRaises(SequenceStoreError):
                SequenceStore(path).snapshot()

    def test_snapshot_is_immutable(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sequences.json"
            store = SequenceStore(path)
            accept_envelope(self.envelope(1), self.registry, store, now=NOW)
            snapshot = store.snapshot()
            record = next(iter(snapshot.values()))
            with self.assertRaises(TypeError):
                record["sequence"] = 100

    def test_key_rotation_keeps_namespace_and_revocation_rejects_old_key(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sequences.json"
            rotated_private = ec.generate_private_key(ec.SECP256R1())
            rotated_public = rotated_private.public_key().public_bytes(
                encoding=serialization.Encoding.X962,
                format=serialization.PublicFormat.UncompressedPoint,
            )
            rotated_registry = TrustRegistry({
                "prod-signing-1": TrustKey(
                    key_id="prod-signing-1", environment="prod",
                    flows=frozenset({"threat-intelligence"}),
                    capabilities=frozenset({"cisa-kev", "malware-metadata"}),
                    public_key=self.public, revoked=True, sequence_namespace="prod-ti"
                ),
                "prod-signing-2": TrustKey(
                    key_id="prod-signing-2", environment="prod",
                    flows=frozenset({"threat-intelligence"}),
                    capabilities=frozenset({"cisa-kev", "malware-metadata"}),
                    public_key=rotated_public, sequence_namespace="prod-ti"
                ),
            })
            store = SequenceStore(path)
            with self.assertRaises(Exception):
                accept_envelope(self.envelope(1), rotated_registry, store, now=NOW)
            def rotated_envelope(sequence):
                envelope = self.envelope(sequence)
                envelope["header"]["key_id"] = "prod-signing-2"
                der = rotated_private.sign(_signed_bytes(envelope["header"], envelope["payload"]), ec.ECDSA(hashes.SHA256()))
                r, s = decode_dss_signature(der)
                if s > P256_ORDER // 2:
                    s = P256_ORDER - s
                envelope["signature"] = encode_base64url(r.to_bytes(32, "big") + s.to_bytes(32, "big"))
                return envelope
            envelope = rotated_envelope(1)
            accept_envelope(envelope, rotated_registry, store, now=NOW)
            envelope = rotated_envelope(2)
            accept_envelope(envelope, rotated_registry, store, now=NOW)
            self.assertEqual(len(SequenceStore(path).snapshot()), 1)


if __name__ == "__main__":
    unittest.main()
