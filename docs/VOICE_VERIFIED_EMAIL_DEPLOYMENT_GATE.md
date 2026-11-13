# Auth0 verified-email requirement for voice API

**Security status: fail closed; not production-qualified.**

The voice API rejects creation of a new session unless `email_verified is True`. Closing an existing session remains available to the authenticated owner after verification status changes, subject to stored session ownership and device-binding checks. The OIDC verifier reads this claim from the cryptographically validated JWT (signature, issuer, audience, expiry).

**Critical deployment dependency:** Auth0 access tokens do not automatically contain the standard ID-token `email_verified` claim. The verifier now requires the namespaced claim `https://sentinelquantumvanguardaipro.pages.dev/claims/email_verified` by default, configurable through `VOICE_EMAIL_VERIFIED_CLAIM`. An example Auth0 Post Login Action is provided at `backend/voice-api/auth0-post-login-action.example.js`. Configure `SENTINEL_VOICE_API_AUDIENCE` as an Action secret and deploy/attach the Action to the Login flow. Confirm the custom claim appears only in access tokens for the correct audience. The namespace is an identifier, not an endpoint to call. Do not copy a decoded, unverified ID token into an access token or trust a client-provided boolean. Until claim provisioning is tested, legitimate sessions may receive 403.

Security tests required before merge:
- Valid JWT, verified true, valid device and entitlement -> permitted.
- Missing, false, string `"true"`, or null verification -> denied.
- Forged/expired/wrong audience/issuer JWT -> denied.
- Logout, account verification-state changes and entitlement revocation -> denied according to server policy.
- Existing active session close behavior after verification changes: authenticated owner can close; unrelated user/device cannot. Add endpoint-level regression tests before release.
- Run `python -m unittest discover -s backend/voice-api -p 'test_email_claim_policy.py'` in the configured backend environment; this is not evidence of a successful run until the command finishes successfully.
- Phone Core and local SMS/MMS must continue to work offline without account.
- Run existing backend Python tests and Android tests; no PASS claimed.

The code change is intentionally isolated to the voice API on a draft qualification branch; production rollout must wait for claim provisioning and regression tests.
