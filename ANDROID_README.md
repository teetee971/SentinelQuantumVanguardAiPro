# Sentinel Quantum Vanguard AI Pro — Android

## Source canonique

Le projet Android maintenu se trouve exclusivement dans `native-android-app/`.

Les anciennes références à `android-app/` et `android-app/android/` sont historiques et ne doivent pas être utilisées pour construire ou publier l’application.

## CI Android

Le build de validation est défini par `.github/workflows/build-native-android.yml` et produit un artefact. Il ne publie pas automatiquement une release.

La release signée est définie par `.github/workflows/android-release.yml` et est déclenchée par des tags de version. Le workflow vérifie le rattachement du tag à `main` avant la publication.

## Sécurité

Aucun keystore, mot de passe ou clé privée ne doit être présent dans Git. Les secrets de release sont injectés uniquement au moment du build signé.

Sentinel reste totalement séparé de A KI PRI SA YÉ : aucun import, package, secret, configuration ou dépendance opérationnelle croisée n’est autorisé.

## Validation

Ne pas utiliser les anciennes affirmations « Production Ready », « tous les checks passent » ou des métriques historiques comme preuve actuelle. La validation exige des tests réellement exécutés et des résultats CI observés.

Des échecs GitHub Actions avant exécution des étapes ont été observés récemment ; cela constitue un blocage CI à traiter séparément du code Android.

## Références

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `RELEASE_STATUS.md`
- `docs/WORKFLOWS.md`
- `docs/RELEASE_BUILD_GUIDE.md`
