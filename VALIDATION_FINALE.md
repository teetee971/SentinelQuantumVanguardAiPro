# Validation finale — Sentinel Quantum Vanguard AI Pro

## Statut

Ce document remplace une ancienne validation Android datée de 2024 qui ne constitue plus une preuve actuelle.

## Référentiel actuel

- Android canonique : `native-android-app/`
- Build de validation : `.github/workflows/build-native-android.yml`
- Release signée : `.github/workflows/android-release.yml`
- Isolation : `scripts/check-sentinel-isolation.js`
- Gouvernance sécurité : `security/` et `decision-plane/`

## Règle de preuve

Un composant n’est pas « validé » parce qu’un fichier existe ou qu’un ancien test a été documenté. La validation actuelle exige l’exécution du test, l’observation du résultat CI et l’examen des artefacts lorsque cela s’applique.

## État CI

Des jobs GitHub Actions ont récemment échoué avant l’exécution de leurs étapes. Tant que ce blocage persiste, aucune conclusion « CI verte » ou « sécurité validée par CI » ne doit être portée dans la documentation.

## Android

Les instructions utilisant `android-app/android/`, `android-debug-apk.yml` ou d’anciens workflows sont obsolètes. Utiliser exclusivement les workflows et chemins indiqués dans le référentiel actuel.

## Sécurité

Sentinel reste totalement séparé de A KI PRI SA YÉ. Aucun import, package, secret, configuration ou dépendance opérationnelle croisée n’est autorisé.

## Conclusion

La validation finale est un état démontré par des preuves actuelles, pas une affirmation historique. Toute future validation doit indiquer la date, le commit, le workflow, le résultat et les artefacts effectivement observés.
