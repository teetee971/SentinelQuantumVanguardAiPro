# AUDIT — Sentinel Quantum Vanguard AI Pro

## Statut documentaire

Ce document remplace les anciens audits datés qui décrivaient une architecture ou des workflows désormais supprimés. Il ne constitue une preuve de réussite CI que pour les exécutions explicitement observées et documentées.

## Architecture actuelle

- Web/PWA à la racine, destiné à Cloudflare Pages.
- Projet Android canonique : `native-android-app/`.
- Les anciens répertoires Android et workflows de release supprimés ne sont plus des sources de vérité.
- Sentinel est autonome et ne doit introduire aucune dépendance opérationnelle Firebase ou provenant d'un autre projet.

## Contrôles de sécurité présents

- Isolation de projet : `scripts/check-sentinel-isolation.js`.
- Pinning des GitHub Actions : `scripts/check-github-actions-pinning.js`.
- Gouvernance IA et registre de modèles.
- Provenance et intégrité des preuves.
- Moteur de confiance et gestion de l'incertitude.
- Simulation d'impact avant action.
- Action gate avec autorisation de cible et validation humaine pour les actions critiques.
- Audit immuable.
- Red-team synthétique et fuzzing de gouvernance.
- CodeQL et contrôles d'intégrité du dépôt.

## Isolation

Le scanner d'isolation couvre notamment les fichiers texte critiques, les dépendances Firebase, les imports/require dynamiques ou statiques, les identifiants de projets externes interdits, les fichiers de configuration Firebase interdits et les éléments Android Firebase incompatibles. Il applique également des limites de profondeur, de nombre de fichiers, de nombre total d'entrées et de taille de fichier, et échoue fermé sur les liens symboliques.

Le contrôle de séparation est donc une barrière automatisée ; son exécution CI reste à distinguer de son existence dans le dépôt.

## Android

Le workflow de build non publié est `.github/workflows/build-native-android.yml`.

Le workflow de release est `.github/workflows/android-release.yml`. Il est déclenché par les tags `v*`, vérifie que le tag pointe sur un commit atteignable depuis `main`, utilise les secrets de signature de production dédiés et publie l'APK accompagné d'un SHA-256.

Le projet Android actuel utilise `compileSdk 37`, `targetSdk 36`, `minSdk 23`, JDK 17, AGP 9.4.0 et Gradle 9.6. La configuration de release refuse toute construction signée sans variables de signature explicites et n'autorise aucun fallback vers une clé debug.

Le seul projet Android maintenu est `native-android-app/`.

## Nettoyage réalisé

- Suppression du document obsolète de conformité du module téléphone, qui décrivait des permissions et fonctionnalités absentes du code actuel.
- Suppression des anciens fichiers de déclenchement et de documentation devenus sans fonction opérationnelle.
- Nettoyage du workflow d'isolation : retrait des contrôles visant des fichiers supprimés et conservation des barrières de séparation réellement exécutables.
- Mise à niveau du socle Android vers les versions actuellement retenues par le projet.
- Fermeture des PR de diagnostic CI #217 et #218 après intégration des corrections utiles dans `main`.
- Correction du smoke test CI afin qu'il vérifie réellement le contenu du dépôt après checkout.
- Simplification du workflow CodeQL avancé : suppression de l'étape `autobuild`, inutile pour les langages web/Actions actuellement ciblés.

## CI — état réel

Les runners GitHub Actions exécutent désormais effectivement des jobs. Sur le commit `08b9518ab1216efb3a873cf6423f78ec90dd3512`, l'exécution observée par l'API GitHub inclut le workflow automatique `Push on main` / CodeQL, mais les trois jobs CodeQL observés (`actions`, `javascript-typescript`, `java-kotlin`) ont échoué. Les logs détaillés de ces jobs ne sont pas récupérables via l'interface actuelle ; aucune réussite CodeQL n'est donc revendiquée.

Le dépôt contient également un workflow avancé `codeql-analysis.yml` qui limite explicitement CodeQL à JavaScript/TypeScript et GitHub Actions. Ce workflow a été simplifié dans le commit `8b3e7f0c8f166d42343394cfe95af945b2b56b33` afin de ne plus lancer d'`autobuild` inutile pour ces deux langages.

La présence d'une exécution automatique distincte indique qu'une configuration CodeQL Default Setup est également active sur le dépôt. Cette configuration automatique doit être reconfigurée dans GitHub afin d'éviter la double analyse et de sélectionner explicitement les langages/build modes voulus. Cette opération relève des paramètres de sécurité du dépôt et n'est pas simulée par une modification de fichier.

Le smoke test `CI Smoke` a été corrigé pour effectuer un checkout avec une action `actions/checkout` épinglée sur un SHA complet avant ses contrôles de fichiers. L'existence du commit de correction est vérifiée dans l'historique ; la réussite de son job doit encore être constatée dans une exécution CI dédiée.

État : **runners opérationnels ; validation logicielle complète non encore prouvée ; configuration CodeQL automatique à corriger**.

## Supply chain GitHub Actions

Le dépôt contient un contrôle dédié `scripts/check-github-actions-pinning.js`, appelé par le workflow de gouvernance. Il exige une référence SHA de 40 caractères pour chaque action externe rencontrée dans les workflows. Cette politique correspond aux recommandations GitHub de pinner les actions sur un commit SHA complet et de limiter les permissions du `GITHUB_TOKEN`.

Une revue du dépôt montre que les workflows actifs référencent les actions externes avec des SHA complets. Le contrôle automatisé reste la source de vérité et doit être exécuté par CI pour produire une preuve d'exécution actuelle.

## Anciennes PR de dépendances

Les PR #192 et #193, qui ciblaient notamment des arbres Android et frontend supprimés, sont désormais fermées car elles ne correspondent plus à l'arborescence canonique actuelle. Elles ne constituent pas une source de mise à jour pour le dépôt courant.

La PR #216 reste ouverte et non fusionnable. Elle contient des travaux substantiels sur la validation de la frontière d'exécution et l'intégration PostgreSQL anti-rejeu. Elle ne doit pas être fusionnée automatiquement tant que sa divergence avec `main` et ses résultats CI n'ont pas été examinés.

## Conclusion

L'objectif de cette passe est de maintenir une architecture Sentinel cohérente, indépendante et vérifiable, et d'empêcher que la documentation historique soit interprétée comme une validation actuelle. Aucune affirmation de type « tous les tests passent » ou « production-ready » ne doit être conservée sans preuve actuelle.
