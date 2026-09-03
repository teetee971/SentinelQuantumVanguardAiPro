# Guide de release — Sentinel Quantum Vanguard AI Pro

Ce document est la référence de procédure pour la release Android signée. La source Android canonique est `native-android-app/`.

## Workflow actif

Le workflow actif est `.github/workflows/android-release.yml`.

Il se déclenche uniquement sur un tag `v*`. Il vérifie le format du tag et exige que le commit du tag soit accessible depuis `main` avant toute signature ou publication.

Il construit l'application Android avec `assembleRelease`, vérifie les APK produits, génère un SHA-256 pour chaque APK, puis publie les fichiers dans une GitHub Release.

## Secrets de signature

Les secrets attendus sont : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`.

Le keystore ne doit jamais être commité. Le workflow le décode temporairement dans `/tmp` avec des permissions restrictives et le supprime après le traitement, y compris en cas d'échec.

## Projet Android canonique

Utiliser exclusivement `native-android-app/`.

La configuration actuelle définit une seule application `com.sentinel.quantum`, avec `minSdk 23`, `targetSdk 36`, `compileSdk 37` et `versionName 1.0.0`. Elle ne définit pas de flavors Public/Institutional.

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
4. pousser le tag ;
5. examiner l'exécution `Android Release APK` ;
6. vérifier l'APK et son SHA-256 ;
7. vérifier la signature ;
8. tester l'installation avant distribution.

Aucun ancien workflow Android ne doit être utilisé comme source de vérité.

## Validation

Un tag, un build lancé ou un artefact présent ne constitue pas à lui seul une preuve de validité.

Règle : `correctif appliqué ≠ testé ≠ CI réussie ≠ release validée ≠ sécurité prouvée`.

## Blocage CI connu

L'issue #195 documente des échecs de certains jobs GitHub Actions avant l'exécution de leurs étapes. Tant que ce blocage persiste, aucune réussite CI globale ne doit être affirmée. Les contrôles de sécurité ne doivent pas être supprimés ou affaiblis pour contourner le problème.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement indépendant de tout autre projet. Aucun import, secret, configuration, dépendance ou couplage opérationnel externe n'est autorisé.
