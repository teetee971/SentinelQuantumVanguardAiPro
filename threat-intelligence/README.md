# STIC v1.1 envelope

This package verifies signed Sentinel Threat Intelligence Control (STIC) v1.1
envelopes. It is intentionally verification-only: it does not fetch samples,
mutate trust state, persist replay state, or claim durable anti-rollback.

The signed input is the ASCII domain separator, a newline, the JCS canonical
header with `key_id` removed, another newline, and the JCS canonical payload.
`key_id` is authorized separately through `TrustRegistry`; it is not silently
added to the signed bytes.

The verifier requires an uncompressed SEC1 P-256 public point, SHA-256, and a
64-byte unpadded base64url IEEE-P1363 signature. High-S signatures, duplicate
JSON keys, unsafe integers, invalid Unicode, non-UTC timestamps, unknown or
revoked keys, cross-environment keys, unauthorized flows/capabilities, payload
size mismatches, and digest mismatches are rejected.

The candidate Termux files mentioned in the mission were not present in this
checkout and were not reconstructed from their hashes.
