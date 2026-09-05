# Production proof trust configuration

Sentinel's final execution boundary verifies Ed25519-signed authorization, human-approval and simulation proofs. The repository provides verification logic, but production authenticity still depends on deployment-side issuer identity, public-key provisioning, private-key custody and rotation.

`decision-plane/policy/proof-trust-config.js` converts an explicit deployment configuration into the runtime trust object consumed by the verifier. It intentionally rejects private-key material and requires all three proof types used by the final boundary.

Example shape:

```json
{
  "issuers": {
    "authorization": [
      {
        "issuer_id": "auth-service-prod",
        "keys": [
          { "key_id": "auth-2026-09", "public_key_pem": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n" }
        ]
      }
    ],
    "approval": [
      {
        "issuer_id": "human-approval-service-prod",
        "keys": [
          { "key_id": "approval-2026-09", "public_key_pem": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n" }
        ]
      }
    ],
    "simulation": [
      {
        "issuer_id": "simulator-service-prod",
        "keys": [
          { "key_id": "simulation-2026-09", "public_key_pem": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n" }
        ]
      }
    ]
  },
  "revoked_key_ids": []
}
```

Operational requirements before claiming production end-to-end authenticity:

- inventory the real producer for each proof type and assign stable issuer IDs;
- provision only public Ed25519 keys into Sentinel trust configuration;
- keep signing private keys in an external managed secret/HSM/KMS boundary appropriate to the producer; never commit them to this repository or inject them into the verifier configuration;
- make key IDs globally unique, because revocation in the current verifier is keyed by `key_id`;
- define a rotation procedure with overlap between old and new public keys, followed by explicit revocation of retired key IDs;
- maintain an auditable revocation source and deployment change record;
- verify in the deployed runtime that each configured issuer maps to the intended producer and that unauthorized/forged/revoked proofs are rejected;
- retain issue #215 until those deployment facts are observed and documented.

Repository tests demonstrate configuration parsing and cryptographic verification behavior only. They do not prove private-key custody, producer identity or production deployment state.
