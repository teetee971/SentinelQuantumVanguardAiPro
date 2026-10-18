# Sentinel D.1 Phase 4 — matrice de fidélité

Cette matrice est la trace de travail de la Phase 4. Les maquettes sont des références de
composition uniquement ; les données affichées par Android restent soumises aux preuves réelles.
Une capture Android ne sera marquée disponible qu'après exécution sur un appareil ou un émulateur.

| # | Référence originale | Surface Android | Scène cible Phase 4 | Source de vérité | Écart Phase 3 traité | Capture réelle | Statut |
|---:|---|---|---|---|---|---|---|
| 01 | `1000415621.png` | `AppPermissionAnalyzerScreen` | cube volumétrique + cartes d'applications | permissions/heuristiques locales | cube filaire, cartes trop génériques, métriques étroites | NON | IN_PROGRESS |
| 02 | `1000415622.png` | `ThreatMapScreen` | globe mondial avec routes et points prouvés | flux OSINT/collectif réellement disponible | globe pauvre, points non contextualisés | NON | IN_PROGRESS |
| 03 | `1000415623.png` | `SecurityAuditScreen` | smartphone éclaté + anneaux de contrôle | contrôles réellement exécutés | couches plates, hiérarchie faible | NON | IN_PROGRESS |
| 04 | `1000415624.png` | `QuarantineScreen` | cylindre de confinement rempli ou vide selon preuve | mécanismes de quarantaine observés | cylindre filaire, état trop affirmatif | NON | IN_PROGRESS |
| 05 | `1000415625.png` | `PhoneSecurityScreen` | sphère/bouclier volumétrique | rôles, permissions, protections observées | bouclier plat, statut ambigu | NON | IN_PROGRESS |
| 06 | `1000415626.png` | `ThreatDetailScreen` | noyau critique rouge + dossier de preuve | réputation/analyse locale/OSINT | menace générique, manque de profondeur | NON | IN_PROGRESS |
| 07 | `1000415627.png` | `SystemDoctorScreen` | téléphone multicouche progressif | diagnostics Android disponibles | smartphone trop abstrait, statistiques fragiles | NON | IN_PROGRESS |
| 08 | `1000415628.png` | `HomeScreen` | command center sphérique + orbites | `HomeSecurityPresentation` | globe filaire, surfaces vides | NON | IN_PROGRESS |
| 09 | `1000415629.png` | chrome et navigation | cohérence des matériaux et densités | routes/actions existantes | variations entre modules | NON | IN_PROGRESS |
| 10 | `1000415630.png` | `VpnScreen` | globe/tunnel et passerelles | `SentinelVpnRuntimeStateRegistry` | globe peu volumétrique, preuves non hiérarchisées | NON | IN_PROGRESS |
| 11 | `1000415631.png` | `SentinelDialerActivity` contacts | portrait/orbite et provenance | `LocalContactLookup`/permissions | hero répétitif, photo réelle non exploitée | NON | IN_PROGRESS |
| 12 | `1000415632.png` | `SmsComposeActivity` / scanner | identité + conversation + alertes | SMS/MMS natif et analyse anti-phishing | hero trop haut, conversations repoussées | NON | IN_PROGRESS |
| 13 | `1000415633.png` | `CallerIdActivity` | identité inconnue volumétrique | réputation/indicatif/provenance | silhouette plate, score illustratif risqué | NON | IN_PROGRESS |

## Éléments transversaux

- `SentinelHolographicScene` : moteur Compose Canvas borné, spécialisé par `SentinelSceneKind`.
- `SentinelReferenceMetricStrip` : passage à une grille adaptative deux colonnes pour éviter les
  mots fractionnés et les cartes trop étroites.
- `NetworkSurveillanceScreen` : topologie distincte ; observé/probable/inconnu reste fourni par
  le scanner et ne devient pas automatiquement « connecté ».
- Phone Core : clavier et bouton d'appel restent prioritaires sur le hero décoratif.
- Activation : les rôles, permissions et états persistants gardent leur cycle réel.

## Limites de preuve

Au démarrage de Phase 4, aucun émulateur ni appareil Android n'est disponible dans l'environnement.
Les captures avant/après, TalkBack réel, GPU/FPS, taille de police système et installation S24+
restent donc non effectués jusqu'à obtention d'un runtime.
