# Revue complète et remédiation ciblée Offline Mesh

Date : 2026-10-24  
Branche : `feature/sentinel-offline-mesh-foundation-20261024`

## Défaut confirmé

Le magasin de confiance pouvait conserver un ancien fichier authentifié et
son ancienne clé HMAC si le processus était interrompu après le calcul du
nouveau MAC mais avant la suppression de l'ancienne clé. Une révocation
interrompue pouvait alors relire l'ancien état `TRUSTED`.

## Correction

Chaque mutation d'une révision existante écrit d'abord un marqueur de
transition durable et synchronisé dans `noBackupFilesDir`. Toute lecture
échoue tant que ce marqueur existe. Le marqueur reste présent après une panne
à n'importe quelle étape suivante et impose la récupération explicite par
réinitialisation/réapprobation. Il est supprimé uniquement après publication
du nouvel état et de sa clé.

Le test ajouté couvre précisément le crash avant la suppression de l'ancienne
clé. Le test existant d'échec de création de clé a été durci pour vérifier le
nouveau comportement fail-closed.

## Validation

- Test ajouté observé rouge avant implémentation : API de marqueur absente.
- `:app:testDebugUnitTest` : `BUILD SUCCESSFUL`, 1 404 tests, 0 échec.
- `:app:assembleDebug` : `BUILD SUCCESSFUL`.
- `:app:compileDebugAndroidTestKotlin` : `BUILD SUCCESSFUL`.
- `git diff --check` : succès.

Les tests instrumentés et physiques restent non exécutés. La correction ne
constitue pas une preuve de validation Keystore matérielle ni de qualification
BLE radio.

## Limites restantes

- Les écritures concurrentes entre plusieurs instances/processus utilisant le
  même fichier doivent encore recevoir un test dédié et un verrouillage
  inter-instance explicite.
- `OfflineMeshGattCryptoSession.encrypt()` accepte toujours un compteur fourni
  par l'appelant ; le transport sécurisé lui-même impose son compteur, mais
  l'API devrait empêcher toute réutilisation accidentelle de nonce.
- La revue cryptographique indépendante, les tests instrumentés et les essais
  sur deux appareils BLE restent nécessaires.

## Verdict

`UNIT_TESTS_PASS / CODE_COMPLETE / LIMITED / NOT_READY`

