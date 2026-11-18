# Sentinel IA · révocation globale P0 · 18 novembre 2026

**NO-GO pour la qualification globale.** Le code a des preuves JVM et de compilation Android. L'exécution sur Galaxy S24+ avec deux activités, Keyguard, AndroidKeyStore et le service local reste requise.

- Branche dédiée `fix/sentinel-ai-revocation-barrier-20261118`
- Référence demandée `0bfb6c26f7f9b5086a448949f406d17e0466f9a7`
- Import préservé `cc0ba093fd98a74771869a8bdedc632ffb929fc4`
- Code effectivement qualifié en local `5a6678f5df1dea4c26ee9c1f675cb0e9b225e56e`

Initialement le workspace était vide. La référence a été récupérée depuis la branche distante `fix/sentinel-ai-bounded-pairing-20261117`. Le patch fourni ensuite a pour SHA-256 `b6b2636682262361d57cb057fec33aed54727498460bf5bedd42fa06dab0f16e`. `git apply --check` a réussi avant application, et le diff binaire de l'import a été comparé octet par octet au fichier fourni. L'import constitue un commit distinct dans l'historique.

## Périmètre et corrections

Sur la référence, le verrou tenu pendant l'effet retardait aussi la rotation du bail. L'activité mettait la révocation en file après son nettoyage local, ce qui laissait une fenêtre pour les anciens baux sur les autres écrans. Le patch fourni introduisait deux phases. Il conservait cette attente dans `revokeAll()` et rendait l'acquittement du second demandeur concurrent invalide après celui du premier.

| Fichier | Changement |
| --- | --- |
| `native-android-app/app/src/main/java/com/sentinel/quantum/ai/SentinelAiProcessGate.kt` | Admission et révocation ordonnées par un verrou court. La demande invalide les baux et réveille les exécutions en attente. L'effet admis s'exécute hors verrou. L'acquittement attend sa sortie, y compris sur exception. Les demandeurs d'un même cycle partagent le ticket exact. Le cycle suivant rend ce ticket périmé. |
| `native-android-app/app/src/main/java/com/sentinel/quantum/ai/SentinelAiActivity.kt` | La demande ferme la barrière sur le thread UI avant le nettoyage. Les activités existantes perdent leurs baux, preuves, propositions et réponses éphémères. Les opérations synchrones de journal de session, checkpoint et lecture/écriture d'appariement passent par la barrière. Un worker indépendant attend le drainage. L'échec de l'audit est signalé séparément. Le consentement doit être donné à nouveau après l'acquittement. |
| `native-android-app/app/src/test/java/com/sentinel/quantum/ai/SentinelAiProcessGateTest.kt` | Neuf tests en plus des huit importés. Ils vérifient le refus des exécutions déjà en attente ou soumises pendant le drainage, la priorité de la révocation, les acquittements concurrents, les tickets étrangers ou périmés, la réentrance des deux phases, l'exception d'effet et le drainage interrompu. |
| `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ai/SentinelAiActivityInstrumentationTest.kt` | Une seconde instance consent via sa case. L'autre déclenche son gestionnaire de révocation alors qu'un effet synthétique est retenu. Le test vérifie le bilan des deux écrans, la capture refusée, l'absence d'acquittement prématuré et un nouveau consentement explicite. Il est réservé au paquet de validation isolé. |

L'autorité continue de consommer chaque proposition avant exécution et de traiter un échec d'effet comme un résultat incertain sans relance. Le stockage d'appariement, l'autorité d'approbation, le journal HMAC, AndroidKeyStore, Phone Core, SMS/MMS, VPN, le moteur local et Qwen sont hors du diff. Le processus ne révoque que ses autorisations Android. Il annule les transports enregistrés et jette les résultats des anciens baux. Un appel loopback déjà admis peut avoir atteint le service. Un effet synchrone admis peut se terminer avant l'acquittement. Une révocation peut être confirmée sans reçu HMAC si le journal est indisponible. Les événements administratifs de fin de session peuvent suivre l'événement global. La révocation globale conserve l'appariement mémorisé.

## Autoloop et preuves

| Contrôle | Statut | Exécution |
| --- | --- | --- |
| Tests historiques ciblés sur la référence | PASS | 14 tests de barrière et d'appariement. |
| Sondes de priorité sur la référence | FAIL attendu | Trois assertions rouges. L'ancien bail reste actif, la capture reste autorisée et l'exécution attend le verrou d'effet. |
| Nouveaux tests sur le patch importé | FAIL attendu | Cinq échecs sur 16, avant la correction. |
| Tests ciblés du correctif | PASS | 17 tests de barrière et 7 d'appariement. 30 relances isolées des 17 tests de barrière, 510 exécutions sans échec. |
| Compilation Android Kotlin, application et tests instrumentés | PASS | Gradle 9.8.0, Kotlin 2.4.20, JDK 17, SDK 37.0. |
| JVM Gradle complète | PASS | 1 720 tests dans 358 suites, zéro échec, erreur ou test ignoré. Application 1 650, Wear 27, contrat Wear 6, sécurité Wear 37. |
| Assemblage et inspection des APK debug isolés | PASS | Deux APK debug. Signature v2, paquet isolé ARM64, APK test visant le paquet isolé et runner AndroidJUnit confirmé. |
| Régression du service loopback local | PASS | 22 tests Python. Moteur simulé, processus enfant réel pour la déconnexion. |
| Politique réseau loopback | PASS | 3 tests statiques. |
| Appareils locaux et tests Android physiques | BLOCKED | Aucun appareil listé par `adb devices -l`. |
| Modèle Qwen ARM64 réel et Keyguard sur S24+ | NOT RUN | À exécuter sur l'appareil avec le service local et le modèle approuvé. |
| `git diff --check` et périmètre | PASS | Contrôles sur le workspace et depuis la référence. Seuls les quatre fichiers Kotlin ci-dessus et les deux pièces de preuve ont changé. |

Les deux tentatives de démarrage Gradle ont rencontré un refus réseau Java puis des packages SDK non enregistrés. La distribution Gradle exacte a été téléchargée et vérifiée, le proxy Java configuré et les packages installés via le gestionnaire SDK. La compilation de l'application et celle des tests instrumentés ont été relancées sur le commit de code. Les preuves des cycles précédents n'entrent pas dans ces résultats.

Journaux de compilation, tests rouges et verts, commandes, vérifications Git et sortie ADB dans [REVOCATION_VALIDATION.log](REVOCATION_VALIDATION.log).

## Validation physique Galaxy S24+

1. Partir du SHA de code ci-dessus ou de la branche qui ajoute uniquement ce rapport et son log. Construire les deux APK **debug isolés** depuis `native-android-app` avec la commande suivante. Relever leurs SHA-256, les identifiants de paquets et le certificat. Conserver le certificat de l'installation de validation existante.

   ```sh
   bash gradlew -I ../audit/cycle9/validation/init.gradle :app:assembleDebug :app:assembleDebugAndroidTest
   ```

2. Sélectionner le S24+ avec `ANDROID_SERIAL` pour que les commandes et le script de collecte utilisent le même appareil. Consigner modèle, API, patch Android, rôles Téléphone/SMS et état VPN avant/après via `audit/cycle9/validation/collect_device.sh` dans des répertoires de preuve distincts. Installer uniquement le paquet `com.sentinel.quantum.aivalidation` et son APK de tests avec `adb install -r` et `adb install -t -r`. Un conflit de signature bloque le test. Préserver chaque installation et ses données.
3. Lancer les classes de qualification IA et contrôler à part le nouveau test. Conserver stdout, logcat filtré et code retour. Les phases instrumentées qui attendent des arguments ont leurs commandes dédiées.

   ```sh
   adb shell am instrument -w -e package com.sentinel.quantum.ai \
     com.sentinel.quantum.aivalidation.test/androidx.test.runner.AndroidJUnitRunner
   adb shell am instrument -w \
     -e class 'com.sentinel.quantum.ai.SentinelAiActivityInstrumentationTest#processRevocationBlocksBothActivitiesUntilDrainedAndRequiresNewConsent' \
     com.sentinel.quantum.aivalidation.test/androidx.test.runner.AndroidJUnitRunner
   ```

4. Avec deux instances dans le même processus, révoquer depuis l'une après consentement sur l'autre. Vérifier que les deux cases reviennent décochées, que les commandes sont inactives pendant le drainage et que seul un nouveau consentement ouvre une session. Rejouer un passage arrière-plan puis retour avant l'acquittement. Établir dans la trace quel écran détient le bail pour distinguer l'annulation ordinaire de la révocation globale.
5. Sur le service local et Qwen ARM64 approuvés, envoyer une requête synthétique. Révoquer pendant l'inférence, la demande de verrouillage ou un appariement en attente. Attendre la réponse réelle pour vérifier l'absence de proposition affichée, d'approbation valable ou de sauvegarde tardive après l'acquittement. Vérifier que le secret mémorisé est conservé par la révocation. Tester « Oublier » séparément avec un secret jetable dans le paquet de validation.
6. Utiliser `SentinelAiActionJournalEvidenceTest` pour chaque fenêtre de preuve. La phase `baseline` livre la séquence et l'empreinte de départ. Passer ces valeurs à la phase `verify` via `sentinel_ai_start_sequence` et `sentinel_ai_start_digest`. Attendre zéro checkpoint après une preuve Keyguard ancienne, annulée, refusée ou expirée. Après nouveau consentement et authentification Keyguard réelle, attendre exactement un checkpoint via `sentinel_ai_expected_checkpoints=1`. Comparer les reçus au journal. Suivre le protocole de `SentinelAiPersistenceQualificationTest#journalPersistenceFixtureProtocol` avec `sentinel_ai_restart_phase=prepare`, arrêter le processus, puis lancer `sentinel_ai_restart_phase=verify`. Répéter avec une nouvelle fixture et un redémarrage du S24+ en passant `sentinel_ai_require_reboot=true` à la vérification.
7. Vérifier le refus si le journal du **paquet isolé** est indisponible avec une fixture dédiée. La confirmation doit indiquer l'indisponibilité du journal et les anciens baux demeurent invalides. Conserver les fichiers de preuve et comparer les rôles avant/après.

La décision reste **NO-GO** tant que ces contrôles physiques critiques restent ouverts.
