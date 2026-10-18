# Sentinel D.1 — correspondance maquette → Android natif

Cette matrice distingue la cible visuelle officielle de la source de vérité fonctionnelle.
Les routes existantes sont conservées ; une route corrective n’est ajoutée que lorsqu’aucune
surface native ne permet de représenter fidèlement la maquette.

| Référence | Surface Android | Source runtime à préserver | Composition 3D phase 3 | Écart restant à qualifier |
|---|---|---|---|---|
| `1000415621.png` Applications à risque | `AppPermissionAnalyzerScreen.kt` / `Screen.AppPermissionAnalyzer` | `AppPermissionAnalyzer`, permissions déclarées et heuristiques | Cube holographique d’applications + anneaux de permission + métriques compactes | Capture S24+ et vérification des longueurs de noms à réaliser |
| `1000415622.png` Carte mondiale des menaces | `ThreatMapScreen.kt` / `Screen.ThreatMap` | événements OSINT/collectifs réellement disponibles ; aucun événement = état inconnu | Globe projeté avec points et arcs uniquement pour les événements prouvés | Flux géolocalisé réel à brancher avant d’afficher des points |
| `1000415623.png` Rapport d’analyse | `SecurityAuditScreen.kt` / `Screen.SecurityAudit` | `SecurityAudit`, permissions et contrôles exécutés | Smartphone multicouche éclaté + métriques de contrôles | Capture réelle et contrôle des états de chargement à réaliser |
| `1000415624.png` Quarantaine sécurisée | `QuarantineScreen.kt` / `Screen.Quarantine` | quarantaine MMS locale et autres mécanismes explicitement observés | Chambre de confinement cylindrique ; actions désactivées sans confinement réel | Aucun mécanisme de confinement global revendiqué |
| `1000415625.png` Protection temps réel | `PhoneSecurityScreen.kt` / `Screen.PhoneSecurity` | rôles Phone Core, permissions, `ProtectionOverviewPresentation` | Sphère/bouclier temps réel avec modules pilotés par les états | « Actif » reste réservé aux preuves ; capture S24+ manquante |
| `1000415626.png` Détail menace | `ThreatDetailScreen.kt` / `Screen.ThreatDetail` | réputation, SMS/phishing, OSINT ou analyse locale selon la preuve | Noyau rouge critique + dossier de preuve | Le dossier réel reste à fournir pour activer le rouge critique |
| `1000415627.png` Analyse appareil | `SystemDoctorScreen.kt` / `Screen.SystemDoctor` et diagnostic Phone Core | diagnostics Android, permissions, batterie, stockage, services | Smartphone multicouche progressif + métriques système | Batterie et rôles restent inconnus sans preuve Android exposée |
| `1000415628.png` Command Center | `HomeScreen.kt` / `Screen.Home` | `HomeSecurityPresentation`, Phone Core, VPN, réseau, communauté | Sphère centrale, orbites contextuelles, bande métrique et actions rapides | Activité système riche de la maquette non inventée ; capture à qualifier |
| `1000415629.png` Planche multi-écrans | `SentinelChrome.kt`, navigation et surfaces ci-dessus | routes et actions existantes | Tokens et scènes spécialisées communs, sans uniformiser tous les modules | Contrôle de cohérence à appliquer à l’ensemble |
| `1000415630.png` VPN privé | `VpnScreen.kt` / `Screen.Vpn` | `SentinelVpnRuntimeStateRegistry` et contrôleur VPN | Globe/tunnel avec route logique, métriques et preuves DNS/IP | Pays, débit, IP et handshake restent inconnus sans tunnel établi |
| `1000415631.png` Contacts Sentinel | `SentinelDialerActivity.kt`, onglet Contacts | `LocalContactLookup`, provenance et permissions | Portrait/profil holographique, métriques et sections de provenance | Photo/profils enrichis restent absents si Android ne les expose pas |
| `1000415632.png` SMS sécurisés | `SmsComposeActivity.kt`, `SmsScannerScreen.kt` | transport SMS/MMS natif, `SmsThreatPresentation`, pipeline anti-phishing | Conversation glass + identité + panneau de risque contextuel | Captures réelles nécessaires pour valider clavier, IME et défilement |
| `1000415633.png` Fiche numéro | `CallerIdActivity.kt`, `PhoneSecurityScreen.kt` | réputation, annuaire, indicatif, communauté et fraîcheur | Identité holographique + métriques + provenance | Identité ne devient vérifiée qu’avec une preuve ; capture réelle manquante |

## Surfaces conservées hors des 13 références

Dialer, appel entrant, appel actif, historique, activation Phone Core, System Doctor, Network
Center local, Studio voix, Assistant IA, Défense collective et Paramètres restent harmonisés avec
le même système de tokens et de chrome, sans placer un visuel 3D au-dessus d’un clavier ou d’une
commande critique.

La qualification écran par écran est suivie dans [`validation-matrix.md`](validation-matrix.md).
Les statuts de capture sont volontairement `NON` tant qu’un runtime Android n’est pas disponible.
