# Sentinel Quantum Vanguard AI Pro — Talkie-Walkie Design

Date: 2026-10-09
Status: Design approved in conversation; implementation not started
Branch at design start: `hardening/physical-session-failclosed`
Baseline HEAD at design start: `444b46bdb43fab12da9011e66a4131896a51fa45`

## 1. Objective

Add a production-grade push-to-talk (PTT) module to Sentinel Quantum Vanguard AI Pro that is functionally distinct from the existing VoIP call path and visually distinct from conventional walkie-talkie products.

The module must combine:

- low-latency half-duplex voice communication;
- strict fail-closed microphone control;
- private/team/temporary channels;
- ephemeral authorization and secure transport;
- Android lifecycle/background correctness;
- strong visual identity: futuristic, spectacular, immersive, and premium;
- deterministic qualification before any production-readiness claim.

The design intentionally separates PTT control from the existing call transport because the current `SentinelLiveKitCallTransport` enables the microphone immediately after connecting to a room, which is incompatible with push-to-talk semantics.

## 2. Product principles

### 2.1 Functional truth over visual simulation

The UI may only display states proven by the PTT state machine and transport. No visual state may imply that audio is transmitting when the microphone is not confirmed active.

### 2.2 Microphone fail-closed

The microphone must be OFF outside the explicit `TRANSMITTING` state. Any uncertainty — lifecycle loss, network loss, room loss, focus loss, floor loss, expired credentials, process error, or watchdog timeout — must force local mute immediately.

### 2.3 Visual engine is non-authoritative

Animations, particles, halos, waveform rendering, haptics, and visual transitions must consume derived state only. They must never directly enable or disable audio capture.

### 2.4 Reuse infrastructure, not semantics

LiveKit remains the preferred RTC layer. Shared secure connection primitives may be extracted, but PTT and classic VoIP retain separate policies and state machines.

## 3. Visual direction

Selected direction: spectacular futuristic cockpit.

The primary screen combines a large central PTT control with a lightweight team cockpit around it.

### 3.1 Hero PTT core

The central PTT element should occupy approximately 35–40% of the main usable vertical area on typical phones.

Visual characteristics:

- layered circular glass core;
- 2–3 animated concentric halos;
- luminous energy rings;
- depth/parallax;
- volumetric or pseudo-3D audio waveform;
- adaptive particle field;
- voice-reactive intensity during real transmission/reception;
- haptic start/end feedback;
- clear text state in the center or immediately below it.

### 3.2 Team cockpit

Secondary information surrounds or frames the PTT core without competing with it:

- active speaker;
- channel name;
- participant presence;
- connection quality;
- encryption/security status;
- audio route (speaker/headset/Bluetooth);
- latency;
- compact activity/history strip.

### 3.3 Visual states

`DISCONNECTED`
- dim halos;
- slow or nearly stopped particles;
- disabled PTT affordance;
- explicit channel-disconnected text.

`CONNECTING`
- converging rings;
- particles moving toward the core;
- no transmit affordance.

`LISTENING`
- low-amplitude breathing halos;
- microphone locally muted;
- text: "Maintenir pour parler".

`REQUESTING_FLOOR`
- short pre-engagement pulse;
- floor acquisition indicator;
- no false transmit indication.

`TRANSMITTING`
- intensified halos;
- directional particles;
- voice-reactive waveform;
- discrete transmit timer;
- text: "Vous parlez".

`RECEIVING`
- receive-focused animation around active speaker identity;
- central core responds to remote audio activity;
- text includes current speaker.

`RECONNECTING`
- fractured/interrupted visual rhythm;
- microphone forced OFF;
- explicit reconnection text.

`FAILED`
- controlled warning state;
- no decorative ambiguity;
- actionable error message.

### 3.4 Performance degradation strategy

Visual effects must adapt to device capability, frame time, thermal pressure, and battery-saving conditions.

Possible degradation levels:

- full effects: particles + halos + depth + waveform;
- reduced effects: fewer particles and simplified blur;
- minimal effects: static/low-motion rings and simple waveform.

Reducing visual fidelity must never affect the PTT control path.

## 4. Main screen architecture

Five visual zones are recommended:

1. Top status bar: channel, security, connection, route indicators.
2. Central hero PTT core.
3. Activity ring/cockpit: active speaker and member presence.
4. Compact lower status panel: latency, last speaker, audio route, mute/listen controls.
5. Sliding contextual panel: participants, channel details, history, advanced settings, visual-effects controls.

## 5. PTT state machine

Primary states:

- `DISCONNECTED`
- `CONNECTING`
- `LISTENING`
- `REQUESTING_FLOOR`
- `TRANSMITTING`
- `RECEIVING`
- `RECONNECTING`
- `FAILED`

Optional internal transient state:

- `INTERRUPTED`

### 5.1 Allowed transmit path

`LISTENING -> REQUESTING_FLOOR -> TRANSMITTING`

Preconditions before entering `TRANSMITTING`:

- room/session connected;
- microphone permission granted;
- floor granted;
- transport available;
- valid non-expired authorization;
- no reconnect/failure state;
- audio focus acceptable;
- watchdog armed.

### 5.2 Release path

`TRANSMITTING -> LISTENING`

Required order:

1. local microphone mute immediately;
2. clear/stop floor renewal;
3. release floor lease;
4. update state to listening;
5. update visual/haptic feedback.

Network acknowledgement is not required before local mute.

### 5.3 Failure path

Any unexpected loss while transmitting must execute local mute first, then transition toward `RECONNECTING`, `LISTENING`, or `FAILED` as appropriate.

### 5.4 No automatic resume

After reconnecting, the client must return to `LISTENING`. It must never automatically restore a prior `TRANSMITTING` state.

## 6. Technical architecture

Target layers:

`Compose UI`
→ `TalkieWalkieViewModel`
→ `TalkieWalkieSessionController`
→ `PushToTalkGuard`
→ `FloorController`
→ `TalkieWalkieTransport`
→ `LiveKit`

Parallel services:

- `AudioRouteController`
- `TalkieWalkieVisualEngine`
- `TalkieWalkieSessionService` when persistent background listening is required.

### 6.1 TalkieWalkieViewModel

Responsibilities:

- expose immutable UI state;
- expose channel/member/speaker state;
- expose transport quality and audio route;
- forward user intent to the session controller;
- never directly enable or disable microphone capture.

### 6.2 TalkieWalkieSessionController

Single application-level authority for state transitions.

Responsibilities:

- enforce allowed transitions;
- coordinate floor acquisition/release;
- coordinate local mute before state changes;
- process lifecycle/transport failures;
- expose authoritative state to ViewModel/service.

### 6.3 PushToTalkGuard

Before transmission it validates:

- connected session;
- valid permission;
- valid floor;
- usable audio route/focus;
- current credentials/session validity;
- no reconnect or failure condition;
- valid press lifecycle.

It also owns or arms the independent microphone watchdog.

### 6.4 FloorController

Provides half-duplex arbitration.

V1 design:

- short-lived floor lease;
- explicit acquire;
- periodic renewal while press remains valid;
- explicit release on button release;
- automatic expiry if the client disappears.

The backend remains authoritative for floor ownership.

### 6.5 TalkieWalkieTransport

Wraps LiveKit-specific concerns:

- connect/disconnect;
- publish audio track;
- microphone mute/unmute;
- room events;
- participant presence;
- active speaker events;
- quality/connection callbacks;
- reconnection handling.

The PTT transport must connect with microphone disabled.

The existing `SentinelLiveKitCallTransport` should not simply be reused unchanged because it enables the microphone immediately after room connection. Shared connection/credential validation code may be factored into a lower-level common component if this can be done without coupling policies.

### 6.6 AudioRouteController

Handles:

- speaker;
- earpiece where appropriate;
- Bluetooth headset;
- wired headset;
- route loss;
- runtime route changes;
- audio focus.

### 6.7 TalkieWalkieSessionService

Foreground service used only where a persistent active listening session must survive backgrounding/screen lock.

Requirements:

- explicit foreground notification;
- clear channel/session state;
- transmission does not remain active merely because the service survives;
- lifecycle uncertainty forces mute.

### 6.8 TalkieWalkieVisualEngine

Input-only derived signals:

- state;
- local transmit amplitude;
- remote receive amplitude;
- network quality;
- active speaker;
- participant activity;
- frame/performance budget.

It cannot invoke transport microphone controls.

## 7. Backend and authorization

The client must never contain a persistent LiveKit server secret.

Recommended flow:

1. authenticated Sentinel client requests channel access;
2. backend validates user, device/session, channel membership, role and policy;
3. backend issues short-lived channel-scoped LiveKit credentials;
4. client connects using secure `wss://` endpoint;
5. credentials expire quickly and are renewed only after policy revalidation.

### 7.1 Channel types

- Private: explicit invitations/membership.
- Team: group-based membership.
- Temporary: short-lived operational/social channel with expiry.

### 7.2 Channel identifiers

Identifiers exposed to clients should be opaque and non-enumerable. Human-readable channel names are metadata, not authorization identifiers.

### 7.3 Minimal backend entities

`Channel`
- opaque id;
- display metadata;
- type;
- owner/team reference;
- security policy;
- status;
- expiry when applicable.

`ChannelMember`
- channel id;
- user id;
- role;
- permissions: listen/talk/invite/admin/remove;
- membership status.

`TalkSession`
- ephemeral session id;
- channel id;
- user/device/session references;
- connected timestamps/status.

`FloorLease`
- channel id;
- holder session/user;
- lease id;
- issued time;
- expiry;
- renewal metadata.

## 8. Security and privacy

### 8.1 Transport security

- `wss://` mandatory;
- short-lived credentials;
- no credential persistence in SharedPreferences/logs;
- no server secrets in APK;
- channel-scoped authorization.

### 8.2 Identity minimization

Participants should see only information needed for the channel:

- display name;
- avatar;
- role/status;
- optional trust/verified indicator.

Phone numbers and email addresses are not exposed by default.

### 8.3 Logging

Technical audit events may include:

- connection/disconnection timestamps;
- pseudonymous/opaque channel id;
- floor acquire/release timestamps;
- transport failures;
- latency/quality data.

Audio content must not be recorded by default.

No automatic transcription is part of V1.

### 8.4 Anti-abuse

V1 protections:

- maximum continuous transmission window;
- floor lease expiry;
- repeated-request throttling;
- local mute/block controls;
- moderator/admin removal where policy permits;
- server-side membership enforcement.

### 8.5 Secure channel evolution

Architecture should preserve a path toward stronger secure-channel features such as:

- device approval;
- shorter token lifetimes;
- stricter session policies;
- application-layer E2EE where supported and validated.

No claim of E2EE should be made until that path is actually implemented and tested.

## 9. Interaction model

Primary gesture:

- press and hold central PTT to request/acquire floor and transmit;
- release to stop local transmission immediately.

Recommended V1 secondary interactions:

- tap participant for detail;
- tap audio route selector;
- swipe/tap lower panel for history/activity;
- open channel details/settings.

Explicitly excluded from V1:

- complex gesture shortcuts;
- double-tap transmission modes;
- persistent "locked microphone" mode;
- automatic recording;
- location tracking as a PTT dependency.

## 10. Latency and UX targets

Targets, subject to measurement on real infrastructure:

- immediate local press feedback without falsely claiming transmit;
- floor acquisition target under approximately 250–400 ms in healthy network conditions;
- local mute on release without waiting for server acknowledgement;
- UI state transitions tied to real transport/floor state;
- no automatic transmit restoration after reconnect.

These are targets, not production claims, until measured.

## 11. Android lifecycle requirements

Transmission must be force-muted when any condition invalidates user control or session certainty, including:

- transport disconnect;
- room loss;
- floor expiry/revocation;
- unrecoverable audio-focus loss;
- lifecycle transition that invalidates current press semantics;
- watchdog timeout;
- permission revocation;
- service/controller teardown.

Background listening may remain active only through a deliberate supported session/service path with a visible foreground notification when Android requires it.

## 12. Qualification strategy

### 12.1 Unit tests

Must cover:

- legal/illegal state transitions;
- floor acquisition/renewal/release;
- release always mutes first;
- watchdog timeout;
- reconnect never auto-transmits;
- expired/revoked floor cannot transmit;
- guard rejects invalid permission/session/transport conditions.

### 12.2 Host tests

Must cover:

- credential validation;
- security invariants;
- derived UI state does not own mic control;
- floor/token policy models;
- bounded timers/leases.

### 12.3 Instrumentation tests

Must cover where platform support permits:

- microphone permission lifecycle;
- foreground/background transitions;
- screen lock/session survival behavior;
- service lifecycle;
- audio focus and route changes;
- fail-closed behavior on lifecycle disruption.

### 12.4 Emulator qualification

Target matrix aligned with the existing Sentinel Android gate strategy: API 24/29/36/37 where technically applicable.

Emulator qualification should prove state/lifecycle/session logic, not claim physical audio quality.

### 12.5 Multi-client integration

At least two independent clients must demonstrate:

- same-channel join;
- alternating floor ownership;
- no simultaneous retained floor;
- speaker identity propagation;
- disconnect/reconnect behavior;
- release/failure local mute.

### 12.6 Physical qualification

Required before `READY`:

- at least two physical Android devices;
- Samsung Galaxy S24+ included in primary validation;
- Wi-Fi and mobile data paths;
- Bluetooth headset path;
- speaker path;
- screen lock/background behavior;
- network handoff/reconnect;
- measured speech-start latency and release behavior.

## 13. READY criteria

The module cannot be labeled `READY` until all of the following are true:

- microphone cannot remain active outside `TRANSMITTING`;
- release locally mutes even if backend is unavailable;
- two clients cannot retain floor simultaneously;
- reconnect never resumes prior transmission automatically;
- authorization is server-enforced;
- no LiveKit infrastructure secret exists in the APK;
- UI reflects actual transport/PTT truth;
- multi-client integration is passing;
- physical Android qualification is passing;
- required CI/emulator gates are green on the exact release candidate head.

Before these criteria are met, status must remain `PARTIAL`, `LIMITED`, or equivalent truthful non-ready state.

## 14. Implementation sequence

Recommended implementation order after planning approval:

1. Pure PTT state model and transition tests.
2. Floor lease domain model and tests.
3. PushToTalkGuard and watchdog tests.
4. LiveKit PTT transport adapter with microphone disabled on connect.
5. Session controller integration.
6. ViewModel and immutable UI state.
7. Minimal functional Compose screen with central PTT.
8. Lifecycle/service/audio-route integration.
9. Instrumentation/emulator qualification.
10. Futuristic visual engine layered on top of proven PTT truth.
11. Multi-client backend integration.
12. Physical validation and performance tuning.

This order deliberately prevents visual work from masking incomplete PTT semantics.

## 15. Non-goals for first production increment

The first production increment does not require:

- persistent mic lock;
- public global channels;
- audio recording;
- speech-to-text/transcription;
- location sharing;
- complex moderation automation;
- carrier-call integration;
- replacing the existing classic VoIP call path.

## 16. Decision summary

Approved design direction:

- spectacular futuristic visual identity;
- large central PTT plus team cockpit;
- LiveKit retained as RTC foundation;
- dedicated PTT transport/policy separate from classic VoIP;
- microphone OFF by default;
- backend-authoritative short floor lease;
- local fail-closed mute on any uncertainty;
- visual engine strictly non-authoritative;
- ephemeral channel-scoped credentials;
- strict emulator and physical qualification before READY.
