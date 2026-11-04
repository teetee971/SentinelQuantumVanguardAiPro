# Phone Core V1 — qualification du 29 octobre 2026

> Rapport initial de d03dfb07 conservé comme historique. La livraison actualisée (sources Android 846ed793, 1 356 tests app, 30 permissions fusionnées) est dans [release/phone-core-v1/BUILD_REPORT.md](../release/phone-core-v1/BUILD_REPORT.md). Les empreintes et résultats ci-dessous concernent la livraison initiale.

Statut : compilation et tests hôte réussis, publication Google Play non autorisée par ce rapport. Les scénarios sur Galaxy S24+ sont **NON TESTÉS** : `adb devices -l` ne détecte aucun appareil. Aucun lancement sans crash, rôle effectivement accordé, appel réseau ou révocation sur appareil ne peut être certifié ici.

## Références et périmètre

Le workspace fourni était vide. Les schémas Room non suivis de la machine de l’utilisateur ne sont donc pas accessibles ici ; aucun fichier de cette machine n’a été supprimé. Le schéma présent dans ce clone est conservé.

- `origin/main` observé : `935344423b2af2a4a57c58123f2c5c178b46ec03`.
- Base demandée récupérée : `da157f4c5593c94f1a1dd12c994c83c23618b7c5`.
- Sauvegarde : `9d093badf024a6834075ed9c593aa3b6b81b4aff`.
- La branche locale de qualification annoncée n’existait pas sur le serveur. Une base distante `qualification/phone-core-v1-20261029` est créée explicitement à `da157f4` pour une PR ciblée depuis `work/phone-core-v1-20261029`.
- Le serveur montre actuellement `main` ancêtre de la base (188 commits) et de la sauvegarde (190 commits). Ce constat ne décrit pas les références locales absentes de la machine utilisateur.
- Aucun merge de sauvegarde, modification de `main`, reset destructif, nettoyage ou déclenchement de CI n’est utilisé. Les commits publiés portent `[skip ci]` ; aucun workflow n’est dispatché.

Comparaison : `f86e7d6` apporte seulement le diagnostic de demande de rôle et sa politique/test ; intégré. `9d093ba` apporte seulement l’import `rememberSaveable` ; intégré avec les deux états effectivement sauvegardés. Le commit intermédiaire `16ea149` n’est pas importé globalement : seule la correction de lecture du rôle demandé est reproduite. Le service de base correspond au parent de `e121aefa` ; son patch Phone Core et ses tests sont intégrés sans les autres changements de sa branche. La revue rétablit ensuite l’ouverture protégée des contrôles pour un appel sortant créé hors du composeur : InCallService est un service Téléphone lié par le système, éligible à l’exception Android de lancement en arrière-plan ; le refus éventuel est capturé. Les politiques pures du service sont isolées dans `InCallEvidenceTracker.kt` pour être testées sans Android.

## Fonctionnement établi par revue du code

| Fonction | Implémentation / limites |
|---|---|
| Candidature Téléphone | ACTION_DIAL avec et sans `tel:`, InCallService exporté protégé par BIND_INCALL_SERVICE, metadata UI=true. Contrat installé couvert par instrumentation, exécution appareil requise. |
| Demande ROLE_DIALER | RoleManager sur API 29+, ACTION_CHANGE_DEFAULT_DIALER avant 29 ; annulation/échec affichés ; résultat vérifié par rôle réellement détenu, état conservé à la recréation. |
| Appels sortants | Telecom.placeCall après rôle, CALL_PHONE, contrôle de ligne SIM ; sélection explicite multi-SIM ; interface ouverte depuis le composeur au premier plan. L’acceptation de placeCall ne prouve pas une connexion réseau. |
| Appels entrants | Sélection de l’appel prioritaire, notification CallStyle, plein écran entrant, réponse/rejet ciblés par ID. Notifications bloquées ou plein écran refusé : visibilité non garantie, à tester explicitement. |
| Appel en cours | Notification persistante avec retour à l’interface et raccrochage ciblé ; remplacement de la notification entrante après changement d’état observé. |
| Contrôles | Réponse, rejet, raccrochage, attente selon capacités Telecom ; commande Attente absente si indisponible ; muet/audio selon état et capacités ; fusion/permutation seulement si exposées par Telecom, non validées réseau. |
| DTMF | Chiffres limités à 0–9, *, #, appel actif et ID exact ; arrêt en finally lors d’une annulation et fin du précédent avant le suivant. |
| Contacts/historique | Contacts optionnels et lecture provider protégée ; historique conditionné au rôle et à READ_CALL_LOG ; révocation réelle non testée. |
| Concurrence | IDs par instance de service/appel, registre de session atomique ; jetons de génération pour preuves ; callbacks audio obsolètes ignorés. |
| Preuves | Notification publiée distincte d’une notification observée par l’utilisateur ; schéma technique v6 à 14 critères, sans déclaration de validation physique. |

SMS/MMS, VPN, BLE, Voice Studio et Security Lab sont préservés dans la base choisie. Leur fonctionnement matériel/réseau n’est pas établi par cette livraison. Les critères SMS/MMS existants restent dans le diagnostic technique ; ils ne sont pas une condition pour fabriquer l’APK Phone Core de test.

## Build local reproductible

Environnement initial : Java 21, pas de SDK Android ni de Gradle installé. Outils installés dans `/workspace/tooling` : JDK Temurin 17.0.21, Gradle 9.8.0, Android SDK `platforms;android-37.0`, Build Tools 36.0.0, platform-tools. Configuration du dépôt : AGP 9.4.1, Kotlin/Compose 2.4.20, KSP 2.3.12, compileSdk 37, targetSdk 36, minSdk 24, versionCode 10, versionName 1.0.9.

```bash
export JAVA_HOME=/chemin/jdk17
export ANDROID_HOME=/chemin/android-sdk
sdkmanager 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0'
bash scripts/phone-core-v1-build.sh
# Relancer les tests avec l’APK comme fixture de preuve cryptographique :
cd native-android-app
bash gradlew :app:testDebugUnitTest -PsentinelSignatureFixture="$PWD/app/build/outputs/apk/debug/app-debug.apk" --no-daemon --console=plain
cd ..
```

En cloud derrière proxy : conserver HTTPS_PROXY ; configurer les propriétés Java `https.proxyHost/Port` et `http.proxyHost/Port` pour le proxy existant. Le JDK téléchargé doit utiliser le truststore système (`-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts` ici). Ne pas désactiver TLS. Termux/proot ARM64 : vérifier `uname -m`, JDK17 et l’exécutable aapt2 compatible ; passer au script `-Pandroid.aapt2FromMavenOverride=/chemin/aapt2` si nécessaire. Aucun compte Codemagic n’est configuré ici.

Tests JVM indépendants du SDK :

```bash
GRADLE_LIB_DIR=/chemin/gradle-9.8.0/lib \
JUNIT_JAR=/chemin/junit-4.13.2.jar HAMCREST_JAR=/chemin/hamcrest-core-1.3.jar \
bash scripts/phone-core-v1-jvm-tests.sh
```

## Google Play / signature

La configuration existante expose `release` minifiée et signée uniquement si `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` sont fournis. Aucune clé ni valeur secrète n’est ajoutée au dépôt. `releaseUnsigned` efface explicitement la signature héritée et sert uniquement à qualifier le bundle.

```bash
cd native-android-app
# Préparation sans secrets, non publiable :
bash gradlew :app:bundleReleaseUnsigned --no-daemon --console=plain
# Après injection sécurisée des quatre variables de signature :
bash gradlew :app:bundleRelease --no-daemon --console=plain
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

Compatibilité pages mémoire 16 Kio : les 14 bibliothèques natives 64 bits de l’APK debug ont des segments PT_LOAD alignés au moins sur 16 Kio ; `zipalign -c -P 16 -v 4` réussit. Cela ne remplace pas le test sur système à pages 16 Kio ni le contrôle du bundle release.

Avant publication : vérifier versionCode face au dernier upload Play (10 n’est pas confirmé disponible), signature d’upload/Play App Signing, manifeste fusionné release, Data Safety, politique de confidentialité publique et déclarations de permissions. Aucun accord Play n’est présumé.

- CALL_PHONE / READ_PHONE_STATE : finalité d’appel/SIM, demander au moment utile et supporter refus/révocation.
- READ_CALL_LOG : permission restreinte ; rôle Téléphone réellement détenu et fonctionnalité principale à déclarer à Play. Arrêter l’accès dès perte du rôle.
- SEND_SMS, RECEIVE_SMS, READ_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH : toujours déclarées pour préserver le module SMS. Leur présence impose la déclaration Play SMS/Call Log et une éligibilité démontrée, généralement comme gestionnaire SMS par défaut. **Blocage de publication potentiel** tant que l’usage/éligibilité du module n’est pas prouvé ; ce rapport n’en atteste pas. Un artefact limité au téléphone pourrait nécessiter une variante de manifeste dédiée après décision produit.
- POST_NOTIFICATIONS / USE_FULL_SCREEN_INTENT : finalité appels ; tester autorisation globale, canal et accès spécial plein écran sur Android 14+. Compléter la déclaration Play applicable aux appels entrants.
- READ_CONTACTS : optionnel ; pas de blocage du composeur si refus.
- RECORD_AUDIO : Voice Studio sur action explicite ; aucune capture d’appel opérateur certifiée.
- Le manifeste **fusionné releaseUnsigned compte 32 permissions**, contre 24 dans le manifeste applicatif source. Les dépendances ajoutent notamment CAMERA, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, MODIFY_AUDIO_SETTINGS, RECEIVE_BOOT_COMPLETED, USE_BIOMETRIC/USE_FINGERPRINT et une permission privée de receiver. Les déclarations héritées caméra/projection ne sont pas qualifiées dans le Phone Core : justifier leur usage et les déclarations FGS auprès de Play, ou retirer les déclarations inutilisées dans une variante dédiée avant publication. Elles n’ont pas été supprimées au risque de modifier les modules préservés. Le service VPN WireGuard est bien présent dans le manifeste fusionné et protégé par BIND_VPN_SERVICE.
- FINE/COARSE_LOCATION, Wi-Fi et Bluetooth : modules de découverte existants, demandes contextuelles, finalité et Data Safety à contrôler. WAKE_LOCK et INTERNET/ACCESS_NETWORK_STATE sont conservées.

Références officielles à vérifier lors de la soumission : [application Téléphone par défaut](https://developer.android.com/develop/connectivity/telecom/dialer-app), [politique SMS/Call Log](https://support.google.com/googleplay/android-developer/answer/10208820), [plein écran](https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi), [niveau API cible](https://support.google.com/googleplay/android-developer/answer/11926878).

## Test court sur Galaxy S24+

1. Sauvegarder les données utiles. Activer le débogage USB et vérifier `adb devices -l`. Si une version installée est signée autrement, ne pas la désinstaller automatiquement : la désinstallation effacerait les données.
2. Installer : `adb install -r native-android-app/app/build/outputs/apk/debug/app-debug.apk`. Ouvrir Sentinel ; conserver `adb logcat -v threadtime > s24-phone-core.log` pendant les essais.
3. Centre d’activation : demander Téléphone ; tester annulation, accord, rotation pendant la demande, retour depuis Paramètres. Confirmer avec `adb shell cmd role get-role-holders android.app.role.DIALER`.
4. Avec une seconde ligne réelle, appeler dans les deux sens. Vérifier réponse/rejet/raccrochage, écran verrouillé, arrière-plan, retour par notification, appel en attente et double appel. Tester haut-parleur/Bluetooth/muet et DTMF sur un serveur vocal sans numéro d’urgence.
5. Révoquer puis restaurer CALL_PHONE, READ_PHONE_STATE, READ_CONTACTS, READ_CALL_LOG et notifications depuis Paramètres. Changer de téléphone par défaut. Vérifier absence de crash, état actualisé et absence d’accès interdit. Tester canal bloqué et accès plein écran refusé séparément.
6. Contacts avec/sans permission ; SIM absente et double SIM ; rotation et retour arrière pendant un appel ; quitter le pavé pendant une tonalité. Exporter les journaux sans numéros/noms privés avant partage.
7. Installer le test APK si disponible, puis lancer les contrats :

```bash
adb install -r native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.sentinel.quantum.PhoneCoreManifestContractInstrumentationTest,com.sentinel.quantum.PhoneCoreSetupResumeInstrumentationTest,com.sentinel.quantum.PhoneCoreCallNotificationInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Les tests de notification peuvent être ignorés si les notifications sont refusées : un skip ne vaut pas PASS. Le test crée une notification synthétique « Test Phone Core », puis l’annule ; ne pas l’exécuter pendant un appel réel.

## Anomalies / limites restantes

- Tous les essais S24+, réseau opérateur, lancement sans crash, révocations et accès plein écran sont NON TESTÉS.
- Aucun émulateur ni accès KVM n’est disponible ici ; compilation de l’instrumentation distincte de son exécution.
- Filtres Android de ROLE_DIALER présents ne prouvent pas que One UI accordera le rôle.
- Le diagnostic technique historique de 14 preuves couvre aussi SMS/MMS ; il ne constitue pas une certification commerciale Phone Core V1.
- Le composeur perd sa demande d’appel en mémoire si son processus est tué pendant une autorisation ; aucune relance automatique d’appel après restauration n’est ajoutée. L’utilisateur doit retoucher Appeler.
- Conférence/permutation, SMS/MMS, VPN, BLE, Voice Studio, Security Lab : non qualifiés matériellement dans cette mission.
- Signature release, versionCode libre et acceptation Play non validés ; permissions héritées caméra/projection/FGS et permissions restreintes SMS/Call Log à justifier ou réduire avant publication.

Voir [le rapport d’exécution et ses journaux](evidence/phone-core-v1-20261029/RESULTATS.md) : build complet réussi, 1 353 tests app sans échec ni test ignoré, 65 tests JVM ciblés, APK debug et test APK générés, AAB minifié non signé validé par bundletool. Ces résultats ne remplacent pas les validations physiques.
