# Home Compose clock — compte rendu

## Fichiers modifiés après rebase

- Le parent `ba6cb321` a déjà supprimé les deux affectations `composeRule.mainClock.autoAdvance = false` de `HomeVisualContractTest.kt`. Le commit rebasé `825686ef` ne modifie pas ce fichier. Les assertions Home y restent intactes.
- `native-android-app/app/src/test/java/com/sentinel/quantum/ui/HomeVisualSourceContractTest.kt` : ajout, puis renforcement, d'une garde de source ciblée sur le test d'instrumentation Home.
- `.superpowers/sdd/visual-wave1-finalization/home-compose-clock-report.md` : présent compte rendu.

## RED

- Avant le rebase, garde ajoutée puis vérification directe du même motif sur le HEAD initial `05968f1d` : échec (code 1), deux occurrences détectées aux lignes 32 et 54. Cette suppression appartient désormais au parent `ba6cb321`.
- L'exécution JUnit ciblée via `./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.HomeVisualSourceContractTest.homeInstrumentationKeepsComposeClockAutoAdvanceEnabled' --console=plain` s'est arrêtée avant les tests : SDK Android introuvable.

## GREEN

- Même vérification directe après suppression : succès (code 0), aucune occurrence.
- `npm run test:android-product-truth` : 68 tests réussis, 0 échec.
- `git diff --check` : succès.

## Limites

- Le SDK Android n'est pas configuré (`ANDROID_HOME` et `ANDROID_SDK_ROOT` absents). La tâche Gradle ciblée reste bloquée avant compilation. Aucune instrumentation sur émulateur n'a pu être exécutée ici.
- Le contrôle JUnit a ensuite été compilé et exécuté isolément avec le compilateur Kotlin présent dans la distribution Gradle et JUnit 4, sans SDK Android (voir ci-dessous). La tâche Gradle habituelle reste à exécuter dans un environnement Android équipé.

## Fix round 1

- Garde renforcée : masque les commentaires Kotlin (dont les blocs imbriqués), les chaînes et les caractères avant de chercher l'affectation ; réduit `false` entre parenthèses, même imbriquées, à un littéral vérifiable. Les cas couvrent également espaces et retours à la ligne.
- RED JUnit avec l'ancien motif réintroduit temporairement : 9 tests exécutés, 2 échecs attendus (`clockGuardRejectsParenthesizedAndCommentSeparatedFalse`, `clockGuardIgnoresCommentsAndStringLiterals`). L'ancien motif manque `(false)` et signale à tort un commentaire.
- GREEN JUnit après restauration de la garde renforcée : 9/9 tests `HomeVisualSourceContractTest` réussis, y compris les nouveaux cas. Compilation Kotlin JVM isolée, sans modification de production.
- `npm run test:android-product-truth` : 68/68 réussis ; `git diff --check` : succès.
- Portée : il s'agit d'une garde de source pour les affectations littérales à `false`, avec parenthèses et commentaires. Elle ne prouve pas le résultat d'expressions Kotlin arbitraires ; l'instrumentation Android demeure non vérifiée localement.
