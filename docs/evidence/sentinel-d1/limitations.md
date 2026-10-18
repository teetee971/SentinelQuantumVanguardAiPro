# Sentinel D.1 qualification limitations

## Blocking environment limitations

1. No `adb` executable, emulator, system image or connected Samsung Galaxy S24+ is available in
   this workspace.
2. Android instrumentation tests were compiled but not executed.
3. No real before/after screenshot can be produced or honestly attached. The visual acceptance
   pass for portrait, system navigation, large fonts, TalkBack and device rendering remains open.
4. No HTTPS artifact hosting or GitHub release upload was authorized/available. The APK is
   available locally from the workspace only.

## Product qualification limitations

- The build is a debug APK signed with the Android debug certificate, not a release signature.
- Voice PSTN/call transformation remains explicitly unavailable until a real VoIP/PSTN path is
  connected. `VOICE_SESSION_HTTP_503` is surfaced as a server error with retry semantics.
- VPN gateway, DNS and IP fields remain unknown whenever the runtime does not expose proof.
- Network discovery does not imply a connection; observed/probable/unknown labels are retained.
- Digital exposure links remain absent unless a real verification result supplies a proof reference.
- Phone Core software readiness remains distinct from physical validation and does not become a
  green global security certification.

The correct final verdict before device/emulator validation is `REDESIGN_BUILT_NOT_VALIDATED`.
