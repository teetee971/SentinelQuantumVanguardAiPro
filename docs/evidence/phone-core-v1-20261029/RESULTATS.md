# Résultats exécutés — Phone Core V1

Les lignes ci-dessous distinguent compilation, tests hôte et exécution sur Android. Aucun résultat de test hôte ne vaut validation sur Galaxy S24+.

| Vérification | Résultat prouvé | Journal |
|---|---|---|
| Build Kotlin/debug/releaseUnsigned et test APK | BUILD SUCCESSFUL en 6 min 35 s | gradle-final.log |
| Suite complète app après fourniture de l’APK comme fixture signée | 1 353 tests, 292 suites, 0 échec, 0 erreur, 0 ignoré ; BUILD SUCCESSFUL en 21 s | gradle-unit-with-apk-fixture.log, unit-tests-summary.log |
| AAB releaseUnsigned | bundletool validate réussi ; bundle volontairement non signé ; configuration native PAGE_ALIGNMENT_16K | aab-validation.log, aab-unsigned.log, aab-config.json |
| Bibliothèques natives AAB | 14 ELF 64 bits identiques octet par octet à celles de l’APK vérifié | aab-native-16k.log |
| JVM Kotlin indépendante du SDK | 65 tests JUnit, aucun échec | jvm-tests.log |
| Contrats ciblés rôle/certification et release | 12 tests Node, aucun échec | node-contracts-focused.log |
| Vérité produit Android | 69 tests, aucun échec + contrôles de capacité | android-product-truth.log |
| Politique artefact AAB | 3 tests, aucun échec | aab-policy.log |
| Manifeste source | Contrôle réussi, 24 permissions déclarées | manifest-check.log |
| Manifeste fusionné releaseUnsigned | Contrôle réussi : non-debuggable, pas de backup/HTTP, min24/target36, InCallService protégé ; 32 permissions héritées comprises | release-manifest.log |
| APK debug signature | apksigner vérifie la signature v2, certificat Android Debug | apk-signature.log |
| APK ZIP 16 Kio | zipalign réussit | apk-zipalign.log |
| Bibliothèques ELF 64 bits | 14 bibliothèques, tous segments PT_LOAD alignés au moins sur 16 Kio ; système 16 Kio NON TESTÉ | elf-16k.log |
| Tests Android instrumentés | Compilation APK de test réussie ; exécution NON TESTÉE | gradle-final.log |
| Appareil physique | NON TESTÉ : aucun appareil ADB | adb-devices.log |
| npm test | NON DISPONIBLE : ce dépôt n’a pas de script `test` ; scripts Android nommés exécutés à la place | npm-test.log |

Les premiers essais échoués sont conservés dans l’archive locale des journaux : wrapper Java sans proxy, nom de package SDK incorrect, JDK17 absent, truststore du JDK téléchargé incomplet. Ils ont été traités sans désactiver TLS ni abaisser les versions du projet. Un build intermédiaire a produit l’APK mais échoué à compiler un test ajouté pendant cette compilation ; le build final est relancé sur les sources stabilisées. Le test Node de schéma v5 a échoué après import du schéma v6, puis a été corrigé avec les deux textes de roadmap et relancé.

Les schémas Room générés restent présents et non suivis ; aucune suppression ni transformation de schéma. Voir `room-schemas.sha256`.

Les signatures, alignements et tests hôte ne prouvent ni installation réussie ni absence de crash au lancement. Tous les scénarios réseau, rôles réellement accordés, affichage One UI, permission/révocation et cycle de vie sur appareil restent NON TESTÉS. Voir `docs/PHONE_CORE_V1_HANDOFF_20261029.md` pour les anomalies et commandes exactes.

Le premier passage de la suite complète ignorait ApkSignatureVerifierProofTest.validAndCorruptedApkAreDistinguished faute de fixture. Le second passage fournit l’APK debug effectivement généré et exécute ce test ; aucun test n’est ignoré dans le résultat final. Les avertissements de dépréciation Kotlin/Compose/Gradle et les avertissements VPN déjà présents ne sont pas des erreurs de compilation.

Les empreintes des trois artefacts sont dans `artifacts.sha256`. Binaire debug pour test uniquement ; AAB non publiable en l’état car non signé.
