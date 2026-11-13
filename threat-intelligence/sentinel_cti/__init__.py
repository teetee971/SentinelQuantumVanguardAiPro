"""Sentinel CTI STIC v1.1 verification primitives."""

from .envelope import (
    STIC_DOMAIN,
    EnvelopeVerificationError,
    TrustKey,
    TrustRegistry,
    VerifiedEnvelope,
    verify_envelope,
)
from .rollback import (
    SequenceConflictError,
    SequenceReceipt,
    SequenceReplayError,
    SequenceStore,
    SequenceStoreError,
    accept_envelope,
)

__all__ = [
    "STIC_DOMAIN",
    "EnvelopeVerificationError",
    "TrustKey",
    "TrustRegistry",
    "VerifiedEnvelope",
    "verify_envelope",
    "SequenceConflictError",
    "SequenceReceipt",
    "SequenceReplayError",
    "SequenceStore",
    "SequenceStoreError",
    "accept_envelope",
]
