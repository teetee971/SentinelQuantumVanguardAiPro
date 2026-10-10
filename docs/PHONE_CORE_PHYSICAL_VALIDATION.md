# Protocole de validation physique — Phone Core v5

**Révision :** 7 octobre 2026  
**Statut :** protocole prêt à exécuter ; aucun résultat terrain renseigné  
**Référence logicielle :** utiliser le SHA exact et l’artefact exact qui auront passé les gates avant l’essai terrain.

Les 14 identifiants de critères sont partagés avec le certificat technique local produit par [PhoneCorePhysicalValidation.kt](../native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCorePhysicalValidation.kt). Ce certificat local peut être complété par des observations d’émulateur et ne constitue donc jamais, à lui seul, une validation d’appareil physique. Le stade produit `physically_validated` exige séparément une observation signée `PHYSICAL_DEVICE`, liée au SHA et à l’artefact exacts, attestant les 14 critères sur matériel réel. Les essais de compatibilité et le Wi-Fi restent séparés de ce compteur.

## Préparation

1. Utiliser un appareil physique et une ligne mobile de test, avec un second appareil/une seconde ligne pour appels et messages. Ne pas utiliser de numéro d’urgence pour ces essais.
2. Installer l’APK du commit à vérifier après examen de ses contrôles CI ; relever son SHA-256 et son certificat de signature. Un APK debug de validation ne devient pas un livrable public signé.
3. Ouvrir le centre Phone Core. Accorder explicitement les rôles Téléphone, Filtrage d’appels et SMS requis, puis les permissions nécessaires. Vérifier canaux de notification et réglages constructeur. Sur API 24–28, le filtrage passe par la sélection explicite de Sentinel comme application Téléphone par défaut ; à partir d’API 29, Android expose le rôle Call Screening dédié.
4. Utiliser le périmètre de certification de l’installation courante. Ne pas réinjecter d’événements, modifier le stockage ou reprendre les preuves d’une ancienne installation pour remplir le compteur.
5. Sélectionner explicitement la ligne utilisée lorsqu’il existe plusieurs SIM.
6. Conserver séparément le relevé terrain et le certificat technique local. Un compteur local 14/14, même obtenu sur le même APK, ne doit pas être converti automatiquement en `physically_validated`.

## Relevé de session

| Champ | Valeur à renseigner |
| --- | --- |
| Date UTC et responsable de l’essai | À renseigner |
| Commit exact, version et canal de l’APK | À renseigner |
| SHA-256 de l’APK et empreinte du certificat | À renseigner |
| Modèle, constructeur, version Android et niveau API | À renseigner |
| Configuration réseau, opérateur et simple/double-SIM | À renseigner sans numéro ni identifiant de souscription |
| Rôles, permissions et canaux effectivement accordés | À renseigner |
| Installation/périmètre de certification courant | À vérifier |
| Liens vers workflows et artefacts du commit testé | À renseigner |

## Les 14 preuves physiques canoniques

Toutes les lignes commencent à **non exécuté**. Après chaque scénario, consulter le diagnostic Phone Core et conserver uniquement une observation minimisée. Une action lancée sans résultat ne suffit pas. Les signaux locaux servent d’indices corrélés ; la preuve physique exige en plus l’observation terrain du scénario sur le matériel déclaré.

| Critère du code | Scénario à exécuter | Résultat exigé | Résultat terrain |
| --- | --- | --- | --- |
| `incoming_call_connected` | Appeler la ligne de test puis décrocher avec Sentinel. | Appel réellement actif ; preuve entrante `INCALL_ACTIVE`. | Non exécuté |
| `outgoing_call_connected` | Depuis le composeur Sentinel, appeler la seconde ligne et faire décrocher. | Appel réellement actif ; preuve sortante `INCALL_ACTIVE`. | Non exécuté |
| `call_screening_observed` | Recevoir un appel avec Sentinel sélectionné pour le filtrage. | Décision réellement observée `CALL_SCREENED:` ; une simple sonnerie ne suffit pas. | Non exécuté |
| `contacts_provider_ready` | Accorder l’accès aux contacts et vérifier le diagnostic/provider sur l’appareil. | Sonde automatique du provider réussie ; permission seule insuffisante. | Non exécuté |
| `call_history_provider_ready` | Avec rôle Téléphone et permission d’historique, vérifier le provider local. | Sonde automatique du provider réussie. | Non exécuté |
| `incoming_sms_received` | Envoyer un SMS à Sentinel depuis la seconde ligne. | Réception réelle et preuve `SMS_RECEIVED`. | Non exécuté |
| `outgoing_sms_submitted` | Envoyer un SMS, puis un SMS multiparties depuis Sentinel. | Toutes les parties envoyées avec succès ; `SMS_ALL_PARTS_SENT`. | Non exécuté |
| `outgoing_sms_delivered` | Observer les retours de livraison du SMS sortant sur le réseau réel. | Toutes les parties livrées avec succès ; `SMS_ALL_PARTS_DELIVERED`. Si l’opérateur ne fournit pas ces retours, conserver le critère manquant. | Non exécuté |
| `incoming_mms_safe_preview` | Recevoir un MMS pris en charge et ouvrir son aperçu sécurisé ; tester le téléchargement opérateur si nécessaire. | `MMS_SAFE_PREVIEW_READY` ou `MMS_DOWNLOAD_SAFE_PREVIEW_READY` ; une quarantaine ne suffit pas. | Non exécuté |
| `outgoing_mms_sent` | Depuis le mode MMS Sentinel, envoyer un MMS réel à la seconde ligne sur la SIM choisie. | Android confirme le succès du transport MMS par callback ; preuve `MMS_SENT_OK`. Une simple soumission du PDU ou un code d’erreur ne suffit pas. | Non exécuté |
| `incoming_call_notification` | Recevoir un appel dans une situation où le canal de notification est autorisé. | Publication acceptée par Android et observation terrain de la notification ; `CALL_NOTIFICATION_POSTED`. | Non exécuté |
| `incoming_sms_notification` | Recevoir un SMS avec notifications autorisées. | Publication acceptée par Android et observation terrain de la notification ; `SMS_NOTIFICATION_POSTED`. | Non exécuté |
| `caller_id_ui_shown` | Recevoir un appel et observer la fiche locale d’identification. | Surface réellement affichée ; `CALLER_ID_UI_SHOWN`. | Non exécuté |
| `in_call_ui_shown` | Observer Sentinel pendant un appel. | Interface InCall réellement affichée ; `INCALL_UI_SHOWN`. | Non exécuté |

Les noms de signaux permettent de rapprocher le diagnostic du code ; ils ne sont jamais des instructions pour fabriquer des événements. Aucun événement synthétique d’émulateur ne peut satisfaire l’attestation `PHYSICAL_DEVICE`.

## Compatibilité et réversibilité — hors compteur

- **Double-SIM :** répéter appels et SMS sur chaque ligne, puis rendre une ligne indisponible. Aucun choix arbitraire ne doit être présenté comme validé.
- **Versions/constructeurs :** répéter sur la matrice réellement ciblée. Le chemin legacy API 24–28 est implémenté via `ACTION_CHANGE_DEFAULT_DIALER` et la CI instrumentée vérifie API 24 sans changer automatiquement le dialer. Cela ne prouve pas le basculement réel du rôle ni le filtrage opérateur sur un appareil physique. À partir d’API 29, le parcours utilise `RoleManager.ROLE_CALL_SCREENING`. Ne revendiquer la compatibilité terrain que pour les versions réellement testées sur appareil.
- **Notifications :** refuser la permission ou désactiver un canal, puis vérifier les états affichés sans déclaration de succès erronée.
- **Retrait des rôles/permissions :** revenir aux applications système et refuser les accès ; observer l’état courant des prérequis et l’absence d’opération non autorisée. Refaire les vérifications après restauration.
- **Échecs transport :** tester envoi/livraison en erreur et MMS non pris en charge ; ils ne doivent pas compléter les critères de succès.
- **Nouvelle installation/version :** vérifier que le périmètre courant n’utilise pas des preuves incompatibles ou antérieures.
- **Wi-Fi séparé :** tester scan frais, cache, permissions, localisation, throttling et absence de résultat frais. Aucun résultat Wi-Fi ne modifie le certificat 14/14.

Pour chaque essai complémentaire, relever scénario, environnement, attendu, observé, résultat et défaut associé. Un scénario non exécuté reste explicitement non validé.

## Clôture

La clôture exige les 14 critères observés sur appareil physique dans la session déclarée, un certificat technique local cohérent, l’examen de la matrice ciblée et la résolution/documentation des écarts. La promotion `physically_validated` requiert ensuite l’observation signée autorisée et liée au SHA/artefact exacts ; elle ne peut pas être déduite du compteur local.

Conserver commit, dates, résultats et références aux preuves. Ne pas exporter numéros, noms de contacts, corps de messages, URL de MMS, identifiants de souscription ou captures non expurgées.

**Décision de validation :** non prononcée.  
**Release publique :** reste soumise à [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md).

## Campagne Samsung S24+ Android 16 / Watch6

Chaque ligne ci-dessous est **NON EXÉCUTÉE**. Consigner attendu, observé, horodatage,
SHA source, SHA-256 APK, modèle/build OEM, preuve expurgée et numéro de défaut.
Un test interrompu, un oracle UNKNOWN ou une action UI sans effet vérifié ne donne
jamais PASS. Répéter les scénarios opérateur sur chaque SIM et conserver
`NOT_REPORTED` lorsque le réseau ne fournit pas de rapport de livraison.

Préparer deux lignes de test, un casque Bluetooth, une Watch6 avec Wear OS et une
version précédente connue. Pour l'upgrade, comparer les certificats : les clés debug
éphémères des runners peuvent différer. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` signifie
que ce couple d'APK ne permet pas de qualifier l'upgrade ; ne pas désinstaller puis
présenter l'installation propre comme un upgrade réussi.

| ID | Manipulation | Preuve / résultat exigé |
| --- | --- | --- |
| S01 | Installation propre : `adb install APK`, premier démarrage | package/version/certificat exacts, processus vivant, écran utilisable, aucun crash/ANR |
| S02 | Upgrade : `adb install -r APK` depuis la version précédente compatible | données/préférences conservées, migration correcte, aucune réutilisation indue d'ancien certificat technique |
| S03 | Wizard : commencer, différer, fermer, rouvrir puis terminer | NOT_STARTED/OFFERED/IN_PROGRESS/DEFERRED/COMPLETED cohérents ; aucun READY artificiel |
| S04 | Accorder puis refuser ROLE_DIALER | titulaire Android effectivement lu ; action protégée bloquée après refus |
| S05 | Accorder puis révoquer Call Screening | titulaire relu avant moteur ; aucune décision moteur après révocation/UNKNOWN |
| S06 | ROLE_SMS : refuser puis accorder | aucune demande de permission SMS avant rôle HELD ; UNAVAILABLE interdit l'envoi |
| S07 | Notifications : refuser/accepter permission, désactiver canal | état réel affiché, pas de faux signal « notification publiée » |
| S08 | Contacts/historique : accorder, révoquer, revenir depuis Réglages | providers observés, absence d'accès non autorisé, état UI resynchronisé |
| S09 | Appel entrant : décrocher, puis terminer | Telecom/InCall RINGING → ACTIVE → DISCONNECTED, conversation audible des deux côtés |
| S10 | Appel entrant : rejeter | rejet effectif côté seconde ligne, fin Telecom cohérente |
| S11 | Appel sortant : une seule action, puis raccrocher | une seule session ; SIM framework sélectionnée ; ACTIVE puis absence de toute session résiduelle |
| S12 | Verrouiller/éteindre écran avant appel ; réveiller et reprendre UI | notification/écran autorisés, réponse/rejet/hangup effectifs ; aucune boucle de lancement |
| S13 | Haut-parleur → écouteur → Bluetooth ; déconnecter/reconnecter casque | route Telecom et son réel cohérents ; pas de perte microphone ou appel fantôme |
| S14 | Urgence : vérifier le handoff système et absence de boucle vers Sentinel | aucun numéro réel d'urgence composé sans procédure expressément autorisée par le laboratoire/opérateur ; routage prioritaire sans SIM picker/risque |
| S15 | SMS simple : envoyer et recevoir | provider + callbacks SENT_ALL_PARTS ; DELIVERED_CONFIRMED seulement si tous les retours existent |
| S16 | SMS multipart : GSM-7/UCS-2, réception désordonnée, doublon | concat ref/sequence et SC timestamp conservés ; reconstitution correcte ; déduplication 24 h |
| S17 | MMS opérateur : envoyer/recevoir, erreur puis reprise | succès de transport réel, aperçu sûr ; erreurs/quarantaine ne sont pas PASS |
| S18 | Réseau absent puis mode avion, restauration réseau | erreurs explicites, aucun faux SENT/DELIVERED/READY ; reprise sans double envoi |
| S19 | Changement SIM et double SIM | choix explicite à chaque ambiguïté, relecture PhoneAccount avant placeCall ; jamais de compte VoIP/self-managed PSTN |
| S20 | Révoquer permissions CALL_PHONE/READ_PHONE_STATE/SEND_SMS et AppOps | absence d'opération autorisée à tort ; UNKNOWN reste conservateur ; restauration observée |
| S21 | Révoquer rôles pendant activité, arrière-plan et appel | état courant, session Telecom suivie ; aucun moteur de blocage sans autorité |
| S22 | Redémarrer téléphone puis relancer | rôles/providers/session réévalués ; pas de preuve réinjectée ni faux canal Wear actif |
| S23 | Watch6 : discovery, handshake, alertes appels/SMS, quick actions | transport Data Layer réel, session authentifiée fraîche, négociation ; rejeu/expiration refusés ; rester NON EXÉCUTÉ si transport absent |
| S24 | Mesurer Call Screening sous charge, froid/chaud et écran éteint | callback → respondToCall <500 ms ; aucun réseau avant réponse ; mesurer séparément latence Telecom complète |
| S25 | Endurance, batterie, transitions UI/audio et perte de réseau | logcat/buffer crash + bugreport expurgés ; aucun crash/ANR critique ; consommation mesurée sur durée déclarée |
| S26 | Voice : appel VoIP/PSTN transformé, écho, mute/hold/reconnexion/audio | serveur/token issuer/gateway réels ; trames transformées et latence mesurées ; aucun support de média SIM natif inventé |

Collecte minimale (sur l'appareil déclaré) : `adb shell getprop ro.build.fingerprint`,
`adb shell dumpsys package com.sentinel.quantum`, `adb shell dumpsys role`,
`adb shell dumpsys telecom`, `adb logcat -b crash -d`, `adb shell dumpsys batterystats`.
Les dumps Telecom/logcat/bugreports peuvent contenir des données personnelles :
conserver l'original dans le laboratoire et expurger avant partage. L'émulateur et
les marqueurs synthétiques n'attestent aucune ligne de cette campagne.

## Manifeste machine-readable et décision fail-closed

Le relevé terrain humain reste la source de l'observation. Pour qu'une session puisse
être vérifiée de façon reproductible par le gate de release, produire en plus un
manifeste JSON conforme à `config/phone-core-physical-session.schema.json`.

Le manifeste doit être lié sans ambiguïté au `source_head_sha`, au SHA-256 de l'APK,
à l'empreinte SHA-256 du certificat de signature, au modèle physique et au fingerprint
OEM. Il contient les 14 critères canoniques, S01 à S26 et les quatre validations
externes bloquantes de la release commerciale. Les références de preuves doivent
pointer vers des fichiers locaux expurgés placés sous le répertoire `evidence/`
à côté du manifeste ; les originaux restent dans le laboratoire lorsqu'ils
contiennent des données personnelles. Chaque référence `evidence_refs` doit être
au format `evidence/nom-de-preuve.ext#sha256=<64 caractères hexadécimaux>`.
Le SHA-256 est couvert par la signature Ed25519 du manifeste. Le vérificateur
ouvre chaque fichier de preuve déclaré `PASS`, rejette les chemins traversants,
les liens symboliques, les fichiers absents ou vides, et recalcule son empreinte.
Le laboratoire doit fournir le répertoire `evidence/` lors de la vérification ;
une URL ou un nom de fichier sans preuve présente ne suffit pas. La signature
authentifie la déclaration du laboratoire, mais ne prouve pas à elle seule qu'un
essai a réellement eu lieu : la confiance dans le signataire et l'audit des
observations physiques demeurent indispensables.

L'attestation doit être `PHYSICAL_DEVICE` et signée en **Ed25519** par une clé de
laboratoire ou de release QA de confiance. La clé privée ne doit jamais être stockée
dans ce dépôt. Le vérificateur reçoit uniquement une clé publique de confiance au
moment de l'exécution. Un manifeste altéré après signature, une signature invalide,
un SHA source/APK/certificat différent, ou une preuve d'émulateur/synthétique entraîne
un refus fail-closed.

Exemple d'exécution :

```bash
node scripts/verify-phone-core-physical-session.js physical-session.json \
  --trusted-public-key release-qa-public-key.pem \
  --expected-source-sha <40-hex> \
  --expected-apk-sha256 <64-hex> \
  --expected-certificate-sha256 <64-hex>
```

Le vérificateur expose trois verdicts strictement distincts :

- `PHYSICAL_SESSION_INCOMPLETE` : au moins un des 14 critères canoniques ou un scénario S01–S26 n'est pas `PASS` ;
- `PHYSICAL_PHONE_CORE_PASS` : les 14 critères et S01–S26 sont `PASS`, mais au moins une validation externe commerciale reste non satisfaite ;
- `COMMERCIAL_RELEASE_ELIGIBLE` : les 14 critères, S01–S26 et les quatre validations externes commerciales sont tous `PASS` avec attestation et provenance valides.

`FAIL`, `NOT_EXECUTED`, `UNKNOWN` et `NOT_REPORTED` ne sont jamais convertis en
`PASS`. Le code de sortie est 1 pour une preuve invalide/non fiable, 2 pour une session
valide mais incomplète ou limitée à `PHYSICAL_PHONE_CORE_PASS`, et 0 uniquement pour
`COMMERCIAL_RELEASE_ELIGIBLE`.

`COMMERCIAL_RELEASE_ELIGIBLE` signifie que ce gate Phone Core n'a plus de preuve
physique manquante. Cela ne remplace pas les autres exigences de publication
(signature de release, conformité Play, juridique, sécurité globale et checklist de
release). Ce mécanisme ne réalise aucun essai physique lui-même et ne modifie aucun
résultat de campagne : tant qu'un opérateur/laboratoire n'a pas réellement exécuté
les scénarios ci-dessus, ils restent **NON EXÉCUTÉS**.
