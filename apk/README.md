# APK — Sentinel Quantum Vanguard AI Pro

Ce répertoire documente la distribution APK. Il ne constitue pas la source de build.

## Source Android canonique

Le projet Android maintenu se trouve dans `native-android-app/`.

Les anciens chemins `android-app/android/`, les anciens workflows de publication automatique et GitHub Pages sont historiques et ne doivent plus être utilisés.

## Build et release

Le build Android de validation est défini dans `.github/workflows/build-native-android.yml` et produit un artefact de build.

La release signée est définie dans `.github/workflows/android-release.yml`. Elle est déclenchée par un tag de version `v*`, avec contrôle que le tag appartient à l’historique de `main`.

Les secrets de signature attendus par le workflow sont : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`. Aucun keystore ni secret ne doit être commité.

## Vérification

Avant de considérer un APK comme validé, vérifier le résultat du workflow, l’artefact produit et son checksum. Un fichier présent ou un build lancé ne constitue pas à lui seul une preuve de sécurité ou de validité de release.

## Historique

Les instructions anciennes relatives à `android-app/android/`, à GitHub Pages ou à un workflow `build-and-publish-apk.yml` sont obsolètes.
