# Audit des workflows GitHub Actions — état courant

**Révision :** septembre 2026

Ce rapport remplace les inventaires historiques. La référence opérationnelle est `.github/workflows/` sur `main`.

## Workflows actuellement suivis

- `ai-governance-validation.yml`
- `android-release.yml`
- `build-native-android.yml`
- `codeql-analysis.yml`
- `frontend-validation.yml`
- `integrity-check.yml`
- `osint-validation.yml`
- `security-fuzz.yml`
- `security-governance-validation.yml`
- `security-validation.yml`
- `sentinel-isolation.yml`

L'ancien workflow Windows/.NET a été supprimé et ne fait plus partie du périmètre.

## Contrôles de sécurité

Les Actions tierces utilisées dans les workflows actifs sont soumises au contrôle de pinning par SHA. Les permissions doivent rester explicites et minimales. Le workflow de release Android est séparé du build de validation.

## Android

La source canonique est `native-android-app/`. Le build de validation ne publie pas de release. La release signée est réservée aux tags conformes et vérifie leur rattachement à `main` avant publication.

## Isolation

Sentinel Quantum Vanguard AI Pro constitue un projet autonome. Aucun import, secret, dépendance, configuration ou déploiement croisé avec une application externe n'est autorisé. `sentinel-isolation.yml` et `scripts/check-sentinel-isolation.js` constituent la barrière automatisée canonique. Les références Firebase des contrôles négatifs sont des fixtures de détection et ne doivent pas être interprétées comme des dépendances opérationnelles.

## État CI

Certains jobs GitHub-hosted observés récemment ont échoué avant l'exécution de leurs étapes (`steps: []`). Il s'agit d'un blocage d'exécution CI/infrastructure et non d'un résultat de test du code. Aucun contrôle ne doit être affaibli pour contourner ce symptôme.

## Règle de preuve

Un contrôle n'est déclaré validé que si son exécution réelle et son résultat sont observés. Les rapports historiques servent uniquement à la traçabilité.

**Statut actuel : validation CI complète en attente pour les jobs bloqués avant exécution.**
