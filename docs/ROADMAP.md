# Roadmap — Sentinel Quantum Vanguard AI Pro

**Dernière mise à jour : 1 octobre 2026**

Cette feuille de route distingue strictement ce qui existe dans le dépôt de ce qui reste à démontrer sur appareil physique. Une capacité logicielle prête ne constitue jamais, à elle seule, une preuve de fonctionnement opérateur/appareil.

## Phone Core — état consolidé

### Implémentation présente — validation sur le commit exact requise

- Application Android native sous `native-android-app/`.
- Parcours d’activation séparant explicitement prérequis logiciels et validation physique.
- Composeur Sentinel et surface InCall pour l’application téléphone sélectionnée par l’utilisateur.
- Filtrage local via `CallScreeningService` et fiche Caller ID locale après la réponse obligatoire à Android.
- Accès local aux contacts avec `READ_CONTACTS`, et historique système avec rôle téléphone + `READ_CALL_LOG`.
- Client SMS par défaut : réception `SMS_DELIVER`, envoi via `SmsManager`, conversations locales et suivi multiparties SENT/DELIVERED.
- Sélection multi-SIM explicite pour les appels et SMS lorsque plusieurs lignes sont disponibles ; aucun choix arbitraire de ligne n’est présenté comme validé.
- MMS entrant/WAP_PUSH borné : rôle SMS obligatoire, décodage fail-closed, aperçu sécurisé, quarantaine locale et téléchargement opérateur associé à une souscription. L’envoi MMS sortant reste bloqué tant que le transport #1455 n’est pas implémenté et validé.
- Notifications appels/SMS soumises uniquement lorsque permissions et canaux Android l’autorisent.
- Scanner Wi-Fi local respectant les permissions, l’état de localisation et les limitations/throttling Android. Dans le schéma Phone Core v5, ce signal reste un diagnostic réseau séparé et ne compte pas dans le certificat Phone Core.
- Validation physique Phone Core v5 structurée en 14 preuves, bornée à l’installation courante. Les preuves incluent appels entrants/sortants réellement actifs, filtrage observé, providers contacts/historique, SMS entrant, SMS envoyé/livré, MMS entrant sécurisé, MMS sortant confirmé, notifications et surfaces Caller ID/InCall réellement affichées.
- CI Android : tests unitaires, lint, build APK, contrôle package/alignment/signature et installation/lancement sur émulateur Android 10.

### Non démontré — bloque le statut « Phone Core 100 % fonctionnel »

- Appels opérateur entrants et sortants sur appareil physique.
- Comportement réel du Dialer/InCall/Caller ID sur les variantes constructeur/opérateur ciblées.
- Contacts et historique sur appareil utilisateur avec rôles/permissions réellement accordés.
- SMS entrants/sortants et agrégation SENT/DELIVERED sur réseau mobile réel.
- MMS entrant sur réseau opérateur réel, y compris téléchargement et aperçu sécurisé.
- Envoi MMS sortant réel : transport non encore implémenté ; issue #1455, preuve `MMS_SENT_OK` obligatoire.
- Multi-SIM réel avec deux lignes actives et changements de disponibilité ; ce scénario de compatibilité ne remplace aucune des 14 preuves du certificat v4.
- Notifications réelles selon réglages utilisateur/constructeur.
**Hors certificat Phone Core v5 — diagnostic réseau séparé :** un scan Wi-Fi réellement frais reste à tester sur appareil physique dans les limites de throttling/localisation Android. Son succès ou son échec ne doit pas modifier le compteur 14/14 Phone Core.

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

## Plan de finalisation — jalons et preuves de sortie

Référence de consolidation : PR #1454, basée sur `main` au commit `dd4d00e2c90797c5d01f1b7763014cdc6f62d784`. Le commit exact à valider reste le dernier SHA de la PR ; aucune fusion ni certification ne doit être prononcée avant réussite de tous les contrôles requis sur ce SHA.

| Ordre | Jalon | État constaté | Condition de clôture |
| --- | --- | --- | --- |
| 1 | Clôture logicielle Phone Core v5 | Consolidation en PR #1454 ; contrôles requis à obtenir sur son dernier SHA | Exiger tests unitaires, lint, APK/AAB, Android 16, CodeQL et gates de gouvernance tous réussis sur la branche à jour de `main`, puis intégrer sans bypass. |
| 2 | Validation physique Phone Core | Non démontrée | Documenter les 14 preuves sur l’installation courante. Le critère `outgoing_mms_sent` reste impossible tant que #1455 n’est pas implémentée ; tester également double-SIM, retrait des rôles/permissions et réversibilité. |
| 3 | Release Android signée | Pipeline présent ; publication non attestée | Appliquer [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md) : APK/AAB signés, signatures et SHA-256 vérifiés, SBOM et preuves liées au commit ; décider la stratégie de signature Play/canal direct et vérifier les exigences de distribution. |
| 4 | Diagnostic Wi-Fi indépendant | Scanner présent ; terrain non attesté | Tester fraîcheur, cache, permissions, localisation et throttling sur appareil ; ne jamais modifier le certificat Phone Core 14/14. |
| 5 | VPN défensif | Client présent ; passerelle non attestée | Provisionner une passerelle réelle et vérifier tunnel, DNS, IPv4/IPv6, MTU, coupures et reprise avant de déclarer le service disponible. |
| 6 | Veille et modules ultérieurs | Dépendances et programmes séparés | Pour la veille, configurer et vérifier la signature Ed25519 de production ; pour chaque autre module, définir périmètre, sources autorisées, tests et preuve de sortie avant réalisation. CTEM reste après validation physique Phone Core. |

### Lecture des contrôles examinés

La PR #1454 est le candidat de consolidation actuel. Son état ne devient acceptable que lorsque tous les contrôles obligatoires sont réussis sur son dernier SHA et que la branche n’est pas en retard sur `main`. Un contrôle réussi sur un ancien SHA ne vaut pas preuve pour la tête courante.

### Protocole du prochain jalon

Le [protocole physique Phone Core v5](PHONE_CORE_PHYSICAL_VALIDATION.md) décrit les 13 critères du code, les scénarios, le relevé de session et les essais complémentaires. Tous ses résultats terrain sont initialement non exécutés.

### Dossier de preuve à conserver par jalon

- Commit exact, date, environnement/appareil et scénario exécuté.
- Résultat observé, liens vers les exécutions et artefacts pertinents.
- Écarts, corrections et résultat de la nouvelle vérification.
- Décision de clôture après examen des preuves ; aucun jalon terrain n’est fermé par une simple modification documentaire.

La feuille de route est consolidée ; la validation opérationnelle reste soumise aux conditions ci-dessus. Aucune date de disponibilité n’est annoncée sans preuve.
