# Sentinel — Registre de remédiation et qualification

Date de référence : 2026-10-24
Branche de travail : `feature/sentinel-d1-official-ui-20261024`
Dépôt : `teetee971/SentinelQuantumVanguardAiPro`

## Portée et limites

Revue de code ciblée et inventaire GitHub, sans exécution CI nouvelle ni essais sur téléphone durant ce rapport. Les nombres d'écart entre branches sont des instantanés, à recalculer avant intégration. `CONFIRMED` signifie observation directement démontrée dans les sources ou métadonnées; `NOT_QUALIFIED` signifie preuve insuffisante, **pas** vulnérabilité avérée. Aucun mécanisme de sécurité ne doit être désactivé pour obtenir un statut vert.

## Registre

| ID | Priorité | Statut | Domaine | Constat | Source de preuve | Remédiation | Critère de clôture |
|---|---|---|---|---|---|---|---|
| SNT-001 | P0 | CONFIRMED | CI | GitHub Actions jobs fail before runner allocation | Runs 40144020653 / 40142070569 / 40126600470; runner_id=0, steps=[] | Identify GitHub Actions pre-run failure and restore hosted runners without bypassing required gates | A complete run executes steps and produces downloadable logs; required workflows pass on exact head |
| SNT-002 | P1 | CONFIRMED | CI | Watchdog schedule 5 min rather than intended hourly | .github/workflows/sentinel-watchdog.yml; PR #1729 | Review and merge isolated hourly schedule fix when gates are available | Effective cron hourly and execution recorded |
| SNT-003 | P0 | CONFIRMED | Integration | D1 branch not integrated with main | D1 ahead main 150 commits / 122 changed files at inspection; BLE ancestor is 16 commits behind D1 | Build an exact-SHA consolidation matrix; isolate regressions; preserve required PR review | Single qualified release candidate; no unresolved required checks |
| SNT-004 | P0 | NOT_QUALIFIED | Offline Mesh | Cryptographic/GATT implementation lacks real-device verification | Offline Mesh phase5 and remediation reports; unit tests reported, instrumentation unavailable | Execute Android Keystore instrumentation and authenticated BLE exchange between two physical devices | Successful independent physical proof, replay/revocation/reconnection tests |
| SNT-005 | P0 | NOT_QUALIFIED | Phone Core | No measured Telecom screening latency or operator-call lifecycle proof | SentinelCallScreeningService.kt / SentinelInCallService.kt | Record P95/P99 timing and exercise real inbound/outbound calls, role loss, SIM change; never dial emergencies for test | Time budget satisfied and lifecycle/evidence verified on hardware |
| SNT-006 | P0 | NOT_QUALIFIED | SMS/MMS | Concurrent callback, crash recovery and operator MMS qualification pending | SentinelSmsStatusReceiver.kt; MmsProviderProjectionTransaction.kt; MmsRoleLossTransactionTest.kt | Run forced-failure JVM/instrumentation and physical carrier scenarios | No duplicate provider rows, lost writes or fabricated delivery confirmations |
| SNT-007 | P1 | NOT_QUALIFIED | Networking | VPN Tunnel.UP reported as PROTECTED without independently proven egress integrity | SentinelVpnController.kt | Test DNS/traffic leakage, reconnects, Android VPN state and tunnel teardown | Protection UI reflects observed network evidence |
| SNT-008 | P1 | CONFIRMED | Web | CSP allows unsafe-inline scripts and styles | _headers | Migrate inline script/style to external resources or hashes; enforce after regression | No unsafe-inline permitted; browser smoke and CSP reports pass |
| SNT-009 | P1 | NOT_QUALIFIED | Web/PWA | Service Worker cache behavior and private-response exclusion unproven | public/sw.js | Test updates, offline fallback and no private data caching | Repeatable browser tests and real deploy verification |
| SNT-010 | P1 | NOT_QUALIFIED | Identity | Session revocation must be tested through in-flight requests and process restart | Auth0SessionController.kt; AuthSessionRevocation.kt | Test logout/revocation races and lifecycle recovery | Revoked tokens cannot authorize later state updates |
| SNT-011 | P1 | CONFIRMED | Telecom | French-only domestic number normalization | CallRuleEngine.kt normalizeNumber/normalizePrefix | Add contextual region routing and DOM/international regression vectors; avoid changing filtering semantics silently | E.164 test matrix passes with no emergency regression |
| SNT-012 | P1 | NOT_QUALIFIED | Android UI | Permissions summary fixed in source but exact post-fix view not verified | AppPermissionAnalyzerScreen.kt; commit 89b51b56 | Physical screenshots across font scaling and portrait mode | Counters, labels and navigation never overlap |
| SNT-013 | P1 | NOT_QUALIFIED | Android publish | Production release, signatures and Play listing not qualified | .github/workflows/android-release.yml; native-android-app/app/build.gradle | Complete signing, Play policy, manifest, device coverage and provenance checks | Signed artifacts linked to passing exact-head evidence |
| SNT-014 | P1 | NOT_QUALIFIED | Privacy | Lock-screen notification settings not physically tested | SmsNotificationHelper.kt; SmsNotificationPrivacy.kt | Test sender/OTP hiding under Android lock-screen privacy settings | No sensitive preview when disabled |
| SNT-015 | P1 | NOT_QUALIFIED | Supply chain | No full recent successful SCA/SAST evidence for D1 head | Dependabot config; codeql-analysis.yml; build-native-android.yml | Run pinned CI checks, dependency audit and provenance verification | Reports archived and actionable findings triaged |
| SNT-016 | P1 | NOT_QUALIFIED | APIs | API 28 lint fix needs isolated adoption | fix/selected-apk-inspector-api28-20261024; SelectedApkInspector.kt | Port only targeted source diff and run lint against integration target | lintDebug pass on target branch |

## Politique de qualification

- `READY` : preuve récente, reproductible et liée au SHA exact, tests obligatoires réussis, scénario physique requis validé.
- `LIMITED` : fonctionnalité partielle, dépendance système non accordée, ou preuves de bout en bout manquantes.
- `LOCKED` : sécurité ou activation empêchant l'usage, sans contournement automatique.
- `NOT_READY` : livraison non qualifiée. Ne pas convertir une compilation debug ou un test JVM isolé en preuve de production.

## Ordre d'exécution

1. Diagnostiquer les échecs avant attribution du runner (SNT-001), sans relancer massivement les workflows.
2. Requalifier les corrections CI isolées et le lint API 28 (SNT-002, SNT-016).
3. Établir la matrice de consolidation exact-SHA (SNT-003).
4. Exécuter tests JVM, instrumentés et physiques critiques (SNT-004 à SNT-007).
5. Durcir le Web, les sessions et les preuves de publication (SNT-008 à SNT-015).

Aucune fusion de `main` n'est autorisée par ce document. Les responsables, dates de correction et résultats de tests doivent être ajoutés lors de l'exécution réelle, jamais inférés.
