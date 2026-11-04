# Sentinel — Verified email for connected services (implementation contract)

Status: DESIGN / NOT IMPLEMENTED. No Play Console claim may be based on this document alone.
Decision: 2026-11-04. Local phone/SMS/security core remains usable without registration. All network-backed account services require a verified email and valid authenticated session.

## Existing code evidence
- `native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt`: optional Auth0 login/logout.
- `native-android-app/app/src/main/java/com/sentinel/quantum/auth/Auth0SessionController.kt`: Auth0 WebAuthProvider, `openid profile email offline_access`, SecureCredentialsManager. Current `Auth0UserSummary` only includes subject/email; it does **not** validate `email_verified`.
- `native-android-app/app/src/main/AndroidManifest.xml`: `usesCleartextTraffic=false`; this alone is not proof that all collected data is encrypted in transit.
- `PRIVACY_POLICY.md`: local core, opt-in remote caller ID, external password breach lookup, possible future voice/media transport.

## Security boundaries (must implement)
1. A centralized `ConnectedServiceAccessPolicy` denies by default unless: valid Auth0 access token, server-side token validation (issuer, audience, expiry, signature), verified email claim from trusted issuer, and required product entitlement. A client UI flag is not authorization.
2. Do not infer email verification from a nonempty email string. Request `email_verified` in the trusted OIDC profile/ID token and check current server-side status where needed; handle provider-specific claims explicitly.
3. Deny connected-service API calls on missing/expired/revoked session, unverified email, absent entitlement, or backend outage. Explain state and offer login, verification/retry, or offline local fallback. Never block emergency calls or local Phone Core/SMS.
4. Registration/login: Auth0 Universal Login, email verification, resend verification, login, logout, recovery, account switching; rate limiting and abuse protection on backend/identity provider. Do not embed secrets in APK.
5. Gate every connected entry point **and** network invocation (including deep links, background jobs, voice room/token provisioning, community, cloud sync, premium APIs). No paywall for essential local protections.
6. Bind server-side data and entitlements to immutable Auth0 `sub`, not email address. Minimize stored personal data; do not upload contacts/SMS/call logs for CRM.
7. Consent to marketing is separate, unchecked by default where required; store purpose, version, timestamp, provenance, and withdrawal. Service/security notices are distinct from newsletters.
8. Account deletion: in-app entry and public web route; delete/retire account, tokens, CRM profile and backend records according to retention obligations, confirm completion. Define processor responsibilities and retention schedule.
9. Billing: do not unlock premium by UI alone; validate Google Play purchases server-side when enabled. Until implemented, mark premium modules unavailable and do not advertise them as functional.

## Planned implementation slices
A. Identity contract: extend trusted Auth0 profile with verification status; tests for unverified/missing/expired tokens, logout, offline state.
B. UI: clear account/verification status, sign-in, resend verification, sign-out, account deletion; keep guest local navigation functional.
C. Server API authorization middleware and per-service entitlement checks; integration tests for bypasses and revocation.
D. Minimal customer profile and opt-in newsletter preferences; separate transactional notices; audit logs without PII leakage.
E. Privacy policy and terms: identify controller/contact, Auth0 processor, categories/purposes/legal bases/retention/transfers, deletion route, consent withdrawal.
F. Build/test on target release AAB and physical Galaxy S24+; inspect merged manifest, dependencies and actual network captures.

## Release gates (ALL required)
- Android unit and instrumentation tests, backend authorization integration tests, no bypass via direct endpoint/background worker/deep link.
- Guest: local protection, dialing/emergency and SMS role functions remain available without account or network.
- Connected: denied for guest/unverified/revoked; permitted for verified active user only with correct entitlement.
- Auth0 tenant configuration confirmed: registration method, verified email delivery, allowed callbacks, logout URLs, attack protection.
- Security review: TLS for every collection path, SDK inventory, data minimization, encryption at rest, deletion, consent withdrawal.
- Release artifact provenance and signed AAB tested; no claims of PASS until executed.

## Google Play Console — changes after verified release
- App access: re-evaluate; if reviewers need login to test connected features, provide a functioning reusable review account or clear review access instructions. Do not claim the entire app is unrestricted.
- Data Safety: disclose actual Auth0 identifiers/email and any other data transmitted, plus SDKs; specify collection/sharing, purposes, optionality, encryption in transit, account creation and deletion. Do not guess based on permissions alone.
- Account creation methods: choose actual Auth0-enabled signup methods after inspecting tenant configuration, not from login code alone.
- Account deletion: in-app and web path and associated policy; verify links live.
- Advertising ID: inspect merged release manifest and dependencies before choosing.
- Privacy policy: publish app-specific current version at the URL declared in Play Console and align with actual APK/network behavior.
- Store listing: clearly describe guest local core and verified-email requirement for connected features.

## Outstanding blockers
- Current GitHub source inspected, not proof of actual AAB or Auth0 tenant configuration.
- No end-to-end account creation/verification, CRM, deletion or newsletter functionality has been implemented by this planning commit.
- No build, test or Google Play declaration has been executed as part of this change.
