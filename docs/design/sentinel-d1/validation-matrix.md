# Sentinel D.1 phase 3 — matrice de conformité

Cette matrice sépare la fidélité de composition implémentée dans le code Android de la preuve
visuelle. Les 13 fichiers de référence sont conservés byte à byte sous
[`reference-source/visuels`](reference-source/visuels/). Aucun visuel de référence n’est chargé à
l’exécution.

| Réf. | Écran Android et code | Scène native / composants | Données raccordées | Test de contrat | Capture Android | État |
|---|---|---|---|---|---|---|
| 01 `1000415621.png` | `AppPermissionAnalyzerScreen.kt` / `Screen.AppPermissionAnalyzer` | `RISK_APPLICATIONS`, cube, orbites, `SentinelReferenceMetricStrip` | `AppPermissionAnalyzer`, permissions et heuristiques locales | `OfficialD1ScreenSourceContractTest`, `SentinelHolographicSceneSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 02 `1000415622.png` | `ThreatMapScreen.kt` / `Screen.ThreatMap` | `THREAT_GLOBE`, globe, points et liaisons neutres | OSINT/collectif seulement si une preuve géographique existe | `OfficialD1ReferenceRoutesSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 03 `1000415623.png` | `SecurityAuditScreen.kt` / `Screen.SecurityAudit` | `ANALYSIS_REPORT`, smartphone multicouche, métriques d’audit | `SecurityAudit`, permissions et contrôles exécutés | `OfficialD1ScreenSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 04 `1000415624.png` | `QuarantineScreen.kt` / `Screen.Quarantine` | `QUARANTINE_CHAMBER`, cylindre et états verrouillés | pipeline MMS local explicite ; aucune isolation globale inventée | `OfficialD1ReferenceRoutesSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 05 `1000415625.png` | `PhoneSecurityScreen.kt` / `Screen.PhoneSecurity` | `REAL_TIME_PROTECTION`, sphère/bouclier et bande de preuves | rôles, permissions et `ProtectionOverviewPresentation` | `OfficialD1ScreenSourceContractTest`, `ProtectionVisualSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 06 `1000415626.png` | `ThreatDetailScreen.kt` / `Screen.ThreatDetail` | `THREAT_DETAIL`, noyau critique rouge conditionnel | dossier réel sélectionné ; sinon `UNKNOWN` | `OfficialD1ReferenceRoutesSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 07 `1000415627.png` | `SystemDoctorScreen.kt` et diagnostic Phone Core | `DEVICE_ANALYSIS`, couches de smartphone, métriques système | diagnostic Android, stockage Sentinel et scan local | `OfficialD1ScreenSourceContractTest`, `OfficialD1ActivitySourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 08 `1000415628.png` | `HomeScreen.kt` / `Screen.Home` | `COMMAND_CENTER`, sphère centrale, orbites, modules et actions | `HomeSecurityPresentation`, Phone Core, VPN, réseau, communauté | `OfficialD1ScreenSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 09 `1000415629.png` | `SentinelD1.kt`, `SentinelChrome.kt` et surfaces phase 3 | tokens centralisés, glass cards, scènes spécialisées et navigation | états runtime propres à chaque surface | `SentinelHolographicSceneSourceContractTest`, tests de navigation | Non — planche de cohérence uniquement | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 10 `1000415630.png` | `VpnScreen.kt` / `Screen.Vpn` | `VPN_GLOBE`, globe, tunnel et orbites | `SentinelVpnRuntimeStateRegistry` et contrôleur VPN | `OfficialD1ScreenSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 11 `1000415631.png` | `SentinelDialerActivity.kt`, onglet Contacts | `CONTACT_ORBIT`, portrait holographique et provenance | `ContactsContract`, contacts et numéros réellement lus | `OfficialD1ActivitySourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 12 `1000415632.png` | `SmsComposeActivity.kt` et `SmsScannerScreen.kt` | `SECURE_MESSAGES`, identité, conversation et risque phishing | transport SMS/MMS natif, `SmsLinkAnalyzer`, inbox locale | `OfficialD1ActivitySourceContractTest`, `OfficialD1ScreenSourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |
| 13 `1000415633.png` | `CallerIdActivity.kt` / fiche numéro | `NUMBER_IDENTITY`, portrait d’identité inconnue, orbites et provenance | répertoire local, ARCEP, réputation autorisée, journal privé | `OfficialD1ActivitySourceContractTest` | Non — aucun runtime Android disponible | `D1_IMPLEMENTED_CAPTURE_PENDING` |

## Composants communs effectivement utilisés

- `SentinelHolographicScene` : Canvas Compose natif, profondeur 2.5D, anneaux, orbites, couches,
  points et scènes spécialisées.
- `SentinelReferenceMetricStrip` et `SentinelReferenceActionGrid` : panneaux compacts sans
  troncature volontaire des valeurs, avec état TalkBack et cibles tactiles.
- `SentinelStatusChip`, `SentinelGlassCard`, `SentinelSectionHeader` et les tokens `SentinelD1` :
  surface, bordure, statut et hiérarchie partagés.
- Glissement horizontal : rotation manuelle de la scène ; `FULL`, `REDUCED` et `STATIC` restent
  pilotés par la politique de mouvement Android.

## Limites de qualification

- Aucun émulateur, appareil Android ou Galaxy S24+ n’est disponible dans cet environnement : aucune
  capture après, mesure GPU, installation, TalkBack réel ou contrôle de non-chevauchement ne peut
  être déclaré.
- Les valeurs illustratives des maquettes — volumes de menaces, IP, débit, pays, scores ou
  identité — ne sont pas copiées dans les états runtime.
- Le verdict de phase reste donc `D1_BUILT_NOT_VISUALLY_VALIDATED` jusqu’à l’exécution sur Android.
