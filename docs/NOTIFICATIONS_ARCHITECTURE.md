# Notifications Architecture — Sentinel Quantum Vanguard AI Pro

## Objective

Provide reliable security notifications for the Sentinel Android application without Firebase and without coupling Sentinel to A KI PRI SA YÉ.

**Isolation rule:** no Firebase SDK, FCM configuration, `google-services.json`, Firebase token or A KI PRI SA YÉ identifier may be required by Sentinel.

## Phase 1 — Local notifications

The baseline is Android's native notification framework. Security events generated locally by Sentinel are delivered through `NotificationManager` and notification channels.

Use WorkManager for deferred/retryable local processing where appropriate. Do not use a remote push provider merely to display an alert generated on the device.

## Phase 2 — Sentinel-controlled remote alerts

If Sentinel later requires remote alerts, use a dedicated Sentinel backend under Sentinel's own domain and identity. The backend must authenticate each device, authorize topics, sign payloads where appropriate, rate-limit senders, and reject malformed or replayed messages.

A possible transport is a WebSocket endpoint hosted by Sentinel infrastructure:

```text
[Sentinel backend] <-- authenticated channel --> [Sentinel Android app]
        |
        +--> signed security alert
```

The exact endpoint must be defined by the deployed Sentinel infrastructure. Documentation must not invent a production hostname.

## Phase 3 — Offline/recovery fallback

For alerts that do not require immediate remote delivery, use WorkManager with an appropriate retry policy. The application should remain functional when the network is unavailable.

## Notification channels

Recommended channels:

- `critical_alerts` — immediate security action required
- `high_priority` — important security event
- `standard_alerts` — normal security information
- `low_priority` — non-urgent background information

Channel importance must follow Android platform rules. Do not assume that an application can bypass user notification settings or Do Not Disturb.

## Security requirements

Remote notification payloads must be treated as untrusted input.

Required controls:

1. Authenticate the device/session.
2. Authenticate and authorize the sender.
3. Validate payload size, schema, type, priority and timestamp.
4. Reject expired or replayed messages.
5. Verify a cryptographic signature when the threat model requires end-to-end authenticity.
6. Rate-limit notifications to prevent spam or denial of service.
7. Never execute arbitrary code or commands from notification payloads.
8. Avoid placing sensitive information in notification text visible on a locked screen.
9. Log security-relevant failures without logging credentials or sensitive payloads.
10. Provide a local emergency notification kill switch.

## Testing

Test at minimum:

- malformed JSON/payloads;
- oversized payloads;
- unknown notification types;
- invalid priorities;
- invalid timestamps and replayed messages;
- authentication failures;
- authorization failures;
- network loss and reconnection;
- battery optimization;
- notification permission denial;
- Android version differences;
- notification flooding/rate limiting.

## Repository isolation check

Before release, verify that the Android project contains none of the following operational dependencies:

```text
com.google.firebase
firebase-messaging
firebase-admin
google-services.json
FIREBASE_TOKEN
akiprisaye
a-ki-pri-sa-ye
com.akiprisaye
```

A mention of these terms in security documentation describing their prohibition is not itself an operational dependency; the source/configuration scan must distinguish documentation from build/runtime inputs.

## Release checklist

- [ ] Native notification channels tested
- [ ] Notification permission behavior tested
- [ ] Remote transport, if enabled, uses Sentinel infrastructure only
- [ ] Authentication and authorization tested
- [ ] Replay protection tested
- [ ] Rate limiting tested
- [ ] Malformed-input tests pass
- [ ] Offline behavior tested
- [ ] No Firebase SDK or FCM configuration
- [ ] No A KI PRI SA YÉ identifier or credential
- [ ] No secrets embedded in APK

## Architectural decision

Firebase/FCM is intentionally excluded from Sentinel. Any future remote notification system must be independently designed, authenticated, threat-modeled and tested as a Sentinel component.
