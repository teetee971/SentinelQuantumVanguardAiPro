# Sentinel Quantum Vanguard AI Pro — Architecture de référence

> Document de référence révisé. Les anciennes descriptions de l’architecture ne constituent pas une preuve de l’état actuel du dépôt.

## Architecture actuelle

- Surface web/PWA : racine du dépôt et `frontend/` selon l’arborescence réellement présente.
- Android natif : `native-android-app/` uniquement.
- Sécurité et gouvernance : `decision-plane/`, `security/` et `scripts/`.
- CI/CD : `.github/workflows/`.
- Déploiement web cible : Cloudflare Pages.

## Principes non négociables

1. Défensif uniquement : audit, détection, monitoring et simulation autorisée.
2. Moindre privilège et absence de secret dans le dépôt.
3. Les actions critiques restent soumises aux garde-fous, à l’autorisation de cible et à la validation humaine.
4. Les preuves, leur provenance et leur intégrité doivent être vérifiables.
5. Aucune capacité offensive, d’interception clandestine ou de contournement.
6. Sentinel Quantum Vanguard AI Pro est totalement séparé de A KI PRI SA YÉ : aucune dépendance, configuration, import, secret ou couplage opérationnel n’est autorisé.

## Contrôles automatisés

Le dépôt maintient notamment :

- contrôle d’isolation du projet ;
- contrôle de pinning SHA des actions GitHub externes ;
- gouvernance des modèles et des données ;
- intégrité et provenance des preuves ;
- simulation avant action ;
- garde-fou des actions critiques ;
- fuzzing déterministe en laboratoire autorisé ;
- contrôles d’intégrité et CodeQL.

## Android

`native-android-app/` est l’unique source Android canonique. Les références historiques à `android-app/android/` ne doivent plus être utilisées pour construire ou publier l’application.

Le build de validation est défini par `.github/workflows/build-native-android.yml`. La release signée est définie par `.github/workflows/android-release.yml` et repose sur des tags de version rattachés à `main`.

## Validation et preuve

Un commit n’est pas une validation. La chaîne de preuve est : correctif appliqué → tests exécutés → CI exécutée → résultats examinés → validation de sécurité.

Les anciens termes « Production Ready », « 100 % testé », « zéro vulnérabilité » ou « conformité certifiée » ne doivent pas être repris sans preuve actuelle.

À la dernière vérification, des jobs GitHub Actions échouaient avant l’exécution de leurs étapes. Ce blocage est traité comme un problème d’exécution CI/infrastructure et ne doit pas être présenté comme un résultat de test du code.

## Documentation historique

Les exemples de Kotlin, de chemins, de permissions, de workflows ou de fonctionnalités figurant dans les anciennes versions de ce document peuvent être obsolètes. Pour toute instruction opérationnelle, consulter les fichiers de référence actuels du dépôt.

## Références actuelles

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `RELEASE_STATUS.md`
- `VALIDATION_FINALE.md`
- `docs/WORKFLOWS.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `SECURITY.md`
