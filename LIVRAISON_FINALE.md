# LIVRAISON FINALE — Sentinel Quantum Vanguard AI Pro

> Archive documentaire. Ce fichier ne constitue pas un état de production actuel.

## Objet

Ce document conservait un ancien compte rendu de livraison du module téléphonique. Il contenait des chemins Android, workflows, permissions, contacts, fonctionnalités et affirmations de validation qui ne correspondent plus nécessairement à l’arborescence actuelle.

## Règle actuelle

La source Android canonique est `native-android-app/`. Les anciennes références à `android-app/android/` sont historiques et ne doivent pas servir aux builds.

Le build Android de validation est défini par `.github/workflows/build-native-android.yml`. La release signée est définie par `.github/workflows/android-release.yml` et est déclenchée par des tags de version conformes rattachés à `main`.

Les anciens workflows et instructions de publication automatique décrits dans ce document ne doivent pas être réactivés sur la base de cette archive.

## Validation

Les affirmations historiques telles que « Production Ready », « conformité garantie », « tous les tests passés » ou « zéro vulnérabilité » sont retirées de la référence opérationnelle. Un correctif n’est considéré comme validé qu’après exécution des tests, de la CI et examen des résultats.

À la dernière vérification, l’exécution de certains jobs GitHub Actions échouait avant le démarrage des étapes. Cela constitue un blocage CI/infrastructure et non une preuve de défaillance du code.

## Sécurité et séparation

Sentinel Quantum Vanguard AI Pro doit rester totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel entre les deux projets n’est autorisé.

## Références

Pour les instructions actuelles, utiliser `README.md`, `ANDROID_README.md`, `VALIDATION_FINALE.md`, `AUDIT.md`, `RELEASE_STATUS.md` et `docs/RELEASE_BUILD_GUIDE.md`.
