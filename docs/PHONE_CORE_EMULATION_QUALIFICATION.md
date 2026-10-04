# Qualification Android par émulation — application Sentinel / Phone Core

**Statut :** gate CI principal pour la validation développeur  
**Validation manuelle utilisateur :** non requise  
**Périmètre :** Android 7 / API 24 (minSdk), Android 10 / API 29 et Android 16 / API 36

Cette qualification automatise tout ce qui peut être reproduit de façon déterministe avec Android Emulator. Elle est distincte de la validation modem/opérateur réelle et ne doit jamais être présentée comme une preuve physique universelle.

## Gate automatisé

Le workflow `.github/workflows/android-emulation-qualification.yml` doit réussir sur API 24, API 29 et API 36.

API 24 vérifie le plancher réellement supporté par l'APK : installation, cold launch, instrumentation et rendu de toutes les destinations statiques de l'application. Les parcours Phone Core basés sur les rôles Android modernes sont volontairement limités aux API 29 et 36 ; leur absence sur API 24 n'est jamais transformée en faux support.

La qualification exécute notamment :

- tests unitaires Android complets ;
- lint Android ;
- compilation APK debug et APK de tests ;
- `connectedDebugAndroidTest` sur API 24, 29 et 36 ;
- rendu instrumenté de chaque destination statique du `NavGraph` : accueil, recherche, OSINT, audits, journaux, Phone Security, communications, blocage et listes, historique, sécurité email/SMS, permissions, réseau, exposition numérique, défense collective, Smart Home, VPN, conformité, à propos et paramètres ;
- canonicalisation régionale FR / GP / MQ / GF / RE / YT / PM ;
- politiques multi-SIM et sélection d'abonnement ;
- retrait/rétablissement réel des rôles SMS, Téléphone et Filtrage dans le framework Android de l'émulateur sur les versions qui les supportent ;
- retrait de permissions critiques et vérification fail-closed ;
- installation, premier lancement, interruption et reprise du parcours Phone Core ;
- appel entrant synthétique via Telecom ;
- appel sortant synthétique depuis le composeur Sentinel ;
- réception SMS synthétique et réponse inline ;
- contrôle crash/ANR ;
- captures, logcat, état Telecom, rôles et état package comme artefacts CI.

Les rapports indiquent explicitement `physical_modem_claim: false` et `commercial_release_claim: false`. Les lignes API 29/36 utilisant la téléphonie simulée portent `synthetic_modem: true`. Une émulation verte ne peut donc pas être transformée en revendication de validation opérateur réelle.

## Multi-SIM

L'absence de téléphone double-SIM chez le propriétaire du dépôt ne bloque pas la qualification développeur.

La logique multi-SIM est validée automatiquement par les tests de politique et les tests instrumentés : ligne explicitement sélectionnée, ligne inactive, absence de ligne, défaut Android autorisé uniquement dans les flux prévus, régions identiques, régions divergentes et absence de région fiable. Aucune branche ne doit choisir arbitrairement une SIM ou inventer une région.

La compatibilité modem/OEM double-SIM réelle reste un contrôle de release externe. Elle est attribuée à une QA ou un device lab et non à une validation manuelle du propriétaire du dépôt.

## Ce que l'émulateur ne peut pas certifier

Trois preuves restent hors du périmètre d'émulation :

1. callbacks de livraison SMS et succès MMS fournis par un opérateur mobile réel ;
2. latence pré-`respondToCall` réellement mesurée sur Samsung Galaxy S24+ / Android 16 ;
3. comportement d'un modem/OEM double-SIM physique.

Ces preuves sont des gates de **release commerciale**, pas des prérequis pour continuer le développement ni pour qualifier une PR par émulation. Elles doivent être obtenues plus tard par QA externe/device lab ou par une campagne de release dédiée.

## Règle de vérité

Une PR peut être déclarée **qualification Android par émulation verte** uniquement lorsque les trois lignes API 24/29/36 réussissent et que les artefacts CI sont présents.

Elle ne peut être déclarée **Phone Core 100 % fonctionnel en production commerciale** tant que les validations externes restantes et la signature/release publique ne sont pas démontrées sur la révision exacte.
