# SIM-swap evidence contract

Sentinel does not infer a SIM replacement or number port from browser or ordinary
application APIs. An accepted assessment requires a signed observation from a
deployment-authorized carrier or identity-provider adapter.

## Signed record

The Ed25519 signature uses the existing `sentinel-proof-v1` canonical proof
format with proof type `sim-swap-signal`. The signed record contains:

- `evidence_id`: unique bounded identifier;
- `subject_id`: pseudonymous account/line binding;
- `observed_at`: carrier or provider observation time;
- `signal_values`: the complete fixed boolean signal set;
- `issued_at` and `expires_at`: bounded validity window;
- `issuer_id`, `key_id`, `signature_alg`, and `signature`.

The evaluator verifies the signature, allowlisted issuer, public-key lookup,
revocation, proof window, subject, observation, complete signal binding, and an
atomic replay key before returning any accepted risk level. Even a low-risk
result requires authenticated evidence, preventing attackers from obtaining a
false low result by deleting positive fields.

## Fail-closed requirements

- Missing, malformed, expired, future, forged, revoked, substituted, or replayed
  evidence is rejected.
- Missing trust configuration or replay protection is rejected.
- Recovery and response-plan functions accept only immutable assessment objects
  branded internally by the verifier; copying or fabricating
  `evidenceVerified: true` cannot bypass the gate.
- An arbitrary caller callback is not an evidence verifier and cannot bypass
  cryptographic validation.

## External production gates

Repository tests do not prove production carrier coverage. Deployment still
requires contracted/authorized evidence producers, audited issuer-to-capability
mapping, HSM/KMS custody for private keys, key rotation and revocation, trusted
configuration loading, durable atomic replay storage, monitoring, and end-to-end
tests against the target carrier or identity provider.
