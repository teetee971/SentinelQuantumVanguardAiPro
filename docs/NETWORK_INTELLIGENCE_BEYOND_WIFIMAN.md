# Sentinel Network Intelligence — au-delà du diagnostic WiFiman

Statut : **spécification et plan de livraison**, pas fonctionnalités implémentées. Ne pas annoncer READY sans preuves. Branche indépendante de PR #1718 (D.1) et #1719 (Bluetooth).

## Objectif produit
Créer un Centre réseau Android offline-first capable d'expliquer **ce qui est observé, ce qui est seulement inféré et ce qui reste inconnu**. Le produit vise le diagnostic Wi-Fi/LAN/Bluetooth/Wear OS/VPN et une détection défensive fondée sur la provenance, sans collecter inutilement les données personnelles.

## Contrats techniques obligatoires
- `NetworkObservation`: id aléatoire local, source (ANDROID_WIFI/ANDROID_BLE/ANDROID_BONDED/LOCAL_GATEWAY/DNS/VPN/USER_REPORT), observedAt, expiresAt, permissionsSnapshot, signalConfidence, fields autorisés, provenance/version.
- `DeviceIdentity`: identifiant local pseudonymisé et rotatif; ne jamais considérer MAC, IP, nom Bluetooth ou constructeur comme identité forte. Séparer entité observée et hypothèse de rapprochement.
- `RiskFinding`: règles/version, evidenceIds, severity, confidence, explanation, remediation, expiresAt; aucun verdict « piraté » sur simple appareil inconnu.
- `CapabilityState`: READY / LIMITED / LOCKED / UNSUPPORTED / NOT_TESTED avec reason et evidenceTimestamp; READY interdit sans sonde exécutée.
- Journal local chiffré quand possible, durée configurable et suppression utilisateur, aucune transmission communautaire par défaut.
- Distinguer `ANDROID_BONDED`, `BLE_ADVERTISEMENT` et `WEAR_AUTHENTICATED`. Un appairage Bluetooth n'est jamais une preuve de session Wear OS.
- Utiliser un moteur d'événements idempotent avec limite de débit et expiration TTL. Empêcher l'inflation du risque par scans répétés.

## Phases / critères de réception

### P0 — Vérité produit et stabilité (bloquant)
- Fermer PR #1719 uniquement après : compilation Kotlin, tests unitaires Android 37, scan permissions accordées/refusées, Bluetooth désactivé et appareil associé réel sur S24+, et contrôle de doublons et données périmées.
- Qualifier PR #1718 sur S24+ (tailles de texte >= 12sp, débordements, rotations, 1er/2e lancement, lecteurs d'écran, thème clair/sombre si applicable).
- CI GitHub : caractériser les jobs qui échouent avec 0 étape/aucun runner, sans masquer leurs statuts.
- Preuves associées au SHA exact, APK signé compatible vérifié, et installation sans contournement de Play Protect.

### P1 — Cartographie locale transparente
- Inventaire opt-in de clients accessibles via les interfaces Android autorisées, découvertes mDNS/NSD et réponses réseau **non intrusives**. Pas de promesse d'inventaire complet du LAN : isolation AP, VPN et permissions peuvent rendre des hôtes invisibles.
- Vue topology « téléphone — passerelle — services découverts » : chaque arête est observée, probable ou inconnue.
- Conserver temporairement les changements de réseau, DNS, IP de passerelle, SSID/BSSID uniquement quand Android les fournit légalement.
- Constructeur via bases OUI téléchargées/licenciées et mises à jour : statut « estimation constructeur » ; MAC privées/aléatoires → inconnu.
- Critères : tests hors-ligne, réseau invité, hotspot, VPN, DNS privé, et permissions refusées.

### P2 — Détection défensive et mesure réseau
- Moniteur opt-in : changement de DNS/passerelle, échecs TLS observables par l'app, anomalies locales explicables. Alertes à double confirmation, anti-faux positifs.
- Latence / gigue / pertes vers cibles configurables, volume et fréquence limités. Débit : serveur de test ou endpoint explicite; ne pas prétendre mesurer la connexion entière à partir d'une estimation.
- Historique agrégé local, effacement des données, export volontaire sans secret.
- Critères : tests en mode avion, économie d'énergie, DNS privé/VPN, et batterie surveillée.

### P3 — Wearable sécurisé
- Découverte Galaxy Watch6 (SM-R930) **indicative** via appareils associés, puis handshake explicite par app compagnon Wear OS quand nécessaire.
- Sessions authentifiées (établissement de clés, consentement, rotation, anti-rejeu, séquences, expiration), indépendantes de l'appairage Android.
- États : NOT_DISCOVERED / BONDED_ONLY / COMPANION_REQUIRED / AUTHENTICATING / CONNECTED_AUTHENTICATED / STALE / REVOKED.
- Tests physiques de reconnexion et révocation, hors-ligne, app en arrière-plan; ne pas promettre accès aux données privées de la montre sans API/consentement.

### P4 — Communauté antifraude à respect de la vie privée
- Participation opt-in séparée, agrégation côté serveur, minimisation, seuils anti-sybil et budget de confidentialité.
- Ne jamais envoyer brute force l'inventaire LAN, MAC, IP privées, carnet de contacts, SSID ou historique d'appels.
- Modération, transparence des verdicts, opt-out et effacement.

## Écrans proposés
- **Vue Réseau** : carte avec statut et source de chaque observation.
- **Fiche appareil** : dernière observation, capacité, hypothèses, provenance, preuve visible, bouton oublier.
- **Observatoire** : chronologie des changements DNS/passerelle et anomalies corroborées.
- **Diagnostics** : latence, gigue, pertes, débit mesuré à la demande.
- **Privacy Center** : permissions, rétention, partage opt-in, exports et effacement.
- **System Doctor** : READY/LIMITED/LOCKED/UNSUPPORTED/NOT_TESTED par sonde, liens vers réglages.

## Architecture indicative (non présumer du code existant)
`AndroidSources -> PermissionGate -> ObservationNormalizer -> LocalEvidenceStore -> Correlator -> RiskExplainer -> UI`.
Des adaptateurs compatibles avec l'architecture actuelle doivent être identifiés par audit avant ajout de classes. Éviter un deuxième moteur de réputation en silo. Les sondes actives nécessitent accord explicite et bornes de consommation.

## Définition de « terminé »
Une fonctionnalité n'est terminée que si : code branché à l'UI, permissions conformes, tests unitaires et instrumentation passés, performance mesurée, écran réel testé, documentation et capture avec SHA exact. « Code présent » ne signifie pas « 100 % fonctionnel ».
