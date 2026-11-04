# Auth0 verified-email requirement for voice API

**Security status: fail closed; not production-qualified.**

The voice API now rejects a principal without `email_verified is True` on both session creation and close. The OIDC verifier reads this claim from the cryptographically validated JWT (signature, issuer, audience, expiry).

**Critical deployment dependency:** Auth0 access tokens do not automatically contain the standard ID-token `email_verified` claim. Do not copy a decoded, unverified ID token into an access token or trust a client-provided boolean. Configure a trusted Auth0 Action to add a namespaced verified-email claim to the API access token, then change the verifier to read that exact namespaced claim; alternatively resolve verified status server-side from Auth0 Management API with suitable permissions and caching/revocation rules. Until this is done, legitimate sessions may receive 403.

Security tests required before merge:
- Valid JWT, verified true, valid device and entitlement -> permitted.
- Missing, false, string `"true"`, or null verification -> denied.
- Forged/expired/wrong audience/issuer JWT -> denied.
- Logout, account verification-state changes and entitlement revocation -> denied according to server policy.
- Existing active session close behavior after revocation must be explicitly defined and tested.
- Phone Core and local SMS/MMS must continue to work offline without account.
- Run existing backend Python tests and Android tests; no PASS claimed.

The code change is intentionally isolated to the voice API on a draft qualification branch; production rollout must wait for claim provisioning and regression tests.
