# Voice Studio — optional paid add-on

## Product contract

Voice Studio is a separate optional add-on. It must never reduce the free Phone Core feature set.

Current repository state:

- a local Android voice preview exists;
- the preview records exactly three seconds from the microphone after an explicit user action;
- the sample stays in RAM, is not written to disk, and is not sent to a Sentinel service;
- the preview refuses to run while Android reports an active telephony/communication session;
- the live paid feature is **not commercialized** and no purchase flow is active.

Target commercial hypothesis:

- monthly add-on: **€2.99/month**;
- annual add-on: **€24.99/year**;
- these prices are non-contractual until store billing, taxes, terms, entitlement delivery, support, and the live media engine are validated together.

## Android platform boundary

A third-party Android dialer can own the call UI and call-management role, but public Android APIs do not grant it a supported path to rewrite the microphone uplink of a carrier/SIM call.

The Android audio sources for carrier-call uplink/downlink require privileged `CAPTURE_AUDIO_OUTPUT`. Sentinel must not attempt to bypass that boundary with accessibility tricks, speaker re-capture, root assumptions, OEM-private APIs, or undocumented routing.

Therefore:

- `CARRIER_PSTN` voice transformation is fail-closed and unsupported;
- payment never changes that platform result;
- live modulation may only be implemented inside a media path Sentinel itself controls, such as a future Sentinel-managed VoIP call.

## Local preview

The preview exists to let a user hear the intended product direction without falsely presenting a live phone-call capability.

Initial presets:

- Natural;
- Lower pitch;
- Higher pitch.

The current preview uses Android playback pitch processing. It is not a voice-anonymization system and must not be marketed as one.

## Live VoIP acceptance gates

The paid live entitlement must remain unavailable until all of the following are proven:

1. Sentinel owns the VoIP microphone-to-encoder media path.
2. The DSP engine can be enabled/disabled during a real VoIP call without restarting the call.
3. Additional DSP latency is measured and remains within the release target.
4. Echo cancellation, noise suppression, Bluetooth routing, speaker routing, wired headsets, and reconnect behavior are physically tested.
5. The original microphone stream is not persisted unless a separate explicit recording feature and consent flow is activated.
6. Audio processing stops immediately when the call ends, the app loses the media session, or the entitlement is revoked.
7. Emergency/carrier calls never enter the modulation path.
8. Entitlement state is server-verifiable, reversible, and fail-closed.
9. Play Store billing/terms/privacy disclosures match the exact shipped behavior.
10. Android physical-device testing covers at least Samsung plus another vendor before the add-on is labeled available.

## Truth invariant

The product must distinguish these states:

- **Preview available** — local sample only;
- **Engine not validated** — VoIP live path exists or is being built but cannot be sold;
- **Purchase required** — engine validated, entitlement absent;
- **Ready** — validated VoIP engine + active entitlement;
- **Unsupported by Android** — carrier/SIM call modulation.

No UI, pricing page, release note, or marketing surface may convert **Unsupported by Android** into **Ready**.
