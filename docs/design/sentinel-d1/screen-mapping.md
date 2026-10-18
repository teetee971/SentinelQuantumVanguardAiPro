# Sentinel D.1 — correspondance maquette → Android natif

Cette matrice distingue la cible visuelle officielle de la source de vérité fonctionnelle.
Les routes existantes sont conservées ; une route corrective n’est ajoutée que lorsqu’aucune
surface native ne permet de représenter fidèlement la maquette.

| Référence | Surface Android | Source runtime à préserver | Composition 3D phase 3 | Écart actuel constaté |
|---|---|---|---|---|
| `1000415621.png` Applications à risque | `AppPermissionAnalyzerScreen.kt` / `Screen.AppPermissionAnalyzer` | `AppPermissionAnalyzer`, permissions déclarées et heuristiques | Cube holographique d’applications + anneaux de permission | Hero générique et cartes répétitives ; aucune confusion heuristique/malware ne doit être introduite |
| `1000415622.png` Carte mondiale des menaces | nouvelle surface `ThreatMapScreen.kt` / `Screen.ThreatMap` | événements OSINT/collectifs réellement disponibles ; aucun événement = état inconnu | Globe projeté avec arcs uniquement pour les événements prouvés | Aucun écran mondial dédié dans la branche D.1 actuelle |
| `1000415623.png` Rapport d’analyse | `SecurityAuditScreen.kt` / `Screen.SecurityAudit` | `SecurityAudit`, permissions et contrôles exécutés | Smartphone multicouche éclaté + timeline de contrôles | Audit présenté comme carte générique ; rapport détaillé à structurer |
| `1000415624.png` Quarantaine sécurisée | nouvelle surface `QuarantineScreen.kt` / `Screen.Quarantine` | quarantaine MMS locale et autres mécanismes explicitement observés | Chambre de confinement cylindrique ; actions désactivées sans confinement réel | Aucune vue de quarantaine produit dédiée |
| `1000415625.png` Protection temps réel | `PhoneSecurityScreen.kt` / `Screen.PhoneSecurity` | rôles Phone Core, permissions, `ProtectionOverviewPresentation` | Sphère/bouclier temps réel avec modules pilotés par les états | Hero trop abstrait ; « actif » doit rester réservé aux preuves |
| `1000415626.png` Détail menace | nouvelle surface `ThreatDetailScreen.kt` / `Screen.ThreatDetail` | réputation, SMS/phishing, OSINT ou analyse locale selon la preuve | Noyau rouge critique + dossier de preuve | Aucun détail menace unifié |
| `1000415627.png` Analyse appareil | `SystemDoctorScreen.kt` / `Screen.SystemDoctor` et diagnostic Phone Core | diagnostics Android, permissions, batterie, stockage, services | Smartphone multicouche progressif + étapes d’analyse | Cartes techniques sans composition de scan dédiée |
| `1000415628.png` Command Center | `HomeScreen.kt` / `Screen.Home` | `HomeSecurityPresentation`, Phone Core, VPN, réseau, communauté | Sphère centrale, orbites contextuelles, alertes et actions rapides | Centre existant encore trop linéaire et textuel |
| `1000415629.png` Planche multi-écrans | `SentinelChrome.kt`, navigation et surfaces ci-dessus | routes et actions existantes | Tokens et scènes spécialisées communs, sans uniformiser tous les modules | Contrôle de cohérence à appliquer à l’ensemble |
| `1000415630.png` VPN privé | `VpnScreen.kt` / `Screen.Vpn` | `SentinelVpnRuntimeStateRegistry` et contrôleur VPN | Globe/tunnel avec route logique et preuves DNS/IP | Maquette contient des valeurs fictives ; elles resteront inconnues sans observation |
| `1000415631.png` Contacts Sentinel | `SentinelDialerActivity.kt`, onglet Contacts | `LocalContactLookup`, provenance et permissions | Portrait/profil holographique et sections de provenance | Fiche enrichie à rapprocher de la hiérarchie officielle |
| `1000415632.png` SMS sécurisés | `SmsComposeActivity.kt`, `SmsScannerScreen.kt` | transport SMS/MMS natif, `SmsThreatPresentation`, pipeline anti-phishing | Conversation glass + panneau de risque contextuel | Composition et alertes à rendre moins empilées |
| `1000415633.png` Fiche numéro | `CallerIdActivity.kt`, `PhoneSecurityScreen.kt` | réputation, annuaire, indicatif, communauté et fraîcheur | Identité holographique + graphe provenance | Hero actuel ne reprend pas la fiche de preuve complète |

## Surfaces conservées hors des 13 références

Dialer, appel entrant, appel actif, historique, activation Phone Core, System Doctor, Network
Center local, Studio voix, Assistant IA, Défense collective et Paramètres restent harmonisés avec
le même système de tokens et de chrome, sans placer un visuel 3D au-dessus d’un clavier ou d’une
commande critique.
