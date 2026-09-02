# Audit de souveraineté numérique — état courant

**Révision :** 2 septembre 2026

Ce document est un cadre d'audit technique. Il ne constitue pas une certification juridique, réglementaire ou de sécurité indépendante.

## Infrastructure
Le dépôt public est hébergé sur GitHub et la surface web cible Cloudflare Pages. Ces fournisseurs constituent des dépendances d'infrastructure externes ; l'usage de ces services ne doit pas être présenté comme une souveraineté absolue.

## CI/CD
GitHub Actions est utilisé pour les contrôles et builds. Les workflows actifs sont ceux présents dans `.github/workflows/` sur `main`. Les Actions tierces sont soumises au pinning par SHA lorsque le contrôle de gouvernance l'exige.

La validation CI complète n'est actuellement pas certifiée : plusieurs jobs GitHub-hosted ont échoué avant l'exécution de leurs étapes. Ce blocage est suivi dans l'issue #195.

## Android
La source Android canonique est `native-android-app/`. La release signée est contrôlée par workflow et secrets GitHub. Aucun keystore ou secret de signature ne doit être stocké dans le dépôt.

## Données et confidentialité
Toute affirmation « aucune collecte », « aucun appel réseau » ou équivalent doit être vérifiée sur le code et l'environnement d'exécution concernés. Elle ne doit pas être déduite d'un ancien rapport.

## Dépendances
Les dépendances de build et d'exécution doivent être inventoriées depuis les manifests et lockfiles actuels. Un score de risque global ou un « niveau zéro » n'est pas justifié sans analyse reproductible et à jour.

## Distribution
Les mécanismes de distribution doivent être décrits selon les workflows et artefacts réellement présents. Les anciens noms de release et anciens chemins APK sont historiques.

## Séparation des projets
Sentinel Quantum Vanguard AI Pro doit rester totalement séparé de **A KI PRI SA YÉ**. Aucun couplage de code, dépendance, secret, configuration ou déploiement croisé n'est autorisé.

## Verdict actuel
**Souveraineté technique : non certifiée comme propriété absolue.** Le dépôt présente des mécanismes d'auditabilité et de contrôle, mais la validation doit rester fondée sur les preuves actuelles et ne peut ignorer les dépendances GitHub/Cloudflare ni le blocage CI en cours.
