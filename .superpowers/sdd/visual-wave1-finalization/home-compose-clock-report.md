# Home Compose clock — compte rendu

## Fichiers modifiés

- `native-android-app/app/src/androidTest/java/com/sentinel/quantum/ui/HomeVisualContractTest.kt` : suppression des deux affectations `composeRule.mainClock.autoAdvance = false`. Toutes les assertions Home sont conservées.
- `native-android-app/app/src/test/java/com/sentinel/quantum/ui/HomeVisualSourceContractTest.kt` : ajout d'une garde de source ciblée qui refuse la désactivation de `mainClock.autoAdvance` dans le test d'instrumentation Home, y compris avec des espaces autour de l'affectation.
- `.superpowers/sdd/visual-wave1-finalization/home-compose-clock-report.md` : présent compte rendu.

## RED

- Garde ajoutée avant le correctif. Vérification directe du même motif sur le HEAD initial : échec (code 1), deux occurrences détectées aux lignes 32 et 54.
- L'exécution JUnit ciblée via `./gradlew :app:testDebugUnitTest --tests 'com.sentinel.quantum.ui.HomeVisualSourceContractTest.homeInstrumentationKeepsComposeClockAutoAdvanceEnabled' --console=plain` s'est arrêtée avant les tests : SDK Android introuvable.

## GREEN

- Même vérification directe après suppression : succès (code 0), aucune occurrence.
- `npm run test:android-product-truth` : 68 tests réussis, 0 échec.
- `git diff --check` : succès.

## Limites

- Le SDK Android n'est pas configuré (`ANDROID_HOME` et `ANDROID_SDK_ROOT` absents). La tâche Gradle ciblée reste bloquée avant compilation et exécution JUnit, également après le correctif. Aucune instrumentation sur émulateur n'a pu être exécutée ici.
- Le GREEN de la nouvelle garde est vérifié par recherche directe du même motif ; son exécution JUnit reste à effectuer dans un environnement Android équipé.
