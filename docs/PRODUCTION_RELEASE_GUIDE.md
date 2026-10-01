# Guide de release — Sentinel Quantum Vanguard AI Pro

Ce document est la référence de procédure pour la release Android signée. La source Android canonique est `native-android-app/`.

## Workflow actif

Le workflow actif est `.github/workflows/android-release.yml`.

Il se déclenche uniquement sur un tag `v*`. Il vérifie le format du tag, sa correspondance exacte avec `versionName` et exige que le commit du tag soit la tête courante de `main` avant toute signature.

Il construit l'application Android avec `assembleRelease` et `bundleRelease`, exporte le graphe résolu `releaseRuntimeClasspath`, exige un seul APK et un seul AAB signés, vérifie leurs signatures, enregistre les empreintes publiques des certificats, génère puis revérifie les SHA-256, lie l’inventaire natif et le SBOM à `release-evidence.json`, puis place le lot dans une GitHub Release en brouillon.

Le job utilise l’environnement GitHub `android-production`. Cet environnement doit exiger une approbation humaine et limiter les déploiements aux tags protégés. La publication publique du brouillon intervient uniquement après les essais sur appareils réels.

## Secrets de signature

Les secrets attendus sont : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`.

Le keystore ne doit jamais être commité. Le workflow le décode temporairement dans `/tmp` avec des permissions restrictives et le supprime après le traitement, y compris en cas d'échec.

## Projet Android canonique

Utiliser exclusivement `native-android-app/`.

La configuration actuelle définit une seule application `com.sentinel.quantum`, avec `minSdk 24`, `targetSdk 36`, `compileSdk 37`, `versionCode 6` et `versionName 1.0.5`. Elle ne définit pas de flavors Public/Institutional.

Le build utilise JDK 17, AGP 9.4.1, Kotlin Compose 2.4.20 et Gradle 9.8.0 via le wrapper.

## Contrôle local

```text
cd native-android-app
./gradlew assembleDebug
```

Pour une release locale, ne jamais placer de mot de passe ou de clé privée en clair dans les fichiers Gradle ou dans Git.

## Compatibilité des canaux de signature

Play App Signing distingue généralement une clé d’upload, utilisée pour signer l’AAB envoyé à Play, et une clé de signature d’application, utilisée par Google pour signer les APK réellement livrés aux appareils. Le fait que notre APK CI et notre AAB CI utilisent le même certificat prouve la cohérence de notre lot de build, mais ne prouve pas à lui seul la compatibilité avec les APK finalement distribués par Play.

Avant la première publication, choisir explicitement l’une des stratégies suivantes :

1. générer et conserver notre propre clé de signature d’application, puis fournir cette clé à Play App Signing afin que Play et le canal direct utilisent la même identité de signature ; ou
2. laisser Play gérer la clé de signature d’application et, pour toute distribution hors Play, utiliser un APK universel signé par Play plutôt que l’APK CI signé seulement avec la clé d’upload.

Tant que ce choix n’est pas documenté, l’APK produit par la CI reste un artefact candidat de validation et ne doit pas être activé comme téléchargement public.

## Procédure de release

1. préparer et commiter la version sur `main` ;
2. examiner les contrôles disponibles ;
3. créer le tag de version sur un commit de `main` ;
4. pousser le tag et approuver l’environnement protégé ;
5. examiner l'exécution `Android Release` ;
6. vérifier l'APK et l'AAB signés, leurs SHA-256, les empreintes de certificat, le SBOM et `release-dependencies.json` ;
7. exécuter `verify-android-release-evidence.js` sur le lot conservé ;
8. tester l'installation et le Phone Core sur plusieurs appareils/opérateurs réels ;
9. vérifier la stratégie Play App Signing et soumettre l’AAB à Play Console ;
10. publier manuellement la GitHub Release restée en brouillon seulement après ces preuves.

Après téléchargement du brouillon, exécuter également :

```text
node scripts/verify-android-release-evidence.js --root /chemin/du-lot --evidence release-evidence.json
```

Le résultat doit être `verified: true` et reprendre le commit, le tag, les SHA-256 de l’APK et de l’AAB, les empreintes publiques de certificat attendues et un inventaire natif `releaseRuntimeClasspath` dont le hash, la taille, les composants et les relations ont été vérifiés.

Aucun ancien workflow Android ne doit être utilisé comme source de vérité.

## Validation

Un tag, un build lancé ou un artefact présent ne constitue pas à lui seul une preuve de validité.

Règle : `correctif appliqué ≠ testé ≠ CI réussie ≠ release validée ≠ sécurité prouvée`.

## État de validation

Les builds de validation CI exécutent les tests unitaires, lint, la construction APK/AAB et un smoke test d’installation/lancement. Cela ne prouve pas qu’une release signée de production a été produite : l’exécution sur tag, l’approbation de l’environnement, les secrets de signature, la preuve de release conservée, Play Console et les tests physiques restent obligatoires.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement indépendant de tout autre projet. Aucun import, secret, configuration, dépendance ou couplage opérationnel externe n'est autorisé.
