# Sentinel Talkie-Walkie — Approved V1 Addendum

Date: 2026-10-09
Status: approved follow-up to `2026-10-09-talkie-walkie-design.md`

This addendum records five realistic differentiators accepted after the main architecture review. They are subordinate to the fail-closed PTT core: none may weaken microphone safety, floor ownership, lifecycle correctness, or truthful UI state.

## A1. Whisper / targeted sub-channel

V1 must preserve a path for targeted PTT to one participant or a selected subset without leaving the primary channel. The initial production increment may implement the domain/API boundary before full server rollout, but the transport contract must support a scoped target set distinct from the primary channel audience.

Constraints:
- no implicit broadening from whisper to whole-channel transmit;
- target membership must be server-authorized;
- UI must clearly distinguish normal channel transmit from whisper transmit;
- local fail-closed mute rules remain identical.

## A2. Temporary channels

Support channels with server-defined expiry (`expiresAt`). Expired channels must reject new floor acquisition and new credentials. The client must not extend channel lifetime locally.

V1 UX should make temporary status visible and show remaining validity without exposing internal identifiers.

## A3. Real network quality model

Expose a derived quality state based on real transport metrics rather than a decorative signal icon.

Inputs should include, where available:
- round-trip latency;
- jitter;
- packet loss;
- reconnect frequency / recent transport instability.

The first implementation should produce a small stable enum such as `EXCELLENT`, `GOOD`, `DEGRADED`, `UNUSABLE`, with thresholds kept in one testable policy class. This model may drive the cockpit visuals but cannot modify microphone safety rules.

## A4. Bluetooth / physical PTT trigger

The architecture must allow a hardware or Bluetooth action to request/release PTT through the same `TalkieWalkieSessionController` path used by the on-screen button.

Requirements:
- no alternate path may directly unmute the microphone;
- press/release semantics must be explicit;
- disconnecting the trigger device while transmitting must force release/mute;
- unsupported devices degrade to on-screen PTT with no false readiness claim.

## A5. Short local replay buffer

A privacy-preserving optional replay capability may retain only a short recent receive window in volatile memory for missed speech.

Recommended initial bound: maximum 20 seconds of decoded receive audio, memory-only, disabled by default until UI/consent is wired.

Requirements:
- never persisted to disk by the replay component;
- cleared on channel leave, process teardown, secure-channel policy change, or user action;
- never records local microphone transmission;
- not described as call recording;
- secure-channel policy may disable it entirely.

## A6. Priority order

Implementation priority remains:
1. fail-closed PTT state machine and floor ownership;
2. transport/session/lifecycle correctness;
3. truthful functional UI;
4. network quality and Bluetooth trigger;
5. temporary channels and whisper;
6. local short replay;
7. futuristic visual effects layered over proven state.

No item in this addendum changes the `READY` criteria of the main design. Physical validation remains required for Bluetooth, real audio latency, network handoff, and final production readiness.
