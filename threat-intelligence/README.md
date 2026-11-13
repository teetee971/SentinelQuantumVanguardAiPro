# STIC v1.1 envelope

This package verifies signed Sentinel Threat Intelligence Control (STIC) v1.1
envelopes and can accept their signed monotonic sequence through the durable
`SequenceStore`. It does not fetch samples or claim hardware/server
anti-rollback.

The signed input is the ASCII domain separator, a newline, the JCS canonical
header with `key_id` removed, another newline, and the JCS canonical payload.
`key_id` is authorized separately through `TrustRegistry`; it is not silently
added to the signed bytes.

The verifier requires an uncompressed SEC1 P-256 public point, SHA-256, and a
64-byte unpadded base64url IEEE-P1363 signature. High-S signatures, duplicate
JSON keys, unsafe integers, invalid Unicode, non-UTC timestamps, unknown or
revoked keys, cross-environment keys, unauthorized flows/capabilities, payload
size mismatches, and digest mismatches are rejected.

`accept_envelope` verifies the envelope completely before taking an exclusive
file lock and committing a strictly increasing FULL/DELTA sequence. The state
file and initialization marker are written with temporary files, `fsync`, and
atomic replacement. Missing/corrupt/mismatched state, unknown schemas, broad
permissions, and write failures fail closed. A first acceptance bootstraps an
empty store; once initialized, loss of either file is an error. The
`sequence_namespace` field keeps a rotated key in the same monotonic lineage.

The candidate Termux files mentioned in the mission were not present in this
checkout and were not reconstructed from their hashes. A local store cannot
protect against an attacker who restores both its state and marker; use a
server/HSM/secure monotonic hardware anchor for that threat model.
