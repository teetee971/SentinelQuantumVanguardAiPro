# Sentinel OWNER recovery

## Objective

OWNER recovery must prevent permanent lockout if the primary phone is lost, stolen, destroyed or unavailable, without creating a universal master password or weakening Sentinel's operational authorization boundary.

## Recovery channels

A production OWNER account should be provisioned with independent recovery channels before an incident occurs:

- primary registered device with a hardware-backed key where available;
- at least one FIDO2/passkey hardware security key stored separately;
- an offline break-glass recovery credential stored securely and independently;
- optionally a second registered OWNER device.

IMEI, phone number, email address, username and other public or discoverable identifiers are not sufficient recovery proofs by themselves.

## Lost-phone sequence

1. Authenticate through an independent approved recovery factor.
2. Identify the lost registered device.
3. Revoke the lost device and record the revocation in the audit trail.
4. Invalidate sessions and credentials bound to the lost device where the production identity system supports it.
5. Perform strong authentication again for replacement-device enrollment.
6. Bind a new device-generated public key to the OWNER identity server-side.
7. Confirm the new device before restoring normal OWNER device authentication.

Recovery does not grant external-target authorization and does not bypass Sentinel action-gate controls.

## Break-glass requirements

Break-glass material must not be embedded in source code, mobile resources, environment defaults, browser storage or public configuration. Production provisioning should use a secret manager or equivalent protected system. The break-glass path must be auditable, revocable and tested periodically without exposing the secret value.

## Device identity

The preferred device identity is cryptographic possession of a device-generated private key protected by Android Keystore/TEE/StrongBox where available. Sentinel should store only the corresponding public key and server-side device record. Device metadata such as model, IMEI or phone number may be recorded for administrative context only when legally and technically appropriate; it must not replace cryptographic proof.

## Availability invariant

The design must avoid a single point of owner lockout. At least two independent recovery paths should exist before the OWNER account is considered operationally recoverable.
