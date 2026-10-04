# Protocole de validation physique — Phone Core v5

**Révision :** 4 octobre 2026  
**Statut :** protocole de release QA externe ; aucun résultat terrain renseigné  
**Validation manuelle du propriétaire du dépôt :** non requise pour la qualification développeur  
**Référence logicielle :** partir du SHA exact finalement retenu après #1559 et après réussite du gate d’émulation Android.

La qualification développeur est définie dans [PHONE_CORE_EMULATION_QUALIFICATION.md](PHONE_CORE_EMULATION_QUALIFICATION.md). Le présent document ne doit pas être utilisé pour bloquer le développement faute de téléphone double-SIM. Il couvre uniquement ce qu’un émulateur ne peut pas certifier : modem/OEM réel, réseau opérateur réel et mesure matérielle réelle.

Source des 14 critères locaux : [PhoneCorePhysicalValidation.kt](../native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCorePhysicalValidation.kt). Le certificat v5 comporte exactement 14 critères : deux vérifications automatiques de providers et douze observations opérationnelles. Les essais de compatibilité, de performance matérielle et le Wi-Fi sont séparés de ce compteur.

## Préparation release QA

1. Exécuter d’abord la matrice d’émulation API 29/API 36 et conserver ses artefacts. Une branche rouge en émulation n’est pas admissible à la validation physique.
2. Utiliser ensuite un ou plusieurs appareils physiques et des lignes mobiles de test gérés par la QA/device-lab. Le propriétaire du dépôt n’est pas tenu de posséder un appareil double-SIM.
3. Installer l’APK du commit exact à vérifier après examen de ses contrôles CI ; relever SHA-256 et certificat de signature. Un APK debug de validation ne devient pas un livrable public signé.
4. Ouvrir le centre Phone Core. Accorder explicitement les rôles Téléphone, Filtrage d’appels et SMS requis, puis les permissions nécessaires. Vérifier canaux de notification et réglages constructeur.
5. Utiliser le périmètre de certification de l’installation courante. Ne pas réinjecter d’événements, modifier le stockage ou reprendre les preuves d’une ancienne installation pour remplir le compteur.

## Relevé de session

| Champ | Valeur à renseigner |
| --- | --- |
| Date UTC et responsable QA | À renseigner |
| Commit exact, version et canal de l’APK | À renseigner |
| SHA-256 de l’APK et empreinte du certificat | À renseigner |
| Modèle, constructeur, version Android et niveau API | À renseigner |
| Configuration réseau, opérateur et simple/double-SIM | À renseigner sans numéro ni identifiant de souscription |
| Rôles, permissions et canaux effectivement accordés | À renseigner |
| Installation/périmètre de certification courant | À vérifier |
| Liens vers workflows et artefacts du commit testé | À renseigner |

## Les 14 preuves locales

Toutes les lignes commencent à **non exécuté**. Après chaque scénario, consulter le diagnostic Phone Core et conserver uniquement une observation minimisée. Une action lancée sans résultat ne suffit pas.

| Critère du code | Scénario à exécuter | Résultat exigé | Résultat terrain |
| --- | --- | --- | --- |
| `incoming_call_connected` | Appeler la ligne de test puis décrocher avec Sentinel. | Appel réellement actif ; preuve entrante `INCALL_ACTIVE`. | Non exécuté |
| `outgoing_call_connected` | Depuis le composeur Sentinel, appeler la seconde ligne et faire décrocher. | Appel réellement actif ; preuve sortante `INCALL_ACTIVE`. | Non exécuté |
| `call_screening_observed` | Recevoir un appel avec Sentinel sélectionné pour le filtrage. | Décision réellement observée `CALL_SCREENED:` ; une simple sonnerie ne suffit pas. | Non exécuté |
| `contacts_provider_ready` | Accorder l’accès aux contacts et vérifier le diagnostic/provider sur l’appareil. | Sonde automatique du provider réussie ; permission seule insuffisante. | Non exécuté |
| `call_history_provider_ready` | Avec rôle Téléphone et permission d’historique, vérifier le provider local. | Sonde automatique du provider réussie. | Non exécuté |
| `incoming_sms_received` | Envoyer un SMS à Sentinel depuis une autre ligne. | Réception réelle et preuve `SMS_RECEIVED`. | Non exécuté |
| `outgoing_sms_submitted` | Envoyer un SMS puis un SMS multiparties depuis Sentinel. | Toutes les parties envoyées avec succès ; `SMS_ALL_PARTS_SENT`. | Non exécuté |
| `outgoing_sms_delivered` | Observer les retours de livraison du SMS sortant sur le réseau réel. | Toutes les parties livrées avec succès ; `SMS_ALL_PARTS_DELIVERED`. Si l’opérateur ne fournit pas ces retours, conserver le critère manquant. | Non exécuté |
| `incoming_mms_safe_preview` | Recevoir un MMS pris en charge et ouvrir son aperçu sécurisé ; tester le téléchargement opérateur si nécessaire. | `MMS_SAFE_PREVIEW_READY` ou `MMS_DOWNLOAD_SAFE_PREVIEW_READY` ; une quarantaine ne suffit pas. | Non exécuté |
| `outgoing_mms_sent` | Depuis le mode MMS Sentinel, envoyer un MMS réel sur la SIM choisie. | Android confirme le succès du transport MMS par callback ; preuve `MMS_SENT_OK`. Une simple soumission du PDU ou un code d’erreur ne suffit pas. | Non exécuté |
| `incoming_call_notification` | Recevoir un appel dans une situation où le canal de notification est autorisé. | Publication acceptée par Android et observation terrain de la notification ; `CALL_NOTIFICATION_POSTED`. | Non exécuté |
| `incoming_sms_notification` | Recevoir un SMS avec notifications autorisées. | Publication acceptée par Android et observation terrain de la notification ; `SMS_NOTIFICATION_POSTED`. | Non exécuté |
| `caller_id_ui_shown` | Recevoir un appel et observer la fiche locale d’identification. | Surface réellement affichée ; `CALLER_ID_UI_SHOWN`. | Non exécuté |
| `in_call_ui_shown` | Observer Sentinel pendant un appel. | Interface InCall réellement affichée ; `INCALL_UI_SHOWN`. | Non exécuté |

Les noms de signaux permettent de rapprocher le diagnostic du code ; ils ne sont jamais des instructions pour fabriquer des événements.

## Contrôles externes indispensables avant release commerciale

Ces contrôles sont hors du gate d’émulation et doivent être réalisés par une QA/device-lab ou une campagne de release dédiée.

### 1. Réseau opérateur réel

- confirmer callbacks de livraison SMS réels ;
- confirmer envoi/réception MMS réel et callback de succès opérateur ;
- conserver les échecs comme échecs, sans transformer une soumission Android en succès réseau.

### 2. Samsung Galaxy S24+ / Android 16

- mesurer la latence entre entrée du screening et appel effectif à `respondToCall` ;
- conserver plusieurs mesures sur appels autorisés, silencés et bloqués ;
- exiger un maximum strictement inférieur à 500 ms ;
- vérifier lockscreen, notification, Caller ID et InCall sur le matériel réel.

### 3. Compatibilité double-SIM physique

La logique multi-SIM est déjà une exigence de la qualification logicielle/émulée. La campagne physique ne doit pas refaire ces tests manuellement pour le plaisir ; elle doit seulement confirmer les différences que l’émulateur ne sait pas reproduire : comportement modem/OEM, bascule de ligne réelle, abonnement désactivé physiquement et régions de SIM divergentes sur matériel réel.

Aucun appareil double-SIM personnel du propriétaire du dépôt n’est requis. Cette vérification peut être déléguée à une QA/device-lab avant release commerciale.

## Compatibilité et réversibilité complémentaires

- **Versions/constructeurs :** répéter seulement la matrice physique réellement ciblée ; l’émulation API 29/API 36 reste le gate développeur.
- **Notifications :** refuser la permission ou désactiver un canal, puis vérifier les états affichés sans déclaration de succès erronée.
- **Retrait des rôles/permissions :** ces scénarios sont déjà couverts automatiquement en instrumentation ; la QA physique ne les répète que pour détecter un comportement OEM différent.
- **Échecs transport :** tester envoi/livraison en erreur et MMS non pris en charge ; ils ne doivent pas compléter les critères de succès.
- **Nouvelle installation/version :** vérifier que le périmètre courant n’utilise pas des preuves incompatibles ou antérieures.
- **Wi-Fi séparé :** tester scan frais, cache, permissions, localisation, throttling et absence de résultat frais. Aucun résultat Wi-Fi ne modifie le certificat 14/14.

## Clôture

La qualification développeur peut être déclarée **émulation verte** sans intervention manuelle du propriétaire du dépôt lorsque la matrice API 29/API 36 est entièrement verte et que ses artefacts existent.

La qualification **production commerciale** exige en plus les contrôles externes réseau/opérateur, la latence S24+ réelle, la compatibilité double-SIM physique cible et la release signée. Ces preuves doivent être liées au même SHA ; elles ne peuvent être héritées d’une autre version.

Ne pas exporter numéros, noms de contacts, corps de messages, URL de MMS, identifiants de souscription ou captures non expurgées.

**Décision de validation commerciale :** non prononcée.  
**Release publique :** reste soumise à [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md).
