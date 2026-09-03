# AUDIT — Sentinel Quantum Vanguard AI Pro

## Statut documentaire

Ce document remplace les anciens audits datés qui décrivaient une architecture ou des workflows désormais supprimés. Il ne constitue pas une preuve de réussite CI tant que les runners GitHub Actions n'exécutent pas réellement les étapes.

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

Le seul projet Android maintenu est `native-android-app/`.

## CI — état réel

Un incident d'infrastructure GitHub Actions a fait échouer plusieurs jobs avant leur première étape. Un essai avec une image Ubuntu explicitement versionnée n'a pas changé le comportement. Cela ne permet pas de conclure à un échec des tests du logiciel : les tests n'ont pas démarré.

État : **CI bloquée / validation en attente**.

Principe de preuve :

`corrigé` ≠ `testé localement` ≠ `testé par CI` ≠ `sécurité validée`.

## Anciennes PR de dépendances

Les PR #192 et #193, qui ciblaient notamment les anciens répertoires `android-app/` et `frontend-mvp/`, sont désormais fermées car elles ne correspondent plus à l'arborescence canonique actuelle. Elles ne constituent pas une source de mise à jour pour le dépôt courant.

## Conclusion

L'objectif de cette passe est de maintenir une architecture Sentinel cohérente, indépendante et vérifiable, et d'empêcher que la documentation historique soit interprétée comme une validation actuelle. Aucune affirmation de type « tous les tests passent » ou « production-ready » ne doit être conservée sans preuve actuelle.
