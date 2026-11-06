# Passage aux essais Android réels

**Statut : BLOCKED. Galaxy S24+ : NOT_TESTED.** Aucun appareil connecté, aucun émulateur installé, pas de /dev/kvm dans cet environnement. Aucun téléchargement d’image système, workflow externe ou installation sur appareil déclenché.

## Binaires prêts

Arbre Android qualifié : `95943bbbe3b03980ece7348ef3d1866eb809f891`. Le SHA de sources de construction des tests est `a94d6d0131e69c0398d6ad1e8e8d4c3118280a09` ; les commits de preuves suivants doivent conserver cet arbre. Les quatre contrôles obligatoires et la suite avec fixture sont relancés sur le SHA final de livraison.

| Artefact | SHA-256 |
| --- | --- |
| app-debug.apk | `2f2ef6e25da6ebe74f098eab5b15e5b5a210ed72edf2c022e0b485969bebff89` |
| app-debug-androidTest.apk | `84e3b6462b15732253818822106d7789262ab4b5aca087ea17ee3ce7437eba3e` |

Les deux APK sont signés en v2 par le même certificat debug : `fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6`. Package applicatif `com.sentinel.quantum`, package de tests `com.sentinel.quantum.test`, minSdk 24, targetSdk 36, runner `androidx.test.runner.AndroidJUnitRunner`. La compilation de l’APK de tests réussit ; cela ne constitue pas une exécution Android réussie. 17 fichiers de tests sont présents, aucun décompte de tests exécutés n’est affirmé.

## Préserver l’installation existante

Utiliser un appareil/profil de test isolé. Avant toute mise à niveau d’une installation existante, comparer son certificat avec celui de l’APK ; si incompatibilité, arrêter et noter BLOCKED, sans désinstallation, pm clear, effacement de contacts ou base Room.

La suite complète n’est pas adaptée à une installation contenant des données à préserver : `PhoneCoreSetupResumeInstrumentationTest` vide les préférences de l’assistant avant/après ; `CommunicationReputationStorageInstrumentationTest` change le mode de protection et nettoie le cache/les clés de réputation. Les tests de notifications publient puis annulent des notifications. Ne pas confondre ces opérations de fixture avec une validation de migration conservant les données.

## Séquence d’essai

1. Relever modèle, API Android, build système, version de l’application et certificat. Confirmer explicitement le modèle Galaxy S24+ avant de qualifier cet appareil. Sélectionner un seul serial ADB avec `-s`.
2. Vérifier les SHA-256 des deux APK. Sur un profil de test, installer les binaires compatibles sans effacer une installation à conserver. Comparer le package et la version installés : versionCode 10, versionName 1.0.9.
3. Exécuter d’abord les contrats de manifeste sélectionnés ci-dessous ; conserver sortie, exit code et nombres de tests. Une sortie contenant une exception ou des tests ignorés doit être classée explicitement, même si la commande shell termine à zéro.
4. Sur le profil isolé, exécuter les autres classes de tests instrumentés et conserver les résultats JUnit. Aucun PASS ne peut être attribué à ces tests avant leur exécution réelle.
5. Vérifier manuellement les réglages : API 24/25 → détails de l’application ; API 26+ → notifications ; API 34+ → plein écran lorsque requis. Tester retour, refus et reprise après arrêt du processus.
6. Appels entrants/sortants, écran verrouillé, proximité, audio/Bluetooth, fin d’appel, double SIM ; puis SMS Unicode/multipart et réception/envoi MMS, miniatures et annulation/révocation des permissions.
7. Vérifier Contacts (recherche/appel), ouverture d’une base Room existante avant/après mise à niveau et conservation des données. Les essais de sauvegarde/transfert OEM doivent rester distincts de la simple ouverture de la base.
8. TalkBack, gros caractères, contraste, rotation et reprise. Documenter les conditions réseau et les prérequis réels ; ne pas transformer un rôle refusé en READY.
9. Compléter une copie du modèle JSON avec les preuves observées ; garder les catégories non exécutées à NOT_TESTED. Ne pas publier de numéros, contenu SMS ou identifiants bruts dans les journaux de qualification.

Commande de sélection des deux contrats de manifeste (ne lance ni installation ni effacement) :

```bash
adb -s "$DEVICE_SERIAL" shell am instrument -w -r \
  -e class com.sentinel.quantum.PhoneCoreManifestContractInstrumentationTest,com.sentinel.quantum.security.MissedCallReceiverManifestInstrumentationTest \
  com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

## Revue des avertissements restants

Les 24 InlinedApi portent sur des constantes Bluetooth, SubscriptionManager, RoleManager et les destinations de réglages ; les opérations de rôle sont gardées selon l’API et les clés de broadcast possèdent des replis. La politique de notifications est couverte par les tests JVM. L’exécution Android/OEM reste une preuve distincte.

Les 8 ApplySharedPref sont des écritures synchrones de clés/identités ou d’état de reprise. Les conversions automatiques en apply() ont été écartées : la durabilité avant changement de rôle et après rotation/nettoyage exige des tests crash/reprise. Certains retours commit() sont ignorés (credentials Mesh, nettoyage, assistant) ; cela reste un risque de robustesse en cas d’échec du stockage, pas une preuve d’ANR ni de perte observée. Les écritures de clés WireGuard vérifient déjà le résultat de persistance. Aucun changement de comportement n’est introduit pendant cette revue.

allowBackup=false et les exclusions root cloud/device-transfer sont présents. Le warning DataExtractionRules et les transferts OEM ne sont pas qualifiés par cette revue statique. Aucun avertissement supprimé pour modifier le décompte lint.

La levée du blocage requiert un appareil/profil réel pour ces contrôles et un accès autorisé aux détails Dependabot (REST 403 dans cette session). Aucun nouveau code applicatif, écran ou dashboard pendant cette étape.
