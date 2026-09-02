# Historique — résolution de conflits de fusion

Ce document est conservé uniquement comme trace historique d'une ancienne opération de fusion. Il ne décrit pas l'architecture, les workflows, les chemins Android ou les procédures de release actuels.

## Périmètre historique

Le document d'origine concernait une ancienne branche `phase-b` et le PR #134. Cette opération n'est pas une procédure à reproduire sur `main` aujourd'hui.

Les chemins, workflows et commandes cités dans l'ancienne version peuvent ne plus exister. Ils ne constituent donc pas une source de vérité technique.

## Source de vérité actuelle

Pour l'état actuel de Sentinel Quantum Vanguard AI Pro, utiliser :

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `RELEASE_STATUS.md`
- `docs/WORKFLOWS.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `ANDROID_APK_GUIDE.md`

Le projet Android canonique actuel est `native-android-app/`. Les workflows actifs doivent être lus directement dans `.github/workflows/`.

## Sécurité

Aucune ancienne procédure de fusion ne doit être utilisée pour contourner les contrôles actuels. Les garde-fous d'isolation, de supply chain et de gouvernance sécurité restent obligatoires.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel avec cet autre projet n'est autorisé.

## Règle de validation

Un document historique ne constitue pas une preuve de l'état actuel du dépôt. Toute affirmation opérationnelle doit être vérifiée contre le code et la configuration présents sur `main`.
