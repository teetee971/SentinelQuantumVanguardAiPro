# Google Play — QUERY_ALL_PACKAGES declaration evidence

Status: implementation-ready; Play Console submission still required before production release.

## Core user-facing purpose

Sentinel Quantum Vanguard AI Pro is a security application. Its System Doctor includes an explicit Android antimalware protection module. After the user enables that module, Sentinel must discover the installed application inventory in order to scan arbitrary installed apps for locally observable malware indicators, risky capability combinations and SHA-256 reputation matches.

A finite package allowlist or manifest `<queries>` list cannot provide this antivirus function because the set of third-party apps that may be installed, newly installed or malicious is not known in advance. Android's own documentation lists security and antivirus apps among the cases where `QUERY_ALL_PACKAGES` can be appropriate.

## User disclosure and control

- Antimalware package-inventory access is disabled by default.
- System Doctor explains what inventory is accessed and why before activation.
- The user explicitly enables or disables the protection with a visible switch.
- Disabling protection cancels the periodic antimalware scan work.
- System Doctor reports the protection as unknown/not activated when opt-in is absent.

## Data minimization

The installed-app inventory is used on-device for security analysis only. Sentinel does not sell it and does not use it for advertising or analytics monetization. The periodic protection state stores only coarse metadata: scan timestamp, observation completeness and risk counters. Sentinel does not distribute its proprietary threat-reputation corpus to the device. If remote reputation is enabled in a future production release, only the SHA-256 of the individual APK being checked may be queried; the complete package inventory must not be uploaded.

## Play Console declaration text

Permission: `android.permission.QUERY_ALL_PACKAGES`

Permitted-use category: antivirus / security application.

Justification: broad installed-app visibility is required by the core, user-facing antimalware function to inspect arbitrary installed applications. Targeted `<queries>` declarations are insufficient because the packages to be inspected cannot be known in advance. Access is disabled until explicit user activation in System Doctor and is used only for malware/security analysis.

## Release gate

Before Play production submission:

1. submit the Permissions Declaration Form for `QUERY_ALL_PACKAGES`;
2. ensure the Play listing prominently describes the antimalware function;
3. ensure Data Safety/privacy disclosures match the exact release;
4. provide reviewer instructions showing System Doctor > Protection antimalware and the opt-in switch;
5. re-run APK/AAB, product-truth, manifest, CodeQL and physical-device validation.

This document is repository evidence only. It is not proof that Google Play has approved the permission.
