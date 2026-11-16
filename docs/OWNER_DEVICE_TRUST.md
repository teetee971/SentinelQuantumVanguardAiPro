# Sentinel OWNER Device Trust

## Scope

This document defines the first concrete device-side building block for OWNER authentication on the native Android application.

The Android application now contains `OwnerDeviceKeyManager`, which creates and uses a dedicated OWNER device identity key inside `AndroidKeyStore`.

This is separate from the Android release-signing keystore. Release signing proves who signed an APK. OWNER device trust proves possession of an enrolled device key. The two key domains must never be reused or merged.

## Device key properties

The current implementation:

- uses an EC P-256 key pair;
- stores the private key in `AndroidKeyStore` so application code cannot export it;
- requests StrongBox-backed key generation on Android 9+ when the device supports it;
- falls back to the platform `AndroidKeyStore` when StrongBox is unavailable;
- can bind a fresh server-provided attestation challenge during key creation;
- exports only the public key and certificate chain;
- signs bounded server challenges with ECDSA/SHA-256;
- can delete the local key when the device is revoked.

The client exposes Android's local `isInsideSecureHardware` result only as a local indicator. It does not claim that StrongBox or any particular hardware security level has been remotely verified. A production server must establish the accepted hardware security level from verified key-attestation evidence and deployment policy.

## Required server-side protocol

A production OWNER enrollment flow must include a trusted backend or IdP. The server must:

1. authenticate the OWNER using the normal OWNER identity flow;
2. issue a high-entropy, short-lived, single-use enrollment challenge;
3. receive the Android public key and certificate chain;
4. validate the key-attestation certificate chain, challenge binding and security level according to the deployment trust policy;
5. bind the accepted public key to the immutable OWNER subject and a server-generated device identifier;
6. store device state (`ACTIVE`, `REVOKED`, etc.) in a trusted registry;
7. write an auditable enrollment event.

For each OWNER challenge-response authentication, the server must:

1. issue a new high-entropy single-use challenge;
2. bind the challenge to the expected OWNER, device, session and requested operation context;
3. enforce expiry and anti-replay;
4. verify the ECDSA signature against the enrolled public key;
5. reject revoked or unknown devices;
6. continue to enforce MFA, OWNER authorization and all non-bypassable operational controls.

## Lost-device handling

If the primary phone is lost or stolen:

1. recover OWNER access through an approved independent recovery factor;
2. mark the old device `REVOKED` server-side;
3. reject every subsequent challenge signed by the old device key;
4. enroll the replacement phone with a fresh key pair;
5. audit both revocation and replacement enrollment.

Deleting the key locally is useful when the device is still physically available, but server-side revocation is authoritative because a lost device cannot be relied upon to execute local deletion.

## What is intentionally not implemented yet

The repository does not currently provide a production OWNER identity backend, device registry, attestation-verification service, challenge service, revocation store or Play Integrity verifier. Therefore this Android component must not be described as production authentication by itself.

The current code establishes a device-side cryptographic primitive that can be integrated into such a backend later.

## Prohibited shortcuts

Production OWNER trust must not be based only on:

- IMEI;
- phone number;
- SIM identity;
- Android ID;
- username;
- email address;
- client-side flags or local storage;
- the APK release-signing key.

These values may be telemetry or correlation inputs where lawful and necessary, but they are not substitutes for cryptographic proof of possession and server-side authorization.
