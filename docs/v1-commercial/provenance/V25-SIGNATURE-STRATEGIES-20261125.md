# Stratégies V25 — document uniquement

Statut : **NO-GO**. Aucune installation, aucune désinstallation, aucun changement de `versionCode`, aucune rotation de signature n’est exécutée ici.

## Faits de signature

- V25 observée : `com.sentinel.quantum`, `versionCode` 25, `versionName` 1.0.11, certificat debug SHA-256 `68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2`, APK SHA-256 `05aeb42b2cd543954bda1467fe698b3c9cb6fb9cc180dbbaab573af357211ddb`.
- L’APK `05aeb42b…` n’est dans aucun commit, tag, release ni document du dépôt.
- Le certificat `68915958…` est documenté comme signataire debug déjà installé (notes v19/v20 du 6 novembre 2026 : `/root/.android/debug.keystore`, alias `androiddebugkey`, clé privée non transmise).
- Le seul keystore versionné, `android-app/android/app/debug.keystore` du commit `4de30186a`, a pour SHA-256 `D9:B9:BD:EC:64:45:8D:1D:96:9D:E3:7D:EF:BA:0D:81:4D:27:65:63:76:C8:A7:8C:C9:F7:7E:08:17:76:B3:30`. Il ne correspond pas. Il est absent de la tête de #1776.
- Cette VM n’a pas de `~/.android/debug.keystore`. Aucun secret `KEYSTORE_*` n’est présent dans l’environnement.
- `.github/workflows/android-release.yml` décode `secrets.KEYSTORE_BASE64` vers `/tmp/release.keystore` pour `assembleRelease`. Ce secret n’est pas lisible ici. Le workflow ne génère pas de clé debug. Posséder le certificat public ne donne pas la clé privée. Si le fichier `/root/.android/debug.keystore` de la machine d’origine a disparu, la clé de la V25 est irrécupérable depuis ce dépôt.

## 1. Mise à niveau qui conserve les données

Conditions :

- même `applicationId` `com.sentinel.quantum` ;
- `versionCode` strictement supérieur à 25 (proposition : 26, non appliquée) ;
- même clé privée que `68915958…`, ou rotation APK Signature Scheme v3 **signée par cette ancienne clé** (elle reste indispensable).

Plan si la clé est retrouvée sur la machine d’origine, hors de ce dépôt : signer un APK de la tête qualifiée, vérifier `apksigner verify --print-certs`, comparer le certificat, n’installer qu’après accord explicite.

Plan B si la clé est introuvable : ne pas désinstaller. Depuis la V25 encore installée, exporter par l’écran de sauvegarde de l’application ce qu’elle sait écrire (préférences et préfixes). Les empreintes exactes liées au Keystore de l’appareil et l’historique Room ne sont pas couverts par cet export. Les SMS/MMS du fournisseur système restent tant que le paquet et le rôle ne sont pas retirés. Pas de `adb backup` destructif, pas de désinstallation.

## 2. Distribution commerciale

Générer une clé de release hors dépôt, la stocker hors Git, enregistrer seulement son empreinte publique. Le Gradle existant signe `release` avec `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`. Play App Signing garde la clé d’application chez Google et n’utilise la clé d’upload que pour les envois.

Cette identité est incompatible avec une mise à jour directe de la V25 signée debug : Android refuse un `applicationId` identique signé par un autre certificat. Une désinstallation détruirait les données privées de l’application. Le canal commercial est donc un autre paquet, ou une installation neuve assumée, pas une mise à jour de la V25.
