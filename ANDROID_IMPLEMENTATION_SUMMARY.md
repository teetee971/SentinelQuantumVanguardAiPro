# Sentinel Quantum Vanguard AI Pro — Android Implementation Summary

## Référence actuelle

Ce document décrit l'état réellement maintenu du projet Android. Il ne constitue pas une preuve de build, de release ou de sécurité tant que les contrôles correspondants n'ont pas été exécutés et examinés.

## Architecture

- Projet Android canonique : `native-android-app/`.
- Application : `com.sentinel.quantum`.
- Configuration actuelle : `minSdk 23`, `targetSdk 34`, `versionCode 1`, `versionName 1.0.0`.
- Une seule application est définie ; aucun flavor Public/Institutional n'est actuellement déclaré.
- L'application est un client Android natif Kotlin autour de la surface web Sentinel.

Les anciens chemins `android-app/android/` et les anciens workflows Android ne sont pas des sources d'exécution actuelles.

## Build et release

Le workflow `.github/workflows/build-native-android.yml` est destiné au build de validation et produit un artefact CI.

Le workflow `.github/workflows/android-release.yml` est réservé aux releases signées. Il est déclenché par un tag `v*`, vérifie le format du tag et exige que le commit du tag soit rattaché à `main` avant la signature et la publication.

Les secrets de signature attendus sont : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`.

Le keystore ne doit jamais être stocké dans Git. Le workflow le traite temporairement et le supprime après le traitement.

## Contrôles de sécurité documentés

La documentation actuelle doit uniquement affirmer les protections qui sont présentes dans le code et vérifiables. Les contrôles attendus incluent notamment les restrictions de navigation et de contenu réseau, la limitation des accès fichiers selon la configuration WebView, l'absence de secrets dans le dépôt, la vérification de signature et de SHA-256 avant distribution et le contrôle des permissions réellement déclarées.

Les fonctionnalités supplémentaires envisagées ne sont pas considérées comme implémentées tant qu'elles ne sont pas présentes et testées.

## Validation

La présence d'un fichier APK, d'un workflow ou d'un commit ne constitue pas une validation.

La chaîne de preuve est : `correctif appliqué → tests exécutés → CI exécutée → résultats inspectés → artefact vérifié → validation de sécurité`.

Les formulations telles que « production ready », « 100 % testé », « zéro vulnérabilité » ou « tous les checks passent » sont interdites sans preuve actuelle.

## Blocage CI connu

L'issue #195 documente des échecs de jobs GitHub Actions avant l'exécution des étapes. Tant que ce problème persiste, aucune réussite CI globale ne doit être affirmée et les garde-fous ne doivent pas être affaiblis pour contourner le blocage.

## Documentation de référence

Pour l'état actuel, utiliser en priorité `ANDROID_APK_GUIDE.md`, `ANDROID_PRODUCTION_BUILD_GUIDE.md`, `docs/PRODUCTION_RELEASE_GUIDE.md`, `docs/RELEASE_BUILD_GUIDE.md`, `.github/workflows/build-native-android.yml` et `.github/workflows/android-release.yml`.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel entre les deux projets n'est autorisé.
