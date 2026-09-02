# Audit des workflows GitHub Actions — état courant

**Révision :** 2 septembre 2026

Ce rapport remplace l'ancien audit de décembre 2025. Les anciens noms de workflows, les anciens chemins Android et les anciens résultats de validation ne constituent pas l'état actuel.

## Source de vérité
La référence est `.github/workflows/` sur `main`. Les fichiers réellement présents et leur configuration priment sur tout rapport historique.

## Workflows actuellement suivis
- `project-isolation.yml`
- `sentinel-isolation.yml`
- `security-governance.yml`
- `security-fuzz.yml`
- `integrity-check.yml`
- `codeql-analysis.yml`
- `defender-for-devops.yml`
- `frontend-validation.yml`
- `build-native-android.yml`
- `android-release.yml`

La liste exacte peut évoluer ; elle doit être vérifiée directement dans le dépôt avant toute certification.

## Contrôles de sécurité
Les Actions tierces utilisées dans les workflows actifs sont soumises au contrôle de pinning par SHA. Les permissions doivent rester explicites et minimales. Le workflow de release Android est séparé du build debug.

## Android
La source canonique est `native-android-app/`. Les anciens chemins `android-app/` et anciens workflows Android sont historiques.

## Isolation
Sentinel Quantum Vanguard AI Pro et **A KI PRI SA YÉ** constituent deux projets distincts. Aucun import, secret, dépendance, configuration ou déploiement croisé n'est autorisé. Les contrôles d'isolation automatisés font partie de la chaîne de validation.

## État CI
La dernière phase d'audit a constaté des jobs GitHub-hosted échouant avant l'exécution des étapes, avec des jobs présentant `steps: []`. Des relances et un changement de runner n'ont pas supprimé le symptôme.

Il est donc incorrect de conclure que les tests sont verts ou que la sécurité est entièrement validée. Le blocage est suivi dans l'issue #195. Aucun contrôle de sécurité ne doit être affaibli pour contourner ce problème.

## Règle de preuve
Un contrôle n'est déclaré validé que si son exécution réelle et son résultat sont observés. Les rapports historiques servent uniquement à la traçabilité.

**Statut actuel : validation CI complète en attente de résolution du blocage d'exécution des runners.**
