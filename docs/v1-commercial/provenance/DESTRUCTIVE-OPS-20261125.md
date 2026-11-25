# Opérations destructives — après le merge 42a57f19

Statut : **NO-GO**. Ce document ne qualifie pas une mise à niveau V25. Aucune base du S24+ n'a été ouverte. Le merge `42a57f19` a pour parents `c2c970d8` et `c4f4d981`. Le verrou Room décrit ici est le commit posé au-dessus de ce merge. `b2e2eebe` n'est pas sur GitHub et n'est pas reproduit.

## Room

`SentinelLocalDatabase.openPreserving` lit `room_master_table` et `user_version` en `OPEN_READONLY` avant `Room.databaseBuilder`. `allowOpen` refuse un fichier présent illisible, une identité différente de `21617aeab12bfb29ee9bec17bc238d62`, ou une version autre que 1. Le refus lance `DESTRUCTIVE_MIGRATION_FORBIDDEN` et ne supprime pas le fichier.

`fallbackToDestructiveMigration` est absent du source de production. `deleteDatabase` n'apparaît que dans le test d'instrumentation des 29 lignes synthétiques. `instrumentationMayStart` n'autorise que le paquet `com.sentinel.quantum.v1validation`. Le paquet installé `com.sentinel.quantum` est refusé même si `sentinel-local.db` est absente, synthétique, illisible, ou accompagnée de fichiers WAL/SHM. L'identité du paquet est lue avant toute ouverture SQLite. Le runner est une défense supplémentaire : `am instrument` peut encore nommer `androidx.test.runner.AndroidJUnitRunner`, qui est dans le classpath de test. Chaque classe d'instrumentation porte donc `RefuseInstalledPackageRule`, et les méthodes qui effacent rappellent le garde-fou avant l'écriture. `testApplicationId` n'est pas redéfini : l'APK de test reste distinct (`<applicationId>.test`). Sans `-PsentinelCommercialV1=true`, la cible est le paquet installé et le garde-fou refuse. Le paquet dédié `com.sentinel.quantum.aivalidation.c11` est lui aussi refusé. `scripts/v1-preflight-termux.sh` refuse désormais l'ancienne exception (`!databaseFilePresent` ou `syntheticFixture == true` sur `com.sentinel.quantum`). La présence du runner dans `build.gradle` ne suffit plus au préflight. Ce script ne compile pas et ne prouve pas une exécution Termux.

SQLite 3.40.1, hors Android, a relu une base WAL synthétique en `mode=ro` tant que le répertoire était inscriptible. Ce n'est pas `SQLiteDatabase.OPEN_READONLY` sur le téléphone, et cela ne qualifie pas la sonde.

`CallFilterDecisionDao.clear()` supprime toutes les décisions. Le seul appelant est la confirmation explicite de `CallFilterHistoryScreen`. `prune(500)` s'exécute après un nouvel enregistrement et ne concerne pas les 29 lignes tant que l'historique reste sous le plafond. Aucun des deux n'est appelé depuis `SentinelApplication.onCreate`.

## Démarrage

`SentinelApplication.onCreate` ne fait que `ScreeningProcessWarmup.schedule`. Après reprise d'une activité, le profil commercial annule des travaux WorkManager (`WorkScheduler`, `CollectiveDefenseWorkScheduler`) et planifie `MmsSendCleanupWorker.scheduleStartupRecovery`. Ce worker appelle `repairJournal()` des deux magasins MMS. Les phases `READY`, `SUBMITTED` et `SUBMISSION_UNKNOWN` gardent la ligne. Les suppressions restantes exigent le rôle SMS et la sélection d'identité.

## Préférences et clés

| Opération | Déclencheur | Effet |
| --- | --- | --- |
| `SettingsStore.purgeCommunicationReputation` | révocation de consentement déjà câblée | retire seulement les clés `h1:` |
| `CallBlocklistStore` | action utilisateur ou expiration | retire des ensembles nommés, pas le fichier entier |
| `OsintFeedCache.clear` | bouton réglages | `edit().clear()` du fichier dédié `sentinel_osint_cache`, y compris `read_ids`. Explicite, pas au démarrage. Hors surface V1. |
| `PhoneDecisionJournalStore.clear`, `PhonePrivateTimelineStore.clear`, `NetworkTimelineStore.clear` | aucun appelant Kotlin | API présente, non invoquée |
| `VpnWireGuardIdentityStore.clear`, `MeshWireGuardIdentityStore.clear`, `MeshNodeCredentialStore.clear` | `clearEnrollment` / `rotateIdentity`, eux-mêmes sans appelant UI | effacent préférences et alias Keystore mesh/VPN. Pas sur le chemin V1 observé. |
| `OwnerDeviceKeyManager.revokeLocalKey` | aucun appelant | `deleteEntry` Keystore. Non invoqué. |
| `NetworkTrustStore.clear(id)` | écran de surveillance réseau | retire une empreinte, pas le magasin |

Les secrets Android Keystore ne sont pas exportés par ce correctif. Une rotation ou un `deleteEntry` reste irréversible sur l'appareil.

## Suite JVM — après ce constat

Le paragraphe « Non fait » ci-dessous décrit l'état au moment de la rédaction initiale. L'exécution ultérieure est dans [JVM-SUITE-20261125.md](JVM-SUITE-20261125.md). Elle ne qualifie toujours pas le S24+.

L'écran d'historique attrape désormais un refus d'ouverture : il affiche que le fichier local est intact au lieu de composer `CallFilterLogStore.get` sans `runCatching`. Le filtrage d'appel faisait déjà ce rattrapage après la réponse Android. Le refus ne supprime pas `sentinel-local.db`.

## Non fait

Pas de suite Gradle. Pas d'ouverture de `sentinel-local.db` du Galaxy S24+. Pas de changement de `versionCode` ni de signature.
