# Sentinel D.1 — Android capture harness

Current status: `CAPTURE_HARNESS_IMPLEMENTED_NOT_VISUALLY_VALIDATED`.

This evidence path is deliberately separate from source-level visual contracts. The instrumentation
test launches the real Compose routes and same-package Activities on Android, waits for the display
to settle, calls `UiAutomation.takeScreenshot()`, and writes the PNGs to the app-private
`files/d1-captures/` directory. The CI workflow pulls those exact files with `run-as` and records
the APK commit, Android API, emulator model, SHA-256 values and capture count.

## Reproducible command

Run the workflow manually on the exact branch or commit:

```text
Sentinel D1 visual capture
```

The workflow is `.github/workflows/sentinel-d1-visual-capture.yml`. It refuses to attest a capture
set unless the checkout SHA and built SHA agree and all 13 expected PNGs are present. The resulting
artifact is emulator evidence, not Galaxy S24+ evidence.

## Execution record — 2026-10-18

The harness was compiled from commit `c4c8a87668ac52d9830aff8b2739123c2959b5dc` and exercised
against real Android emulator processes in the workspace. No valid PNG evidence was retained:

| Attempt | Result | Evidence boundary |
|---|---|---|
| GitHub Actions run `39165859131` | Not started | GitHub blocked the job before provisioning because the account is locked for a billing issue ([run](https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions/runs/39165859131)) |
| Android 16 API 36, software x86_64 | Boot timeout | No `sys.boot_completed`; no application capture |
| Android 10 API 29, `google_apis` x86_64 | Runtime services unstable | Package Manager/ADB failed during instrumentation; no PNG |
| Android 10 API 29, `default` x86_64 | Runtime ANR | `system_server`/`SystemUI` entered ANR under TCG; no PNG |

The API 29 manual path did install the APK and the instrumentation APK on one attempt, and launched
the official `AndroidJUnitRunner`, but it did not produce a complete set of 13 valid files before
the emulator became unresponsive. The screenshot directory was therefore rejected rather than
treated as evidence. The workspace has no connected physical device or Galaxy S24+.

## Capture mapping

| Capture | Official reference | Android surface | Runtime entry |
|---|---|---|---|
| `01-fiche-numero.png` | Fiche numéro | Caller ID | `CallerIdActivity` with a reserved documentation number |
| `02-messages-securises.png` | Messages sécurisés | SMS/MMS | `SmsComposeActivity` with conversations requested |
| `03-contacts-sentinel.png` | Contacts Sentinel | Contacts | `SentinelDialerActivity` with contacts tab requested |
| `04-vpn-prive.png` | VPN privé | VPN | `Screen.Vpn` / `VpnScreen` |
| `05-planche-coherence-shell.png` | Planche des interfaces | D.1 shell representative | `Screen.Home` / `HomeScreen` |
| `06-command-center.png` | Command Center | Accueil | `Screen.Home` / `HomeScreen` |
| `07-analyse-appareil.png` | Analyse de l’appareil | System Doctor | `Screen.SystemDoctor` / `SystemDoctorScreen` |
| `08-detail-menace.png` | Menace détectée | Détail menace | `Screen.ThreatDetail` / `ThreatDetailScreen` |
| `09-protection-temps-reel.png` | Protection temps réel | Protection | `Screen.PhoneSecurity` / `PhoneSecurityScreen` |
| `10-quarantaine-securisee.png` | Quarantaine sécurisée | Quarantaine | `Screen.Quarantine` / `QuarantineScreen` |
| `11-applications-a-risque.png` | Applications à risque | Permissions/applications | `Screen.AppPermissionAnalyzer` / `AppPermissionAnalyzerScreen` |
| `12-carte-menaces.png` | Carte mondiale des menaces | Carte | `Screen.ThreatMap` / `ThreatMapScreen` |
| `13-rapport-analyse.png` | Rapport d’analyse | Audit | `Screen.SecurityAudit` / `SecurityAuditScreen` |

The official planche is a cross-screen reference and has no one-to-one Android destination. Its
capture is explicitly labelled representative; the other 12 runtime PNGs are the evidence used for
the global comparison.

## Qualification boundary

- A successful workflow proves that these surfaces rendered on the stated Android emulator.
- It does not prove Galaxy S24+ layout, GPU performance, TalkBack, real roles, carrier calls, SMS,
  MMS or VPN operation.
- Unknown, limited or locked runtime states remain visible; the harness does not seed fake security
  evidence or grant Android roles silently.
- No capture is considered available until the workflow artifact is downloaded and each PNG is
  inspected in the current run.
