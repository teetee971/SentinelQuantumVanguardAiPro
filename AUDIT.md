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

Le workflow de release est `.github/workflows/android-release.yml`. Il est déclenché par les tags `v*`, exige que le tag pointe exactement sur la tête courante de `main`, utilise les secrets de signature de production dédiés et prépare un APK signé et un AAB signé, chacun accompagné d'un SHA-256 et de preuves de certificat.

Le projet Android actuel utilise `compileSdk 37`, `targetSdk 36`, `minSdk 24`, JDK 17, AGP 9.4.0 et Gradle 9.7.1. La configuration de release refuse toute construction signée sans variables de signature explicites et n'autorise aucun fallback vers une clé debug.

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

Au 18 septembre 2026, les workflows de validation observés sur `main` exécutent effectivement leurs étapes sur runners GitHub. Les passages récents ont validé CI Smoke, Canary, Integrity, Pre-production, Isolation, Security Governance, AI Governance, CodeQL Web/Actions/Android, builds APK/AAB, Pages et Lighthouse sans échec sur les commits contrôlés.

Cette réussite CI ne vaut pas release publique signée : le workflow de tag `.github/workflows/android-release.yml`, les secrets de production, l'approbation de l'environnement `android-production` et les tests sur appareils réels restent des preuves séparées obligatoires.

L'ancien constat de double configuration CodeQL et d'échecs sans preuve détaillée n'est plus utilisé comme état courant ; toute régression future doit être établie à partir du SHA et des runs concernés.

## Supply chain GitHub Actions

Le dépôt contient un contrôle dédié `scripts/check-github-actions-pinning.js`, appelé par le workflow de gouvernance. Il exige une référence SHA de 40 caractères pour chaque action externe rencontrée dans les workflows. Cette politique correspond aux recommandations GitHub de pinner les actions sur un commit SHA complet et de limiter les permissions du `GITHUB_TOKEN`.

Une revue du dépôt montre que les workflows actifs référencent les actions externes avec des SHA complets. Le contrôle automatisé reste la source de vérité et doit être exécuté par CI pour produire une preuve d'exécution actuelle.

## Anciennes PR de dépendances

Les PR #192 et #193, qui ciblaient notamment des arbres Android et frontend supprimés, sont désormais fermées car elles ne correspondent plus à l'arborescence canonique actuelle. Elles ne constituent pas une source de mise à jour pour le dépôt courant.

La PR #216 reste ouverte et non fusionnable. Elle contient des travaux substantiels sur la validation de la frontière d'exécution et l'intégration PostgreSQL anti-rejeu. Elle ne doit pas être fusionnée automatiquement tant que sa divergence avec `main` et ses résultats CI n'ont pas été examinés.

## Conclusion

L'objectif de cette passe est de maintenir une architecture Sentinel cohérente, indépendante et vérifiable, et d'empêcher que la documentation historique soit interprétée comme une validation actuelle. Aucune affirmation de type « tous les tests passent » ou « production-ready » ne doit être conservée sans preuve actuelle.
