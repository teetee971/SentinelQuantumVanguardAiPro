# Sentinel Quantum Vanguard AI Pro — Phone Core Completion & Security Intelligence Design

Date: 2026-10-12
Status: design approved in chat; written specification pending owner review
Base: `main` at `aaa96b110f758560677afc67ec776a1a57f4fcef`

## 1. Intent

Finish the Android Phone Core as a complete user journey rather than a collection of partially exposed Telecom/SMS capabilities, while adding a security-first layer inspired by leading dialer/caller-ID products without copying their product identity.

The target experience must let a user move end-to-end through dialing, contacts, recent calls, incoming calls, in-call controls, multi-call/conference, SMS/MMS, suspicious-link handling, post-call actions, and voicemail access without dead ends, decorative controls, hidden panels, or false capability claims.

Security intelligence must remain truthful to what Android, the OEM, the carrier and the device actually expose. Features that require unavailable call audio or carrier visual voicemail must report `LIMITED` or `UNSUPPORTED`, never simulate success.

## 2. Current product truth

### 2.1 Already present

The repository already contains:

- `ROLE_DIALER` flows and direct PSTN placement guarded by role/permissions;
- `InCallService` with answer, reject, disconnect, mute, hold/unhold, audio route selection, DTMF start/stop, merge/swap conference, multi-call snapshots;
- `CallScreeningService` with bounded local `ALLOW` / `SILENCE` / `BLOCK`, emergency fail-open and post-response logging;
- contacts lookup, call history, favorites, blocklist/reputation primitives and multi-SIM selection;
- default-SMS role flows, SMS/MMS sending, callbacks, conversation storage and notification controls;
- `SmsLinkAnalyzer` with local pre-open risk heuristics for suspicious URLs/social engineering;
- SMS receive-time analysis and conversation-list risk indicators;
- Phone Core emulator qualification across API 24/29/36/37.

### 2.2 Confirmed gaps

The following gaps are already evidenced in the current code/UI:

- in-call keypad can be toggled but is rendered inside a scrollable secondary region and may remain off-screen;
- audio routes use the same secondary-region pattern and may be hidden below the viewport;
- `onBringToForeground(showDialpad)` currently does not honor the requested dialpad state;
- DTMF uses fixed 150 ms click pulses rather than press/release semantics and lacks guaranteed stop on coroutine cancellation;
- there is no complete `Add call` user journey even though hold/merge/swap primitives exist;
- the dial pad does not offer `Create contact` / `Add to existing contact` from a typed number;
- the dial pad does not offer a first-class paste/edit flow and its ABC/DEF labels do not implement T9 lookup;
- user actions for unknown numbers are fragmented between dialer, recents and other surfaces instead of sharing one action model;
- SMS risk analysis exists but there is no complete safe-link click interception flow;
- conversational AI call screening, in-call fraud analysis, synthetic/deepfake voice detection, transcription, summarization and visual voicemail are not currently implemented as verified capabilities.

## 3. Scope

This design introduces five coordinated workstreams:

1. **Phone Core Journey Completion**
2. **SafeLink SMS**
3. **Call Guard**
4. **Conversation Intelligence**
5. **Voicemail Capability Layer**

The first two are required before Phone Core can be called functionally complete. Call Guard is the first security-intelligence layer. Conversation Intelligence and Visual Voicemail are capability-gated and must not block core telephony when unsupported.

## 4. Non-goals

This design does not:

- bypass Android role/permission models;
- record or capture PSTN audio through unsupported or hidden APIs;
- claim carrier/OEM functionality before physical validation;
- replace emergency-call handoff safeguards;
- make fraud or deepfake verdicts absolute;
- upload raw SMS or call audio by default;
- add referral, billing or connector-based account architecture;
- merge unrelated Auth0/Voice API work into Phone Core.

## 5. Complete Phone Core journeys

### 5.1 Dial a number

Required flow:

`Dialer → enter/paste/edit number → optional T9/contact suggestions → number intelligence → choose SIM when needed → Call → Dialing → Connecting → Active → In-call controls → Ended → Post-call actions`

Typed number actions must include:

- Call;
- Send SMS;
- Create contact;
- Add to existing contact;
- Copy;
- Block/unblock where appropriate;
- Report/review reputation where supported;
- Open number details.

Pasting must sanitize through the same canonical dial-number policy as keypad input. Pasting must never directly place a call.

### 5.2 Create or update a contact

For a non-empty valid number, surface:

- `Create contact` via Android Contacts insertion flow;
- `Add to existing contact` via a supported contacts edit/pick flow.

Sentinel must avoid silently writing contacts. The user explicitly confirms creation/update in the Android contacts UI or a Sentinel form backed by `ContactsContract` with the required permission model.

The same actions must be reachable from:

- dialer;
- recents;
- unknown caller card;
- post-call summary;
- SMS conversation header.

### 5.3 T9 and contact discovery

ABC/DEF labels become functional:

- digit sequence matches normalized contact names and phone numbers;
- results update incrementally;
- explicit number remains independently callable;
- no contact lookup if permission is unavailable;
- no fake empty state: UI distinguishes no permission, no contacts and no matches.

### 5.4 Incoming call

Required flow:

`CallScreening callback → bounded decision → caller/reputation card → Ringing → Answer/Reject → Active or Ended`

Incoming UI must show, when known:

- local contact identity;
- normalized number;
- country/prefix indicators only as indicative metadata;
- network verification state when Android exposes it;
- local reputation decision and explanation;
- block/report action outside emergency cases.

### 5.5 In-call controls

The in-call primary screen must keep critical actions visible without scrolling:

- Mute;
- Audio;
- Keypad;
- Hold/Resume;
- Add call;
- Hang up.

Opening Keypad or Audio must change the central layout immediately rather than append a panel below caller intelligence content.

When keypad is open:

- caller identity becomes compact;
- DTMF pad is immediately visible;
- Hang up remains pinned;
- reputation disclosure is collapsed/hidden;
- Android `onBringToForeground(showDialpad=true)` opens the keypad directly.

### 5.6 DTMF semantics

DTMF interaction uses press/release semantics:

- press starts `playDtmfTone(digit)`;
- release/cancel stops `stopDtmfTone()`;
- stop is guaranteed in cleanup/finally;
- only the current active call accepts DTMF;
- rapid sequential digits cannot let a stale stop cancel the next tone;
- accessibility exposes digit, star and hash labels.

A short click still produces a valid bounded tone, but long press remains possible for IVR systems that require it.

### 5.7 Add call, call waiting and conference

Complete flow:

`Active call → Add call → active call held → dialer/contact picker → second call → Active/Holding pair → Swap / Merge / End individually`

Incoming call while another call is active must expose explicit choices when Android capabilities permit:

- Reject second call;
- Answer and hold current call;
- End current call and answer second call, if platform semantics are safely supported.

Conference controls must only appear when corresponding Telecom capabilities are present.

### 5.8 Recent calls and post-call

Recents must clearly distinguish:

- incoming;
- outgoing;
- missed;
- blocked/silenced where available;
- duration;
- SIM/account when known;
- reputation/risk indicator.

Post-call actions for an unknown number:

- Call back;
- SMS;
- Create/Add contact;
- Block;
- Report;
- Copy;
- Number details.

No security claim is inferred only from a country prefix or absence from contacts.

## 6. SafeLink SMS

### 6.1 Objective

A user must be able to click links in SMS, but every link must pass through Sentinel's safety layer before external navigation.

### 6.2 Receive-time analysis

Keep the existing local `SmsLinkAnalyzer` receive-time analysis and expand it into a structured result containing per-link findings rather than only aggregate message findings.

Baseline local signals:

- cleartext HTTP;
- IP-literal host;
- misleading `userinfo`;
- URL shortener;
- suspicious TLD;
- excessive links;
- social-engineering/urgency language;
- suspicious premium-shortcode context;
- Unicode/punycode and homograph indicators;
- suspicious port/scheme;
- host/visible-text mismatch when the UI displays a disguised link;
- brand-like domain similarity only as a probabilistic signal.

### 6.3 Click-time interception

All clickable URLs in Sentinel SMS surfaces must route through a `SafeLink` decision surface.

The surface displays:

- full destination host;
- normalized/punycode host representation when relevant;
- risk level;
- specific reasons;
- whether the link is shortened;
- whether reputation lookup is unavailable.

Risk policy:

- `LOW`: primary action `Open link`;
- `MEDIUM`: warning + explicit confirmation;
- `HIGH`: primary action `Do not open`; opening requires an explicit secondary action and confirmation;
- `UNKNOWN`: state the limitation; do not display a green/safe verdict.

External opening uses a standard Android browser intent. Sentinel does not embed arbitrary remote pages in an internal WebView.

### 6.4 Optional reputation enrichment

A network reputation provider may augment local analysis only outside receive-critical paths and only through HTTPS with explicit timeouts/cache.

If unavailable, local analysis continues and the UI reports `Reputation unavailable` rather than lowering risk.

Raw message bodies must not be uploaded merely to score a URL. Prefer URL/domain-only lookups with privacy-preserving normalization where feasible.

## 7. Call Guard

### 7.1 Fast screening boundary

The existing `CallScreeningService` remains a strict, local, synchronous boundary.

No LLM, remote lookup, transcript generation or disk-heavy operation may block `respondToCall()`.

Expected latency budget remains 200–500 ms, fail-open for unknown/error cases, with emergency calls never blocked by Sentinel heuristics.

### 7.2 Post-response enrichment

After the platform response, Sentinel may enrich caller risk using:

- local block/reputation stores;
- network verification status;
- signed/synchronized reputation feeds;
- user reports;
- contact/history context;
- known fraud-source mappings where legally and technically usable.

The UI must explain why a call is considered suspicious.

### 7.3 Conversational screening

A future `Screen this call` capability may answer an unknown call and ask who is calling and why only if the device/Android/OEM stack exposes a supported, user-consented mechanism.

Capability states:

- `AVAILABLE`
- `LIMITED`
- `UNSUPPORTED`
- `PERMISSION_REQUIRED`

No button is shown as functional when capability is unsupported.

## 8. Conversation Intelligence

### 8.1 Capability gate

Conversation intelligence must be runtime-gated because third-party Android apps cannot assume access to both sides of PSTN call audio.

A capability probe determines whether the current device/OEM/legal configuration exposes sufficient audio/transcription primitives.

No capability proof → no claim of transcription, deepfake detection or full spoken-content fraud analysis.

### 8.2 Transcription

When supported and explicitly enabled:

- live transcription is visible during the call;
- speaker attribution is shown only when reliable;
- transcript is not persisted by default unless the user opts in;
- sensitive segments can be locally redacted before storage/export where supported.

### 8.3 Fraud detection during conversation

Signals may include:

- requests for one-time codes/passwords;
- urgent money transfer/payment requests;
- impersonation language;
- remote-access installation requests;
- unusual account-lock threats;
- mismatch between caller reputation and claimed organization.

Warnings must be probabilistic and explanatory, e.g. `Several scam indicators detected`, not `This caller is a scammer`.

### 8.4 Synthetic/deepfake voice detection

Only enable when a validated audio input is available and a detector has documented calibration.

Output must include uncertainty:

- `No strong synthetic-voice signal detected`
- `Synthetic-voice indicators detected`
- `Unable to assess`

Never represent a detector score as proof of identity or fraud.

### 8.5 Call summary

After a supported/transcribed call, optional summary may include:

- short summary;
- commitments/action items;
- detected risk signals;
- user-marked notes.

The summary must identify whether it came from a complete or partial transcript.

## 9. Voicemail capability layer

### 9.1 Baseline voicemail

Always provide a clear carrier voicemail entry point when available, including the traditional long-press `1` behavior only when it maps correctly for the active SIM/account.

### 9.2 Visual voicemail

Visual voicemail is enabled only when Android/carrier APIs expose it and the app is legitimately eligible to consume it.

States:

- `AVAILABLE`
- `CARRIER_UNSUPPORTED`
- `DEVICE_UNSUPPORTED`
- `PERMISSION_REQUIRED`
- `UNKNOWN`

Never fabricate a visual voicemail inbox from unsupported carrier state.

## 10. Shared capability model

Introduce a common capability truth model for advanced Phone Core features:

```text
AVAILABLE
LIMITED
UNSUPPORTED
PERMISSION_REQUIRED
ROLE_REQUIRED
PHYSICAL_VALIDATION_REQUIRED
UNKNOWN
```

Each capability records:

- source of truth;
- last checked timestamp;
- user-visible explanation;
- repair action if applicable.

This model applies to:

- DTMF;
- audio routes;
- add-call/conference;
- call waiting actions;
- conversational screening;
- transcription;
- deepfake detection;
- visual voicemail;
- carrier/OEM-only functions.

## 11. Privacy and security principles

1. Local-first analysis for call-screening decisions and SMS pre-open risk.
2. No hidden recording or unsupported audio capture.
3. Explicit consent for transcription/summary features.
4. No raw call audio retained by default.
5. No raw SMS body sent to remote reputation services by default.
6. Security findings are explainable and reversible.
7. Emergency handling stays outside heuristic blocking.
8. Unknown capability is never rendered as safe/green.
9. All remote enrichment is bounded by timeout, TLS and failure-safe behavior.
10. Security telemetry must avoid raw phone numbers/messages unless the user explicitly chooses export/support diagnostics.

## 12. UI architecture

### 12.1 Shared number action sheet

A single action model is used by Dialer, Recents, Caller ID, post-call and SMS headers:

- Call;
- SMS;
- Create contact;
- Add to contact;
- Copy;
- Block/unblock;
- Report;
- Details.

Availability is capability/permission-driven.

### 12.2 In-call layout states

Explicit mutually exclusive secondary modes:

```text
NONE
KEYPAD
AUDIO
ADD_CALL
CONFERENCE
```

The mode belongs to the active call/session identifier and resets when that call ends or changes ownership.

### 12.3 SafeLink sheet

SafeLink is a dedicated decision surface, not a generic toast/dialog. It must be readable before navigation and must show the actual host prominently.

## 13. Internal interfaces

Design intent, not implementation lock-in:

```kotlin
interface SafeLinkAnalyzer {
    fun analyzeMessage(message: String): MessageRisk
    suspend fun analyzeLink(url: String): LinkRisk
}

interface PhoneCapabilityProvider {
    fun snapshot(): PhoneCapabilitySnapshot
}

interface ConversationIntelligenceProvider {
    fun capability(): CapabilityState
    fun start(sessionId: String): Result<Unit>
    fun stop(sessionId: String): Result<ConversationResult>
}

interface NumberActionProvider {
    fun actions(number: String, context: NumberActionContext): List<NumberAction>
}
```

Implementations must remain replaceable; UI must depend on truth models, not vendor-specific APIs.

## 14. Testing and qualification gates

### 14.1 Unit/contract tests

Required coverage includes:

- number paste/sanitization;
- contact action availability;
- T9 matching;
- in-call mode reset per call ID;
- `showDialpad=true` handling;
- DTMF press/release/cancel cleanup;
- add-call state machine;
- call-waiting decisions;
- SafeLink parsing, punycode, homographs, shorteners, misleading hosts and risk policy;
- capability truth states;
- no false `AVAILABLE` for unsupported conversation intelligence/voicemail.

### 14.2 Android instrumentation

Across API 24/29/36/37 where platform support permits:

- keypad becomes visible without scrolling;
- hangup remains reachable with keypad/audio open;
- dialer number can be pasted/edited;
- contact-create intent is correctly formed;
- SafeLink click does not directly launch the browser before Sentinel decision;
- role/permission revocation produces truthful disabled states;
- add-call/conference controls follow Telecom capabilities.

### 14.3 Emulator qualification

Extend machine-readable evidence for:

- `incall_keypad_visible`
- `incall_dtmf_press_release`
- `incall_audio_panel_visible`
- `incall_add_call_path`
- `sms_safelink_intercepted`
- `sms_high_risk_link_warned`

Do not claim physical carrier behavior from emulator-only evidence.

### 14.4 Physical validation

Required on Samsung Galaxy S24+ and, where relevant, secondary Android hardware:

- real carrier incoming/outgoing calls;
- long/rapid DTMF against real IVR;
- call waiting and two-call conference;
- Bluetooth/earpiece/speaker switching;
- dual-SIM selection;
- OEM behavior of CallScreening/InCallService;
- carrier voicemail/visual voicemail;
- any conversation intelligence requiring actual call audio;
- synthetic/deepfake detector validation with controlled audio samples if technically available.

## 15. Rollout order

### Phase A — Core journey completion

- in-call keypad/audio layout;
- DTMF lifecycle;
- honor `showDialpad`;
- create/add contact from typed/recent/unknown numbers;
- paste/edit number;
- shared number actions;
- Add call / call waiting / conference journey;
- T9 contact search;
- voicemail baseline.

### Phase B — SafeLink SMS

- per-link risk model;
- clickable SMS links;
- SafeLink interception surface;
- click-time reanalysis;
- high-risk confirmation policy;
- optional privacy-bounded reputation enrichment.

### Phase C — Call Guard enrichment

- richer post-response reputation;
- security explanation UI;
- report/feedback path;
- no added latency in `CallScreeningService`.

### Phase D — Conversation Intelligence capability probe

- runtime capability model;
- explicit consent;
- transcription only where supported;
- fraud indicators;
- deepfake/synthetic voice detector only after technical validation;
- optional summary.

### Phase E — Visual voicemail

- carrier/device capability detection;
- visual voicemail UI only where legitimately supported;
- fallback to normal voicemail access.

## 16. Release criteria

Phone Core must not be labeled complete until:

- all Phase A journeys are reachable end-to-end without dead ends;
- SafeLink SMS prevents direct unsafe navigation and is tested;
- no primary action is merely decorative;
- emulator gates pass on supported API levels;
- physical-only requirements remain explicitly pending until tested;
- advanced AI capabilities truthfully degrade to limited/unsupported;
- security warnings expose reasons and uncertainty;
- permissions/roles revoked at runtime produce truthful UI and recovery flows.

`PRODUCTION READY` additionally requires physical qualification, deployment/signing and store-readiness gates outside this design.

## 17. Key risks and mitigations

- **Android/OEM audio restrictions:** capability-gate conversation intelligence; never promise unavailable capture.
- **False-positive SafeLink warnings:** explain findings, retain user override, calibrate signals with tests.
- **False deepfake claims:** uncertainty-first UX and validated detector thresholds.
- **Call-screening latency regression:** keep advanced work strictly after the synchronous response.
- **UI density during calls:** explicit in-call modes and pinned critical controls.
- **Multi-call Telecom variability:** capability-driven controls and physical validation.
- **Privacy exposure:** local-first processing, minimal persistence, explicit consent for transcript/audio-derived features.

## 18. Acceptance of this specification

Approval of this written specification permits creation of the implementation plan. It does not by itself authorize implementation until the implementation plan has been reviewed and an execution method selected.
