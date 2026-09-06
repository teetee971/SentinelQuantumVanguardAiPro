# Sentinel Phone Protection — bounded architecture

## Product position

Sentinel combines local call rules, privacy-preserving exact-number storage,
platform call-screening APIs, explainable decisions, and SIM-swap recovery
controls. It does not reuse Truecaller data or code and does not import a user
address book.

## Android implementation

- `CallScreeningService` is the system entrypoint selected explicitly by the user.
- A response is produced synchronously from local rules; there is no network call
  on the incoming-call path.
- Exact blocked numbers are stored only as device-bound HMAC-SHA-256
  fingerprints. The non-exportable secret is generated in Android Keystore,
  preventing practical offline enumeration of the small phone-number space.
- Users can remove individual prefix rules or clear every exact-number rule.
- User prefixes may block. Bundled vigilance prefixes only silence, which reduces
  false-positive harm.
- Blocked calls remain visible in the system call log and notifications are not
  hidden.
- No `READ_CALL_LOG`, `READ_PHONE_STATE`, `READ_SMS`, contacts, microphone, or
  location permission is requested.

Android requires the user to grant Sentinel the call-screening role. The platform
requires a screening response within five seconds. Calls in contacts are not
normally provided unless contact permission is granted; Sentinel deliberately
does not request it.

## iOS boundary

iOS requires a separate signed Xcode target containing a Call Directory extension.
The extension can load sorted identification and blocking entries, but it does not
offer Android's per-call arbitrary execution model. No iOS target exists in this
repository yet, so iOS blocking remains `NOT_IMPLEMENTED`.

## SIM-swap boundary

SIM-swap decisions do not trust client booleans or caller-provided verification
callbacks. Every accepted assessment, including a low-risk result, requires fresh
Ed25519 evidence bound to the subject, observation time, and complete signal set,
plus allowlisted issuer/key resolution, revocation checks, and atomic anti-replay.
The policy engine can then require an independent authenticator and prohibit
SMS/voice recovery for high-risk events. It does not claim to observe a carrier
event itself.

Reliable carrier-side number-port or SIM-swap confirmation requires a contracted
carrier or authorized identity-provider signal. Android subscription identifiers
are platform-limited and permission-sensitive; iOS does not expose a stable public
SIM identifier suitable for a universal detector.

## Next gates

1. Review and license an authoritative French numbering/rule source before adding
   a signed update channel.
2. Add rollback-protected signed rule packages and expiry metadata.
3. Build a separate iOS Call Directory target and validate it on physical devices.
4. Add an authorized carrier/IdP adapter for porting and SIM-change evidence.
5. Complete privacy, Play Store, App Store, legal, false-positive, latency, and
   physical-device validation before any production claim.
