# Preuves de tests · Sentinel Phone Core V1

**V1 COMMERCIAL NO-GO.** Les preuves locales ci-dessous concernent le candidat `25c4e2c9224abe8feaf07474a2d092ea657330f5`. La recette Galaxy S24+, la signature de livraison et l’acceptation Play restent des preuves distinctes.

## Reproductions et itérations

| Passe | Résultat |
| --- | --- |
| Bouton Raccrocher, lecture MMS suivant un rappel refusé ou révoqué, cache de contacts, sorties optionnelles | FAIL attendu sur les contrôles de reproduction. Les rapports `call-*`, `mms-*`, `directory-*`, `ui-scope-*` conservent les sorties. |
| Réponse restrictive calculée avant retrait du rôle de filtrage | FAIL attendu sur le nouveau contrat, puis 6 contrôles de source réussis après la réévaluation du rôle. |
| Premier Gradle debug | FAIL avec un contrat de comptage de réponses et 9 erreurs lint. La nouvelle branche de réponse neutre reste couverte par le contrat une-réponse. |
| Remédiation lint | Les expéditeurs utilisent la lecture SIM déjà protégée et recontrôlent l’autorité avant et après. Les chaînes Compose proviennent de `stringResource`. Trois alertes ciblées concernaient des marqueurs de suppression de manifeste. L’absence des éléments concernés est recontrôlée dans les manifestes fusionnés et l’artefact. |
| Contrats refactorisés | La garde d’envoi est contrôlée dans le scope du ViewModel retenu. Le contrat MMS vérifie la reprise centralisée et le maintien des fichiers RETRY/DIAGNOSE. Les assertions d’ordre et de rétention restent actives. |

## Résultats sur l’instantané de code

| Contrôle | Résultat |
| --- | --- |
| JVM Android et Wear | PASS · 1 734 tests sur 360 suites (1 664 app, 27 Wear, 6 contrats et 37 sécurité Wear), zéro échec, erreur ou test sauté. |
| Android Kotlin et instrumentation | PASS · compilation Android Kotlin, `compileDebugAndroidTestKotlin` et `assembleDebugAndroidTest`. Exécution sur téléphone BLOCKED. |
| Node Phone Intelligence et contrats Android, produit, hygiène | PASS · 298 tests au total dans les suites comptées, zéro échec ni test sauté. Contrôles manifestes et promesses publiques réussis. |
| JVM · profil historique (`-PsentinelCommercialV1=false`) | PASS · 1 734 tests sur 360 suites, zéro échec, erreur ou test sauté. Le build a réutilisé les tâches Wear indépendantes du profil. |
| Lint debug | PASS · `lintDebug`, 0 erreur, 246 avertissements et 19 indications. `lintVitalReleaseUnsigned` a aussi réussi lors du build. |
| APK debug ARM64 et APK de tests | PASS · CRC, portée du manifeste, ABI ARM64, signatures APK v2 et certificat commun. |
| APK/AAB releaseUnsigned | PASS · assemblage, CRC, manifestes, validation bundletool et alignement 16 Kio. Variante d’inspection sans signature. |
| Dépendances Android de la variante d’inspection | PASS · graphe `releaseUnsignedRuntimeClasspath` résolu. OSV n’a retourné aucune correspondance sur les 189 coordonnées Maven interrogées. Le résultat dépend de la couverture du catalogue OSV. |
| Alertes Dependabot du dépôt | BLOCKED · GitHub indique six alertes sur `main`, dont une élevée. L’API de détails a répondu HTTP 403. Leur applicabilité au candidat reste à établir. |
| Signature de livraison | BLOCKED · `:app:bundleRelease` a refusé de continuer faute de configuration de signature. Voir `release-signing-gate.log`. |
| Installation et tests Android sur S24+ | BLOCKED · `adb devices -l` vide. Les sondes JVM et les fixtures de la mission servent aux régressions locales. |
| Qwen, Wi-Fi/Bluetooth et distribution Play sur appareil | NOT RUN. |
| Procédure S24+ et documentation | PASS · liens locaux, syntaxe des commandes Bash et prévol des empreintes, du manifeste de recette et du certificat contrôlés sans appareil. |

Les sorties de tests des sous-ensembles se recoupent avec les suites complètes. Les 6 sondes de raccrochage reposent sur des méthodes du service compilées avec des fixtures Telecom. Les 43 tests MMS, 21 tests de politiques de confidentialité et 15 tests ciblés d’envoi couvrent les chemins JVM. Les scénarios opérateur et les permissions réellement accordées par Android restent ouverts.

## Reproduction locale

```sh
cd native-android-app
bash gradlew -PsentinelCommercialV1=true --no-daemon --max-workers=2 \
  :app:testDebugUnitTest :wear-app:testDebugUnitTest :wearable-contract:test :wearable-security:test \
  :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:assembleReleaseUnsigned :app:bundleReleaseUnsigned
bash gradlew -PsentinelCommercialV1=true --no-daemon :app:lintDebug
bash gradlew -PsentinelCommercialV1=true --no-daemon :app:dependencies \
  --configuration releaseUnsignedRuntimeClasspath
bash gradlew -PsentinelCommercialV1=false --no-daemon --max-workers=2 \
  :app:testDebugUnitTest :wear-app:testDebugUnitTest :wearable-contract:test :wearable-security:test
```

Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20, JDK 17, Android SDK 37 et Build-Tools 36.0.0. Dossier de preuve `v1-evidence` dans le workspace. Les journaux bruts de compilation, tests, contrôles, erreurs observées et vérifications d’APK accompagnent l’archive privée. Les SHA-256 identifient les sorties de cette exécution. La reproductibilité bit à bit sur un second environnement reste NOT RUN.

[Remédiations](P0_REMEDIATION.md) · [Recette physique](S24_PHYSICAL_ACCEPTANCE.md) · [Décision release](RELEASE_READINESS.md)
