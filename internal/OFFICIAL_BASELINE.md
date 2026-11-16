# BASELINE OFFICIELLE — SENTINEL QUANTUM VANGUARD AI PRO

## Objet

Ce document définit la baseline actuelle du dépôt. Les anciennes descriptions et snapshots historiques ne font pas foi.

## Architecture canonique

- Web/PWA : racine du dépôt et `frontend/` selon l'arborescence actuelle.
- Android : `native-android-app/` uniquement.
- Gouvernance et sécurité : `decision-plane/`, `security/` et `scripts/`.
- CI/CD : `.github/workflows/`.

Les anciens arbres Android et frontend MVP ont été supprimés et ne doivent pas être recréés comme sources de build.

## Sécurité

Les contrôles principaux comprennent :

- contrôle d'isolation strict du projet ;
- pinning des actions GitHub sur SHA immuables ;
- tests de gouvernance sécurité et IA ;
- fuzzing de sécurité déterministe en environnement autorisé ;
- validation de provenance et d'intégrité des preuves ;
- simulation avant actions critiques ;
- validation humaine pour les actions sensibles ;
- absence d'exécution directe d'actions sensibles par les composants IA.

## Android release

Workflow canonique : `.github/workflows/android-release.yml`.

Source : `native-android-app/`.

Secrets de signature attendus :

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Aucun keystore, mot de passe ou clé privée ne doit être commité.

## Validation

Une fonctionnalité n'est considérée comme validée que si les preuves correspondantes existent réellement : tests exécutés, CI exécutée, résultats inspectés et contrôles de sécurité passés.

Un échec GitHub Actions avant l'exécution du premier step est classé comme problème d'infrastructure/runner et ne constitue pas une validation des tests.

## Documentation canonique

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `SECURITY.md`
- `ANDROID_APK_GUIDE.md`
- `RELEASE_CHECKLIST.md`
- `RELEASE_STATUS.md`
- `RESUME_FINAL_FACTUEL.md`
- `VALIDATION_FINALE.md`
- `VALIDATION_ARCHITECTURE.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `docs/PRODUCTION_RELEASE_GUIDE.md`
- `docs/WORKFLOWS.md`

## Séparation de projets

Sentinel Quantum Vanguard AI Pro reste totalement séparé de tout autre projet. Aucune dépendance, donnée, configuration, secret, déploiement ou intégration croisée n'est autorisée.

**Last reviewed:** September 2026
