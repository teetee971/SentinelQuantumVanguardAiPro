# Sentinel Quantum Vanguard AI Pro — Index

Ce document est la référence de navigation du projet. Les anciennes descriptions historiques ne font pas foi.

## Architecture actuelle

- Interface web/PWA : racine du projet et `frontend/`.
- Android natif : `native-android-app/` uniquement.
- Sécurité et gouvernance : `decision-plane/`, `security/`, `scripts/`.
- CI/CD : `.github/workflows/`.
- Déploiement web cible : Cloudflare Pages.

## Sécurité et séparation de projet

Sentinel Quantum Vanguard AI Pro est un projet autonome. Il ne doit contenir ni dépendance opérationnelle, ni configuration, ni secret, ni import provenant d’A KI PRI SA YÉ.

Le contrôle d’isolation est automatisé par `scripts/check-sentinel-isolation.js` et son test associé. Les workflows externes sont également soumis au contrôle de pinning SHA.

## Workflows actifs

- Project Isolation
- Security Governance Validation
- Security Fuzzing
- CodeQL
- Integrity Check
- Microsoft Defender for DevOps
- Frontend/Web build selon la configuration active
- Native Android build
- Android release sur tags de version uniquement

Les workflows supprimés ou historiques ne doivent pas être considérés comme actifs.

## Android

Le projet Android canonique est `native-android-app/`. Aucun ancien répertoire Android supprimé ne doit être recréé ou réutilisé comme source de build.

Le build de validation produit un APK de test. La release signée est contrôlée par `.github/workflows/android-release.yml` et doit être déclenchée par un tag de version conforme et rattaché à `main`.

## Validation

Un correctif n’est pas considéré comme validé uniquement parce qu’il est commité. La chaîne de preuve est : correctif appliqué → test local/automatisé → CI exécutée → résultats examinés → validation de sécurité.

À la date de cette révision, les jobs GitHub Actions peuvent échouer avant l’exécution des étapes. Cette situation est traitée comme un blocage CI/infrastructure et non comme une preuve de réussite ou d’échec du code.

## Documentation

Références prioritaires :

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `RELEASE_STATUS.md`
- `docs/WORKFLOWS.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `SECURITY.md`

## Principes

1. Défensif uniquement.
2. Moindre privilège.
3. Actions critiques soumises aux garde-fous et à la validation humaine.
4. Preuves et provenance vérifiables.
5. Aucun secret dans le dépôt.
6. Aucune capacité offensive ou de contournement.
7. Séparation stricte avec tout autre projet.

**Statut : documentation de référence technique, sans prétention de validation CI tant que les runners ne sont pas opérationnels.**
