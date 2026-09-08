# Feuille de route réaliste — Sentinel Quantum Vanguard AI Pro

**Révision :** 7 septembre 2026  
**Statut :** feuille de route technique, sans promesse de date

## Priorité 1 — Stabilisation et preuve

Objectif : disposer d'un dépôt cohérent et vérifiable avant toute nouvelle capacité.

- supprimer les anciens arbres et artefacts qui ne sont plus des sources de vérité ;
- maintenir `native-android-app/` comme unique source Android ;
- maintenir une séparation stricte avec les autres projets ;
- conserver le pinning SHA des GitHub Actions ;
- exécuter les tests de gouvernance, d'isolation et de fuzzing ;
- rétablir l'exécution normale des runners GitHub Actions ;
- examiner les résultats CI avant toute déclaration de validation ;
- **(fait)** remplacer le workflow `defender-for-devops.yml` défaillant par un placeholder `workflow_dispatch` sans action externe ;
- **(fait)** nettoyer les configurations de code scanning MSDO obsolètes et ne conserver que CodeQL actif.

## Priorité 2 — Qualité du produit

- vérifier le build web/PWA réel ;
- vérifier les références et ressources chargées par l'interface ;
- éliminer les chemins morts et configurations orphelines ;
- vérifier l'interface mobile et les comportements responsive ;
- maintenir une documentation courte, factuelle et synchronisée avec le dépôt.

## Priorité 3 — Android

- maintenir exclusivement `native-android-app/` ;
- vérifier manifeste, permissions, configuration réseau et composants exportés ;
- produire un build de validation reproductible ;
- vérifier signature et checksum des releases ;
- ne jamais stocker de keystore ou de secret dans Git.

## Priorité 4 — Sécurité et gouvernance

- poursuivre le fuzzing synthétique dans un environnement autorisé ;
- renforcer les contrôles de provenance et d'intégrité ;
- conserver les garde-fous sur les actions critiques ;
- conserver l'autorisation de cible et la validation humaine lorsque requises ;
- empêcher qu'un composant IA puisse contourner les contrôles ou exécuter directement une action sensible ;
- surveiller les régressions de dépendances et de workflows.

## Blocage connu

La validation CI complète restait conditionnée au rétablissement des runners GitHub-hosted. Ce blocage est maintenant levé : les runners exécutent les workflows et le workflow MSDO a été remplacé par un placeholder manuel stable. Reste à surveiller les prochaines exécutions complètes avant d'étendre le périmètre.

## Hors périmètre

Ne pas réintroduire :

- anciens arbres Android ou frontend ;
- anciens pipelines de release supprimés ;
- artefacts APK committés comme preuve de build ;
- secrets, keystores ou configurations privées ;
- dépendances opérationnelles provenant d'un autre projet ;
- affirmations de conformité, de sécurité absolue ou de production sans preuve actuelle.

## Critère de progression

Une étape est considérée comme terminée uniquement lorsque le code correspondant existe, que les contrôles pertinents ont été exécutés et que les résultats sont disponibles et examinés.

**Principe :** stabiliser → tester → observer les preuves → corriger → seulement ensuite étendre le périmètre.
