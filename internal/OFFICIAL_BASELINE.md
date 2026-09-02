# BASELINE OFFICIELLE — SENTINEL QUANTUM VANGUARD AI PRO

## Objet

Ce document définit la baseline actuelle du dépôt. Il remplace les anciennes descriptions de produit et les snapshots historiques.

## Architecture canonique

- Web/PWA : racine du dépôt et `frontend/` selon l'arborescence actuelle.
- Android : `native-android-app/` uniquement.
- Gouvernance et sécurité : `decision-plane/`, `security/` et `scripts/`.
- CI/CD : `.github/workflows/`.

Le répertoire historique `android-app/` a été supprimé et ne doit plus être référencé comme source.

## Sécurité

Les contrôles principaux comprennent :

- contrôle d'isolation Sentinel/A KI PRI SA YÉ ;
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

Les anciens noms `RELEASE_*` et les anciennes variantes Android ne font plus partie de la configuration courante.

## Validation

Une fonctionnalité n'est considérée comme validée que si les preuves correspondantes existent réellement : tests exécutés, CI exécutée, résultats inspectés et contrôles de sécurité passés.

Un échec GitHub Actions avant l'exécution du premier step est classé comme problème d'infrastructure/runner et ne constitue pas une validation des tests.

## Documentation canonique

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `SECURITY.md`
- `FINAL_ACCEPTANCE_CHECKLIST.md`
- `AUDIT_CHECKLIST.md`
- `CHECKLIST_VERIFICATION.md`
- `RELEASE_STATUS.md`
- `ANDROID_README.md`
- `ANDROID_APK_GUIDE.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `docs/PRODUCTION_RELEASE_GUIDE.md`
- `docs/WORKFLOWS.md`

## Séparation de projets

**Sentinel Quantum Vanguard AI Pro ≠ A KI PRI SA YÉ.**

Aucune dépendance, donnée, configuration, secret, déploiement ou intégration d'A KI PRI SA YÉ ne doit être introduit dans ce dépôt.

**Last reviewed:** September 2026
