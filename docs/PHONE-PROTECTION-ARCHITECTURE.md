# Sentinel Phone Protection — bounded architecture

## Product position

Sentinel combines local call rules, privacy-preserving exact-number storage,
platform call-screening APIs, explainable decisions, and SIM-swap recovery
controls. It does not reuse third-party caller-identification data or code and does not import a user
address book.

## Android implementation

- `CallScreeningService` is the system entrypoint selected explicitly by the user.
- A response is produced synchronously from local rules; there is no network call
  on the incoming-call path.
- Exact blocked numbers are stored only as device-bound HMAC-SHA-256
  fingerprints. The non-exportable secret is generated in Android Keystore,
  preventing practical offline enumeration of the small phone-number space.
- Fingerprints carry a key version. New rules use `v2`; upgraded installations
  read existing unversioned/`v1` rules only when the legacy Keystore key already
  exists. A fresh installation does not create `v1`. Because raw numbers are not
  retained, final `v1` retirement requires clearing and re-enrolling legacy exact
  rules; silently re-hashing them is intentionally impossible.
- Users can remove individual prefix rules or clear every exact-number rule.
- French national, `+33`, and `0033` forms are canonicalized consistently,
  while legacy exact-number fingerprints remain matchable.
- User prefixes may block. Remote reputation prefixes are accepted only from
  strict P-256 signed packages with expiry and monotonically increasing sequence;
  they may silence but never block.
- Blocked calls remain visible in the system call log and notifications are not
  hidden.
- After the mandatory screening response, an optional read-only caller-ID activity
  displays locally derived facts: normalized number, country/flag, indicative call
  type, network verification status, Sentinel action and reason. Missing names or
  organisations remain explicitly unavailable; they are never inferred from a prefix.
- Default-dialer mode declares `CALL_PHONE` and `READ_CALL_LOG`. The system
  call-log reader is fail-closed: it reads only while Sentinel holds
  `ROLE_DIALER` and the user has granted `READ_CALL_LOG`; call placement also
  requires the appropriate role and permission.
- `READ_PHONE_STATE` supports explicit multi-SIM selection. The staged default
  SMS/MMS client declares `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS` and MMS
  permissions, but access is gated by the actual `ROLE_SMS` state and the
  required runtime permissions. None of these declarations is an automatic grant.
- `READ_CONTACTS` is optional, requested separately and used for an on-device
  lookup without contact upload. Phone Core itself does not use the microphone for carrier-call handling.
- Local log messages pass through bounded best-effort credential and signed-envelope
  redaction before persistence. Callers must still avoid supplying secrets because
  pattern-based redaction cannot prove coverage of every future credential format.

Android requires the user to grant Sentinel the call-screening role. The platform
requires a screening response within five seconds. Calls in contacts are not
normally provided unless contact permission is granted; Sentinel exposes a
separate, reversible opt-in for that permission.

A separate, opt-in Caller Reputation client is implemented after the mandatory
local screening decision. It may transmit the normalized incoming caller number,
destination country and network-verification status only when the user has
explicitly enabled this remote enrichment; it never uploads local contacts.
ARCEP allocation facts are available as directory context but identify the holder
of a number range, not the current carrier after portability or the caller's
natural-person identity. Production claims still require physical-device and
infrastructure validation; future labels or data sources must preserve provenance,
confidence and freshness.

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

1. Review and license an authoritative French numbering/rule source before
   provisioning the signed update channel with real data and production keys.
2. Add an authorized carrier/IdP adapter for porting and SIM-change evidence.
3. Complete privacy, Play Store, legal, false-positive, latency, and
   physical-device validation before any production claim.


## Voice Studio and live-call transformation boundary

Live voice transformation is a **required product capability** for the Sentinel calling add-on, not an optional future idea. The Android app now contains a bounded streaming PCM16 transformer, a `SentinelVoipVoicePipeline` outgoing-microphone stage, and a `SentinelVoipCallSession` that routes each active-call microphone frame through the transformer before handing it to the owned media transport. A concrete WebRTC/SIP transport still has to implement that transport boundary before production calling can be enabled.

The separate Voice Studio still uses `RECORD_AUDIO` for an explicit local microphone preview. Its sample is written to app-private cache, is not uploaded, and is deleted when the Voice Studio closes.

The Android default-dialer / `InCallService` role still does **not** give an ordinary third-party app a public carrier/SIM media-injection path. Sentinel therefore does not claim to transform a native SIM call. To provide transformed calls to normal telephone numbers, Sentinel must originate the call through its own VoIP media session and a controlled VoIP/PSTN gateway.

Paid checkout remains fail-closed until all of these are true:

1. the Sentinel-owned VoIP media transport is connected to `SentinelVoipVoicePipeline`;
2. PSTN or peer-call transport is validated end-to-end;
3. real-device latency, intelligibility, echo and Bluetooth routing are validated;
4. privacy/legal review for live transformation has passed.

The DSP engine being integrated is necessary but is not, by itself, evidence that a production telephone call has traversed the transformed media path.
