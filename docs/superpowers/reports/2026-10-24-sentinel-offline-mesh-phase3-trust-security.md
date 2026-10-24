# Sentinel Offline Mesh — phase 3 identities and trust

Date : 2026-10-24  
Branche : `feature/sentinel-offline-mesh-foundation-20261024`  
Référence : `17bce82011750beb6f3ddc331a3c360e63307418`

## Implémentation

- `OwnerDeviceKeyManager` conserve l'identité locale dans Android Keystore,
  génère une clé EC P-256 non exportable, tente StrongBox sur Android P+ puis
  retombe sur Android Keystore si StrongBox est indisponible. L'indicateur
  `localHardwareBackedIndicator` expose la propriété observée; aucune promesse
  matérielle n'est faite si l'appareil ne la fournit pas.
- Une invalidation Keystore ou une erreur de récupération provoque
  `IdentityUnavailableException`. La clé n'est pas remplacée silencieusement;
  un nouvel enrôlement explicite est nécessaire.
- `OfflineMeshTrustStore` implémente `UNKNOWN -> PENDING_APPROVAL -> TRUSTED`
  et `REVOKED`, avec erreurs de stockage séparées. L'approbation est une action
  distincte du handshake.
- `OfflineMeshGattAndroidTrustStore` persiste uniquement les métadonnées
  publiques et l'état de confiance dans `Context.noBackupFilesDir`, avec MAC
  HMAC-SHA-256 d'une clé Android Keystore non exportable. Une corruption, une
  clé MAC indisponible ou une écriture non confirmée refuse l'autorisation.
- La rotation exige une signature de l'ancienne clé puis une acceptation
  explicite. L'ancienne empreinte est conservée comme clé retirée et ne peut
  plus autoriser de session.
- `OfflineMeshGattOwnerIdentity` fournit le trust provider dynamique et le
  callback d'autorisation du transport. `OfflineMeshGattSecureTransport`
  réévalue l'autorisation après handshake et interrompt la session active après
  révocation.

## Tests ajoutés

Tests JVM (`OfflineMeshTrustStoreTest`, 9) : approbation explicite et reprise
après recréation, révocation persistante, rotation signée et refusée, refus de
réapprobation, écriture échouée, stockage corrompu et appels concurrents.

Tests de transport : révocation d'une autorisation sur canal établi, avec
verrouillage effectif des données privées.

Tests instrumentés (`OfflineMeshGattKeystoreInstrumentationTest`, 3) :
persistance/révocation avec Android Keystore, corruption du fichier local et
cycle de signature/révocation de l'identité Owner. Ils sont compilés mais non
exécutés faute de `adb` et d'appareil/émulateur disponible.

## Validation réelle

Commande :

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL`; 1 399 tests unitaires, 0 échec, 0 erreur,
0 ignoré. Offline Mesh : 82 tests, 0 échec/erreur. APK debug assemblé et
tests instrumentés compilés.

`git diff --check` : aucun diagnostic.

`./gradlew :app:lintDebug --no-daemon --console=plain` : échec avec les 3
erreurs API 28 historiques de `SelectedApkInspector.kt`, 217 avertissements et
19 hints. Aucun correctif APK n'a été intégré à cette branche; la correction
reste isolée sur `fix/selected-apk-inspector-api28-20261024`.

## Limites et risques résiduels

- Le protocole applicatif reste propriétaire et ne constitue pas une
  implémentation Noise; aucune revue cryptographique indépendante n'a eu lieu.
- La résistance au rollback est fournie par `noBackupFilesDir` et la clé MAC
  Keystore; une restauration manuelle de fichiers ou une compromission du
  système Android reste hors garantie.
- La révocation d'une session active est appliquée au prochain appel du canal
  ou callback de transport; le coordinateur doit utiliser le callback fourni
  lors de la construction du transport.
- Aucun test physique BLE ni test instrumenté sur matériel n'a été exécuté.

## Verdict

`CODE_COMPLETE / UNIT_TESTS_PASS / LIMITED / NOT_READY`.

`INSTRUMENTED_PASS`, `CRYPTO_REVIEW_PASS`, `PHYSICAL_BLE_PASS` et `READY` ne
sont pas attribués.
