# Qualification Galaxy S24+ / Android 17

**Statut de tous les scénarios : NON TESTÉ.** Aucun appareil physique connecté dans l’environnement de build. La compilation de l’APK instrumenté et les tests JVM ne valident aucun scénario de cette fiche.

## Préparation

Utiliser un Galaxy S24+ de test, deux ou trois lignes connues appartenant aux testeurs, sans numéro surtaxé ni urgence. Aucun appel n’est automatisé par les scripts livrés. Pas de données de contacts ou messages personnelles dans les captures partagées. Ne pas effacer les données d’une installation existante pour contourner une incompatibilité de signature.

```bash
adb get-state
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell am get-current-user
```

Noter le modèle et la version réelle : la campagne demandée attend **Android 17 / API 37**. Si cette version n’est pas installée, ne pas qualifier le résultat comme Android 17. Noter l’empreinte du binaire avec `sha256sum -c SHA256SUMS.txt`, la date UTC et la SIM de test sous un libellé fictif (« ligne A »), sans numéro/IMSI/IMEI/numéro de série.

## Exécution manuelle

| ID | Action | Attendu et preuve à conserver | Résultat actuel |
|---|---|---|---|
| S01 | Installer l’APK, lancer via l’icône puis ouvrir Téléphone | Installation Success, écran accessible, absence d’exception fatale sur ce parcours ; version 1.0.9/code10 | NON TESTÉ |
| S02 | Centre d’activation : annuler une demande ROLE_DIALER, puis redemander et accepter ; tourner l’écran pendant la demande | Annulation explicite ; après accord le holder est com.sentinel.quantum, état rafraîchi ; pas de demande dupliquée | NON TESTÉ |
| S03 | Appeler la ligne A depuis la ligne B, Sentinel au premier plan puis en arrière-plan/écran verrouillé | Sonnerie opérateur, surface entrante et bonnes actions sur le bon appel ; plein écran selon autorisation Android | NON TESTÉ |
| S04 | Répondre, vérifier audio bidirectionnel, raccrocher depuis Sentinel puis répéter depuis l’autre ligne | Passage observé RINGING→ACTIVE→fin ; arrêt notification/tonalité ; fin réelle sur les deux lignes | NON TESTÉ |
| S05 | Appeler B depuis le pavé, puis un contact autorisé ; tester origine externe ACTION_DIAL sans lancer automatiquement | Bonne ligne/SIM choisie, surface d’appel, audio réel, raccrochage ; refus explicite si ligne absente | NON TESTÉ |
| S06 | Sur messagerie vocale ou serveur vocal de test connu, envoyer DTMF ; fermer le pavé pendant une tonalité et saisir rapidement deux chiffres | Chiffres reçus, aucune tonalité persistante ; aucune utilisation de numéro inconnu/surtaxé | NON TESTÉ |
| S07 | Ne pas répondre à un appel de B, puis ouvrir et effacer la notification d’appel manqué | Un appel manqué cohérent avec historique Android ; pas de notification fantôme ni crash | NON TESTÉ |
| S08 | Pendant A↔B, recevoir un appel de C ; répondre/rejeter le second, mettre en attente/reprendre si capacité disponible | Nouvelle alerte pour C ; commandes ciblées au bon ID ; appel restant encore contrôlable après fin du second. Conférence masquée en V1 | NON TESTÉ |
| S09 | Pendant appel, accueil puis retour par notification ; rotation, mise en veille, reprise | Notification en cours permet le retour et le raccrochage ; état réel conservé ; audio/muet cohérents | NON TESTÉ |
| S10 | Hors appel, enlever le rôle Téléphone dans Paramètres, revenir à Sentinel puis tenter l’appel | Rôle relu, accès au journal et appel bloqués conformément aux permissions ; aucune affirmation d’attribution conservée | NON TESTÉ |
| S11 | Révoquer une permission à la fois (CALL_PHONE, READ_PHONE_STATE, READ_CONTACTS, READ_CALL_LOG, POST_NOTIFICATIONS), revenir dans l’application | Pas de crash ; demandes ou limitations explicites ; pas d’accès aux contacts/historique après retrait ; aucune relance automatique d’appel après mort du processus | NON TESTÉ |
| S12 | Bloquer canal Appels, Appels manqués, puis désactiver l’accès plein écran ; répéter un appel de test | État d’activation non présenté comme entièrement prêt ; comportement limité visible et conforme aux réglages ; publication technique ≠ notification vue | NON TESTÉ |
| S13 | Hors appel, fermer/forcer l’arrêt puis rouvrir ; redémarrer le téléphone puis ouvrir Sentinel | Rôles/permissions relus ; numéro saisi restauré seulement si Android a sauvegardé la tâche ; aucun appel automatique ; un nouvel appel entrant reste testable après réouverture | NON TESTÉ |
| S14 | Revenir au téléphone Samsung par Paramètres, refaire un appel de test entrant/sortant | Holder Samsung observé, commandes Samsung fonctionnelles ; Sentinel ne se présente plus comme titulaire | NON TESTÉ |

Ne pas tester de force-stop/reboot pendant un appel utile. Un force-stop Android peut suspendre la livraison des événements jusqu’à une réouverture explicite ; ne pas prétendre résister à cette restriction système.

## ADB et preuves

```bash
adb shell cmd role get-role-holders android.app.role.DIALER
adb shell dumpsys package com.sentinel.quantum | rg 'versionCode=|versionName='
# Lecture des faits/permissions uniquement, sans dumpsys telecom ni contacts/messages :
adb shell dumpsys package com.sentinel.quantum | rg 'android.permission.(CALL_PHONE|READ_PHONE_STATE|READ_CONTACTS|READ_CALL_LOG|POST_NOTIFICATIONS).*granted='
# Tests de révocation : déterminer le user de test ; exemple ci-dessous pour user 0 seulement.
adb shell pm revoke --user 0 com.sentinel.quantum android.permission.CALL_PHONE
# Restaurer par le dialogue Android lors de la prochaine action, puis confirmer l’état.
adb shell am start -a android.settings.MANAGE_DEFAULT_APPS_SETTINGS
```

Pour les utilisateurs autres que 0, remplacer explicitement le user après `am get-current-user`. Si une permission est verrouillée par le rôle/la politique du téléphone et que `pm revoke` échoue, conserver l’échec de commande puis tester le retrait depuis Paramètres ; ne pas inscrire PASS.

Collecte bornée et filtrée, sans enregistrement de numéros ni dump de conversations :

```bash
python3 scripts/capture-phone-core-s24.py --output /chemin/prive/preuves-S24
```

Le collecteur vérifie le runtime, copie seulement modèle/version et holders de rôle, filtre les tags de journal ciblés, masque numéros/adresses/URLs avant toute écriture, et ne collecte pas le numéro de série. Relire le journal filtré avant tout partage : un nom libre dans un message d’exception ne peut pas être anonymisé avec certitude par une expression régulière. Ne pas joindre de capture visuelle contenant noms/numéros réels. Les traces Android peuvent être absentes : l’absence d’exception dans un tampon incomplet ne prouve pas seule l’absence de crash.

Pour chaque scénario, enregistrer dans une copie locale : ID, UTC, binaire SHA-256, action manuelle, résultat PASS/FAIL/NON TESTÉ, observation des deux testeurs et nom du fichier expurgé. Un signal de publication de notification ne suffit pas à PASS S03/S07/S08.

## Instrumentation (runtime nécessaire)

```bash
adb install -r Sentinel-PhoneCore-V1-androidTest.apk
adb shell am instrument -w -e class com.sentinel.quantum.PhoneCoreManifestContractInstrumentationTest,com.sentinel.quantum.PhoneCoreSetupResumeInstrumentationTest,com.sentinel.quantum.PhoneCoreCallNotificationInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Exécuter hors appel réel : le test de notification utilise une notification synthétique puis l’annule. Déclarer les tests ignorés pour notifications refusées comme SKIP, jamais PASS. Cette instrumentation ne remplace pas les 14 scénarios opérateur. Ici : **NON EXÉCUTÉE, aucun runtime Android connecté et aucun émulateur/KVM disponible**.
