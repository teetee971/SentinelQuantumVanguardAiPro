# Roadmap — Sentinel Quantum Vanguard AI Pro

**Dernière mise à jour : 24 septembre 2026**

Cette feuille de route distingue strictement ce qui existe dans le dépôt de ce qui reste à démontrer sur appareil physique. Une capacité logicielle prête ne constitue jamais, à elle seule, une preuve de fonctionnement opérateur/appareil.

## Phone Core — état consolidé

### Implémenté et validé au niveau logiciel

- Application Android native sous `native-android-app/`.
- Parcours d’activation séparant explicitement prérequis logiciels et validation physique.
- Composeur Sentinel et surface InCall pour l’application téléphone sélectionnée par l’utilisateur.
- Filtrage local via `CallScreeningService` et fiche Caller ID locale après la réponse obligatoire à Android.
- Accès local aux contacts avec `READ_CONTACTS`, et historique système avec rôle téléphone + `READ_CALL_LOG`.
- Client SMS par défaut : réception `SMS_DELIVER`, envoi via `SmsManager`, conversations locales et suivi multiparties SENT/DELIVERED.
- Sélection multi-SIM explicite pour les appels et SMS lorsque plusieurs lignes sont disponibles ; aucun choix arbitraire de ligne n’est présenté comme validé.
- MMS/WAP_PUSH borné : rôle SMS obligatoire, décodage fail-closed, aperçu sécurisé, quarantaine locale et téléchargement opérateur associé à une souscription.
- Notifications appels/SMS soumises uniquement lorsque permissions et canaux Android l’autorisent.
- Scanner Wi-Fi local respectant les permissions, l’état de localisation et les limitations/throttling Android ; seuls les résultats réellement frais peuvent satisfaire la preuve physique de scan.
- Validation physique locale structurée en 14 preuves, bornée à l’installation courante. Les preuves incluent appels entrants/sortants réellement actifs, filtrage observé, providers contacts/historique, SMS entrant, SMS envoyé/livré, MMS sécurisé, scan Wi-Fi frais, notifications et surfaces Caller ID/InCall réellement affichées.
- CI Android : tests unitaires, lint, build APK, contrôle package/alignment/signature et installation/lancement sur émulateur Android 10.

### Non démontré — bloque le statut « Phone Core 100 % fonctionnel »

- Appels opérateur entrants et sortants sur appareil physique.
- Comportement réel du Dialer/InCall/Caller ID sur les variantes constructeur/opérateur ciblées.
- Contacts et historique sur appareil utilisateur avec rôles/permissions réellement accordés.
- SMS entrants/sortants et agrégation SENT/DELIVERED sur réseau mobile réel.
- MMS sur réseau opérateur réel, y compris téléchargement et aperçu sécurisé.
- Multi-SIM réel avec deux lignes actives et changements de disponibilité.
- Notifications réelles selon réglages utilisateur/constructeur.
- Scan Wi-Fi frais sur appareil physique dans les limites de throttling/localisation Android.

**Règle de sortie Phone Core :** ne jamais déclarer « 100 % fonctionnel » avant réussite documentée de ces tests physiques. Une APK installable ou des prérequis logiciels à 100 % ne remplacent pas cette preuve.

## Sécurité et limites Android

- Les rôles Téléphone, Filtrage d’appels et SMS restent des choix explicites de l’utilisateur ; Sentinel ne les contourne pas.
- Android peut limiter ou refuser certaines opérations selon version, constructeur, opérateur, permissions, rôle, SIM, état réseau, localisation et politiques système.
- Le scanner Wi-Fi n’implique aucune interception de trafic, cassage de clé, déchiffrement ou surveillance d’un réseau tiers.
- Aucun résultat de Caller ID ne doit inventer une identité, une société, une réputation ou l’opérateur actuel d’un numéro porté.
- Les données de validation physique restent minimisées : elles ne doivent pas conserver numéro, contact, corps de message, URL ou identifiant de souscription comme preuve.

## Après Phone Core

Les autres programmes Sentinel (VPN, réseau défensif, veille, Email Security, Digital Exposure, Social Intelligence, Investigations et autres modules) restent séparés de ce jalon. CTEM ne doit pas être engagé comme phase de finalisation tant que Phone Core n’a pas franchi son protocole physique.

Sentinel reste strictement séparé de **A KI PRI SA YÉ**.
