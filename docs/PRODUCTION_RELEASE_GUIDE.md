# Guide de release — Sentinel Quantum Vanguard AI Pro

Ce document est la référence de procédure pour la release Android signée. La source Android canonique est `native-android-app/`.

## Workflow actif

Le workflow actif est `.github/workflows/android-release.yml`.

Il se déclenche uniquement sur un tag `v*`. Il vérifie le format du tag, sa correspondance exacte avec `versionName` et exige que le commit du tag soit la tête courante de `main` avant toute signature.

Il construit l'application Android avec `assembleRelease`, exige un seul APK, vérifie sa signature et affiche les empreintes publiques du certificat, génère puis revérifie le SHA-256, et place les fichiers dans une GitHub Release en brouillon.

Le job utilise l’environnement GitHub `android-production`. Cet environnement doit exiger une approbation humaine et limiter les déploiements aux tags protégés. La publication publique du brouillon intervient uniquement après les essais sur appareils réels.

## Secrets de signature

Les secrets attendus sont : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`.

Le keystore ne doit jamais être commité. Le workflow le décode temporairement dans `/tmp` avec des permissions restrictives et le supprime après le traitement, y compris en cas d'échec.

## Projet Android canonique

Utiliser exclusivement `native-android-app/`.

La configuration actuelle définit une seule application `com.sentinel.quantum`, avec `minSdk 24`, `targetSdk 36`, `compileSdk 37` et `versionName 1.0.0`. Elle ne définit pas de flavors Public/Institutional.

Le build utilise JDK 17, AGP 9.4.0 et Gradle 9.6 via le wrapper.

## Contrôle local

```text
cd native-android-app
./gradlew assembleDebug
```

Pour une release locale, ne jamais placer de mot de passe ou de clé privée en clair dans les fichiers Gradle ou dans Git.

## Procédure de release

1. préparer et commiter la version sur `main` ;
2. examiner les contrôles disponibles ;
3. créer le tag de version sur un commit de `main` ;
4. pousser le tag et approuver l’environnement protégé ;
5. examiner l'exécution `Android Release APK` ;
6. vérifier l'APK, son SHA-256 et les empreintes du certificat ;
7. tester l'installation et le filtrage sur plusieurs appareils réels ;
8. publier manuellement la GitHub Release restée en brouillon.

Aucun ancien workflow Android ne doit être utilisé comme source de vérité.

## Validation

Un tag, un build lancé ou un artefact présent ne constitue pas à lui seul une preuve de validité.

Règle : `correctif appliqué ≠ testé ≠ CI réussie ≠ release validée ≠ sécurité prouvée`.

## État de validation

Les validations des PRs #386, #390 et #391 ont réellement exécuté et réussi les builds Android et les contrôles de sécurité le 9 septembre 2026. Cela ne prouve pas qu’une release signée a été produite : l’exécution sur tag, l’approbation de l’environnement et les tests physiques restent obligatoires.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement indépendant de tout autre projet. Aucun import, secret, configuration, dépendance ou couplage opérationnel externe n'est autorisé.
