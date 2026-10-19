# Sentinel Mail Shield — pipeline v1

Status: foundation only. This document does not claim mailbox integration is active.

Pipeline:
1. bounded raw-message ingest;
2. local header / Received-chain analysis;
3. observed SPF, DKIM and DMARC result analysis;
4. local IOC extraction (URLs, public IP literals, email addresses, phone numbers);
5. attachment-name/type indicators and lookalike-domain signals;
6. deterministic disposition: NORMAL, REVIEW, SUSPICIOUS, or QUARANTINE_CANDIDATE.

Privacy and safety boundaries:
- no Gmail, Microsoft 365, IMAP or SMTP credentials in this module;
- no mailbox mutation, deletion or automatic quarantine;
- no DNS, IP geolocation, ASN or reputation network lookup yet;
- no URL opening and no attachment execution;
- QUARANTINE_CANDIDATE is a recommendation, not an automatic destructive action;
- remote enrichment must later be gated by ProtectionMode.ENHANCED plus feature-specific consent.

Planned follow-up stages:
- OAuth provider adapters with least-privilege scopes;
- normalized message envelope and provider-neutral mailbox connector;
- provenance graph for Received hops and observable public IPs;
- separately consented IP/ASN/country/reputation enrichment with confidence/provenance;
- reversible quarantine and audit journal;
- provider-specific webhook/polling ingestion with replay protection and bounded rate limits.

## Connector architecture — October 2026

Implemented in code: `MailConnectorContract.kt`, a provider-neutral, non-operational
interface with explicit connection states, per-capability authorization and
provenance-bearing message references. This is **not** a Gmail or Outlook
connection and does not grant access to any mailbox.

Release gates before enabling a provider:
1. Gmail: configure a verified Google OAuth application, exact redirect URIs,
   narrow read-only Gmail scope where feasible, consent UX, and provider
   verification as required by Google. OAuth refresh tokens must be encrypted
   server-side and bound to a Sentinel user and provider account.
2. Microsoft 365/Outlook: Microsoft identity platform authorization code +
   PKCE, least-privilege delegated Microsoft Graph Mail.Read, tenant handling,
   expiry/revocation checks and account isolation.
3. IMAP: optional and disabled by default; require TLS verification and
   provider-supported OAuth; avoid retaining mailbox passwords.
4. Enforce a server-side scope/capability map (client UI gating is insufficient).
   Message send, archive, label and delete must be separately consented;
   default permission is read-only. No bulk destructive action without an
   explicit preview and confirmation.
5. Bounded background sync, cursor replay protection, rate limits, connection
   health/expiry, audit log with redacted identifiers and secure account
   disconnect/revocation.
6. Feed normalized message content to the existing bounded local Mail Shield
   analyzer only with account-specific consent. Do not download full
   attachments or open links during passive inspection.
7. Test OAuth callback integrity, CSRF state/PKCE, tenant separation, revoked
   grants, pagination, provider throttling and non-consensual actions.
8. Production UI must display NOT_CONFIGURED / AUTHORIZATION_REQUIRED /
   CONNECTED / EXPIRED / REVOKED / ERROR from actual backend evidence.
   Never show CONNECTED based on a mock or merely present SDK.

Recommended next adapters after email: Google Calendar / Microsoft Calendar
(read-only events), Contacts with privacy controls, Google Drive / OneDrive
(file metadata only by default), and opt-in threat intelligence feeds.
Each requires its own consent, scope boundary and product-specific tests.

## OAuth protocol gate implemented (not live)

`MailOAuthHandshake.kt` generates cryptographically random authorization state
and RFC 7636 S256 PKCE material, checks same-provider and exact callback
matching, enforces five-minute expiry, and consumes each pending flow only
once. It intentionally does not open Google login, exchange tokens, or grant
access. The JVM unit test `MailOAuthHandshakeTest.kt` covers random state,
one-time use, mismatch, expiry, redirect URI and rejection of generic IMAP.

**Deployment dependency:** provision a dedicated mailbox OAuth backend with
exactly registered provider client identifiers and HTTPS callbacks. The backend
must store hashed short-lived state transaction identifiers, enforce PKCE,
session ownership, redirect and nonce verification, verify Google's granted
scopes, encrypt refresh tokens at rest, rotate secrets and record account-bound
consent. It must never accept a client-supplied redirect destination or
trust `MailOAuthHandshake` as a server-side authorization decision.

The Android UI currently renders provider status as NOT_CONFIGURED. Enable
connect actions only after live backend connectivity and end-to-end tests.
Do not import credentials or pre-existing Gmail permissions from ChatGPT.
