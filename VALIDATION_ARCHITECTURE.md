# VALIDATION ARCHITECTURE — ÉTAT FACTUEL

Ce document remplace une ancienne validation de décembre 2025 qui décrivait une architecture différente et déclarait une production readiness sans preuve actuelle.

## Architecture actuellement maintenue

- Surface web/PWA : racine du dépôt.
- Android canonique : `native-android-app/`.
- Contrôle d'isolation : workflows et scripts dédiés, avec séparation stricte du projet Sentinel et des applications externes.
- Gouvernance : contrôles de modèles, données, preuves, décisions et actions.
- Sécurité : fuzzing, audit, intégrité, CodeQL et contrôle de supply chain.

## Validation

Les propriétés de sécurité et de conformité doivent être démontrées par les tests et workflows actuels. Une description documentaire ou une ancienne exécution ne constitue pas une preuve actuelle.

Les assertions historiques de ce fichier concernant un nombre fixe de modules, une arborescence `frontend/`, l'ancien projet `android-app/android/`, des permissions téléphoniques, une conformité juridique « totale » ou un statut « Production Ready » sont retirées de la référence actuelle.

## Android

Le seul projet Android maintenu est `native-android-app/`. Le workflow de build effectue la validation de compilation ; le workflow de release produit la version signée lorsqu'un tag conforme et rattaché à `main` est utilisé.

## Sécurité

Le projet est défensif. Les contrôles de décision et d'action restent soumis aux politiques, à l'intégrité des preuves, au niveau de confiance, à la simulation sûre et, pour les actions critiques, à l'autorisation de la cible et à la validation humaine.

## CI

Un blocage d'exécution des runners GitHub Actions est documenté dans l'issue #195. Il empêche actuellement de considérer l'ensemble de la validation CI comme démontrée tant que les jobs concernés ne parviennent pas à exécuter leurs étapes.

## Séparation des projets

Aucun code, dépendance, configuration, secret ou intégration opérationnelle provenant d'une application externe ne doit être introduit dans Sentinel Quantum Vanguard AI Pro.

## Règle de décision

Ne pas déclarer « validé », « sécurisé », « sans vulnérabilité » ou « prêt pour production » sur la seule base de cette documentation. Utiliser les résultats datés des contrôles exécutés et les artefacts vérifiables comme preuve.
