# Distribution Android — référence actuelle

Ce document remplace l'ancienne documentation « dual distribution ». L'ancien modèle Institutional/Public avec flavors Android, permissions avancées et workflows dédiés n'est pas la configuration Android actuellement maintenue.

## Source Android canonique

Le projet Android maintenu se trouve dans `native-android-app/`.

La configuration actuelle utilise l'application Sentinel `com.sentinel.quantum` et ne doit pas être décrite comme deux flavors indépendants sans preuve dans la configuration source.

## Build et release

- Validation de build : `.github/workflows/build-native-android.yml`.
- Release signée : `.github/workflows/android-release.yml`.
- La release signée est déclenchée par un tag de version `v*`.
- Le workflow vérifie que le commit du tag est atteignable depuis `main`.
- Les secrets de signature sont fournis exclusivement par GitHub Actions.

## Sécurité

Aucun keystore ni aucune clé privée de signature ne doit être versionné. Les permissions Android doivent correspondre aux fonctionnalités réellement implémentées et être minimales.

## Validation

Ne pas utiliser les anciennes affirmations de cette archive concernant des builds Institutional/Public, des permissions d'écoute ou d'enregistrement, des variants Play Store ou des workflows supprimés. Une release n'est considérée comme validée qu'après exécution effective des contrôles concernés et vérification des artefacts.

## Séparation des projets

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ : aucun code, dépendance, configuration, secret ou intégration opérationnelle de A KI PRI SA YÉ ne doit être introduit dans Sentinel.

Pour les procédures actuelles, utiliser `ANDROID_README.md`, `docs/RELEASE_BUILD_GUIDE.md`, `VALIDATION_FINALE.md` et les workflows actifs comme sources de vérité.