# Sentinel D.1 evidence manifest

> Historical inventory note: the build checkpoint and APK values below predate the dedicated
> Android capture harness. They are not evidence for the current branch head. Use
> [`android-capture-harness.md`](android-capture-harness.md) and the workflow artifact for current
> runtime captures; no capture is accepted until that artifact is downloaded and inspected.

Date: 2026-10-18  
Branch: `feat/sentinel-d1-3d-total-redesign`  
Implementation checkpoint: `9b670dc765fd3dec678b260cdd24769195466343`  
APK build: `native-android-app/app/build/outputs/apk/debug/app-debug.apk`

## Runtime evidence boundary

The workspace has no `adb`, emulator, Android system image or connected device. Therefore every
visual capture is explicitly marked `BLOCKED_NO_ANDROID_RUNTIME`; no mock, generated image or old
APK is presented as proof of the new UI.

## Screen inventory

| Surface | Native implementation changed | D.1 component / truth connection | Before capture | After capture | Automated evidence |
|---|---|---|---|---|---|
| Accueil / Command Center | `HomeScreen.kt` | `SentinelQuantumCore`, real Phone Core/network/VPN/community facts, quick actions | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Home visual contracts; unit tests |
| Communications Hub | `CommunicationsHubScreen.kt` | D.1 hero and status modules for phone, calls, SMS and unavailable external channel | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Kotlin compile; static route compilation |
| Dialer / Contacts / Recents | `SentinelDialerActivity.kt` | Interactive core nodes route to existing tabs, roles and permission flows | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Phone/Telecom contracts; standalone smoke compilation |
| Caller ID / number sheet | `CallerIdActivity.kt`, `PhoneSecurityScreen.kt` | Number, origin, local and community evidence stay unknown/limited without proof | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Existing reputation and presentation tests |
| Incoming / active call | `SentinelInCallActivity.kt` | Caller trust, call state, audio route and dialpad nodes; no emergency bypass change | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Telecom and in-call policy tests |
| SMS / MMS conversations and compose | `SmsComposeActivity.kt`, `SmsScannerScreen.kt` | D.1 Phone Core, message/link/anti-phishing states, existing SMS/MMS transport | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | SMS/MMS and threat presentation tests |
| Phone Core activation / diagnostic | `PhoneCoreActivationActivity.kt`, `PhoneCoreDiagnosticActivity.kt` | Runtime Android facts mapped to locked/ready/unknown nodes; lifecycle preserved | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Phone Core lifecycle/certification tests |
| Protection overview | `PhoneSecurityScreen.kt` | Calls, SMS, network, VPN and reputation nodes connected to observed prerequisites | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Protection visual source contract |
| Network Center | `NetworkSurveillanceScreen.kt` | Wi-Fi/Bluetooth topology labels `OBSERVÉ`, `PROBABLE`, `INCONNU`; no connected claim from detection | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Network/VPN presentation tests |
| Sentinel VPN | `VpnScreen.kt` | Runtime tunnel state; gateway only observed when protected; DNS/IP unknown unless measured | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | VPN controller/presentation tests |
| Empreinte numérique | `DigitalExposureScreen.kt` | `SentinelEvidenceGraph`; edges require proof references and sources/freshness remain visible | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Exposure source contract and password tests |
| Audit / System Doctor | `SecurityAuditScreen.kt`, `SystemDoctorScreen.kt`, `AppPermissionAnalyzerScreen.kt` | Evidence-layer modules, permission/role/storage/battery facts, no antivirus certification | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Device analysis and audit tests |
| Studio voix | `VoiceStudioActivity.kt`, `SentinelVoiceVisualizer.kt` | Recording/effect/PTT/server/PSTN nodes; HTTP 503 is retryable error; PSTN remains locked | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Voice/session/PTT tests |
| Défense collective | `CollectiveDefenseScreen.kt` | Campaign, alert, reputation, report and privacy states; contributor identity not exposed | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Collective-defense tests |
| Assistant Sentinel | `AssistantScreen.kt`, `AssistantPromptPolicy.kt` | Local advice marked `TEST`; provenance and technical proof remain separate | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Assistant policy unit tests |
| Paramètres / navigation | `SettingsScreen.kt`, `MainActivity.kt`, `SentinelChrome.kt` | Shared top bar, D.1 bottom navigation, settings and diagnostic destinations preserved | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Navigation route tests; Android-test compilation |
| OSINT feed / detail | `OsintFeedScreen.kt`, `OsintDetailScreen.kt` | Source, coverage, cache and safe HTTPS link states separated from security verdicts | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Feed and route tests |
| Journaux locaux | `LocalLogsScreen.kt` | Local evidence, search, sanitized export and privacy modules | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Unit tests; Kotlin/lint |
| E-mail / call blocking / history / lists / search / smart home / about / compliance | Corresponding `ui/screens/*.kt` | Legacy hero API now renders through the shared native D.1 quantum core; existing actions remain below | `BLOCKED_NO_ANDROID_RUNTIME` | `BLOCKED_NO_ANDROID_RUNTIME` | Full unit suite; lint; Android-test compilation |

## Build evidence

- `:app:compileDebugKotlin`: passed.
- `:app:compileDebugAndroidTestKotlin`: passed.
- `:app:testDebugUnitTest`: passed, 1,405 tests.
- `:app:lintDebug`: passed.
- `:app:assembleDebug`: passed.
- Final APK: `app-debug.apk`, 98,946,595 bytes, SHA-256 `849aab489fe08637fe64d0e659430db5c7b9d1e2c3004f7a2864f7d1193350c7`.
- Package: `com.sentinel.quantum` (`versionCode 10`, `versionName 1.0.9`).
- Signature: Android debug certificate, APK Signature Scheme v2 verified.
- Instrumentation execution: blocked because no Android runtime is present.
- Visual screenshot comparison: blocked for the same reason.
