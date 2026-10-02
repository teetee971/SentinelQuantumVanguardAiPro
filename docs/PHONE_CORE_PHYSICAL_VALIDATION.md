# Protocole de validation physique — Phone Core v5

**Révision :** 2 octobre 2026  
**Statut :** protocole prêt à exécuter ; aucun résultat terrain renseigné  
**Référence logicielle examinée :** PR #1497 ; utiliser le SHA exact et l’artefact exact qui auront passé les gates avant l’essai terrain.

Source des critères : [PhoneCorePhysicalValidation.kt](../native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCorePhysicalValidation.kt). Le certificat comporte exactement 14 critères : deux vérifications automatiques de providers et douze observations opérationnelles, dont l’envoi MMS réel. Les essais de compatibilité et le Wi-Fi sont séparés de ce compteur.

## Préparation

1. Utiliser un appareil physique et une ligne mobile de test, avec un second appareil/une seconde ligne pour appels et messages. Ne pas utiliser de numéro d’urgence pour ces essais.
2. Installer l’APK du commit à vérifier après examen de ses contrôles CI ; relever son SHA-256 et son certificat de signature. Un APK debug de validation ne devient pas un livrable public signé.
3. Ouvrir le centre Phone Core. Accorder explicitement les rôles Téléphone, Filtrage d’appels et SMS requis, puis les permissions nécessaires. Vérifier canaux de notification et réglages constructeur.
4. Utiliser le périmètre de certification de l’installation courante. Ne pas réinjecter d’événements, modifier le stockage ou reprendre les preuves d’une ancienne installation pour remplir le compteur.
5. Sélectionner explicitement la ligne utilisée lorsqu’il existe plusieurs SIM.

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

## Les 14 preuves

Toutes les lignes commencent à **non exécuté**. Après chaque scénario, consulter le diagnostic Phone Core et conserver uniquement une observation minimisée. Une action lancée sans résultat ne suffit pas.

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

Les noms de signaux permettent de rapprocher le diagnostic du code ; ils ne sont jamais des instructions pour fabriquer des événements.

## Compatibilité et réversibilité — hors compteur

- **Double-SIM :** répéter appels et SMS sur chaque ligne, puis rendre une ligne indisponible. Aucun choix arbitraire ne doit être présenté comme validé.
- **Versions/constructeurs :** répéter sur la matrice réellement ciblée. L’activation guidée du filtrage est revendiquée à partir d’API 29 ; API 24–28 ne sont pas réputées couvertes par ce parcours.
- **Notifications :** refuser la permission ou désactiver un canal, puis vérifier les états affichés sans déclaration de succès erronée.
- **Retrait des rôles/permissions :** revenir aux applications système et refuser les accès ; observer l’état courant des prérequis et l’absence d’opération non autorisée. Refaire les vérifications après restauration.
- **Échecs transport :** tester envoi/livraison en erreur et MMS non pris en charge ; ils ne doivent pas compléter les critères de succès.
- **Nouvelle installation/version :** vérifier que le périmètre courant n’utilise pas des preuves incompatibles ou antérieures.
- **Wi-Fi séparé :** tester scan frais, cache, permissions, localisation, throttling et absence de résultat frais. Aucun résultat Wi-Fi ne modifie le certificat 14/14.

Pour chaque essai complémentaire, relever scénario, environnement, attendu, observé, résultat et défaut associé. Un scénario non exécuté reste explicitement non validé.

## Clôture

La clôture exige 14/14 dans le diagnostic de l’installation courante, des observations terrain correspondantes, l’examen de la matrice ciblée et la résolution/documentation des écarts. Le certificat local n’est pas une certification externe et ne prouve pas la compatibilité universelle.

Conserver commit, dates, résultats et références aux preuves. Ne pas exporter numéros, noms de contacts, corps de messages, URL de MMS, identifiants de souscription ou captures non expurgées.

**Décision de validation :** non prononcée.  
**Release publique :** reste soumise à [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md).
