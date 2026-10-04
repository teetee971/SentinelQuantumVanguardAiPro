# Android developer qualification — emulator first

## Policy

The primary developer qualification gate for the Android application is automated CI on Android emulators. A repository-owner approval is not part of the merge qualification policy.

For pull requests targeting `main`, the existing required `Analyze Android (Java/Kotlin)` status remains the stable branch-protection context. That status may pass only after the exact pull-request head has successfully completed the Android build/install flow, Android connected instrumentation, AAB packaging checks, product-truth checks, security/governance checks and the other universal preproduction gates listed in `.github/workflows/codeql-analysis.yml`.

Repository-owner pull requests from branches in this repository are eligible for auto-merge. Auto-merge does not bypass branch protection and does not execute pull-request code in the privileged `pull_request_target` workflow.

## Emulator qualification scope

Developer qualification is expected to prove, automatically and fail-closed where technically possible:

- source and manifest security validation;
- unit tests and Android lint;
- dependency inventory and packaging integrity;
- APK construction, alignment, signature verification and packaged-secret checks;
- installability and cold-start behavior on the supported Android baseline emulator;
- first-run / Phone Core activation-resume behavior;
- Android 16 Phone Core synthetic telecom/SMS flow;
- connected Android instrumentation tests on the target Android emulator;
- AAB build/package checks;
- product capability truth, isolation, integrity and security-governance gates;
- absence of crash/ANR evidence in the emulator flows covered by CI.

The emulator gate qualifies the software revision for developer integration. It does not manufacture physical-device evidence.

## Residual physical / production boundaries

Manual or physical validation remains only where an emulator cannot provide equivalent evidence or where release credentials create a materially different trust boundary. Examples include:

- real carrier voice routing and incoming/outgoing calls;
- real SMS/MMS delivery reports and carrier behavior;
- Bluetooth, Wear OS and accessory interoperability on physical hardware;
- microphone/audio-route behavior during real calls, including OEM restrictions;
- OEM/background-process restrictions, battery policy and lock-screen behavior that differ from AOSP emulator behavior;
- Play Integrity / hardware-backed attestation evidence that requires a real supported device;
- production signing secrets and protected production-release environment controls;
- final store/commercial publication decisions.

These residual checks are certification/release evidence, not routine developer merge approval.

## Non-regression rule

Do not reintroduce a mandatory repository-owner review for ordinary developer pull requests. If a future Android capability can be tested reliably in emulator or instrumentation CI, add it to the automated qualification path before adding a human checklist item.
