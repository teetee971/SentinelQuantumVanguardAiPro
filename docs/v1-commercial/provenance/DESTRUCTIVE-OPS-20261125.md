# Opérations destructives — après le merge 42a57f19

Statut : **NO-GO**. Ce document ne qualifie pas une mise à niveau V25. Aucune base du S24+ n'a été ouverte. Le merge `42a57f19` a pour parents `c2c970d8` et `c4f4d981`. Le verrou Room décrit ici est le commit posé au-dessus de ce merge. `b2e2eebe` n'est pas sur GitHub et n'est pas reproduit.

## Room

`SentinelLocalDatabase.openPreserving` lit `room_master_table` et `user_version` en `OPEN_READONLY` avant `Room.databaseBuilder`. `allowOpen` refuse un fichier présent illisible, une identité différente de `21617aeab12bfb29ee9bec17bc238d62`, ou une version autre que 1. Le refus lance `DESTRUCTIVE_MIGRATION_FORBIDDEN` et ne supprime pas le fichier.

`fallbackToDestructiveMigration` est absent du source de production. `deleteDatabase` n'apparaît que dans le test d'instrumentation, qui efface sa propre base synthétique avant de la créer.

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

## Non fait

Pas de suite Gradle. Pas d'ouverture de `sentinel-local.db` du Galaxy S24+. Pas de changement de `versionCode` ni de signature.
