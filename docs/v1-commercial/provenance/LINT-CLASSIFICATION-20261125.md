# Classement des avertissements lint — 2026-11-25

Statut : **NO-GO commercial** et **NO-GO installation V26**. Ce classement ne supprime aucun contrôle, n'ajoute pas de `lint.xml` baseline, et ne change pas `versionCode` ni la signature.

Source : `./gradlew :app:lintDebug -PsentinelCommercialV1=true` sur `3fd8aa1`, rapport `lint-results-debug.xml`, lint 9.4.1. **264** issues : **245** Warning, **19** Hint, **0** erreur. La même tâche, relancée après le seul changement de `SentinelAiLocalHttpTest`, reste à 264 / 245 / 19 / 0. Relance du 2026-11-25T17:49Z après le refus inconditionnel de l'instrumentation hors `com.sentinel.quantum.v1validation` (tests, garde-fou, pas le chemin Room de production) : toujours **0 erreur, 245 warnings, 19 hints**. Aucune issue nouvelle. Aucun `lint.xml` n'est ajouté. Ce lint n'exécute pas l'instrumentation ARM64.

Aucun de ces 264 signaux n'est une erreur de compilation lint. Aucun ne remplace l'instrumentation ARM64, l'ouverture de la base réelle, ni la preuve de signature. Ils ne justifient pas un APK de mise à niveau.

## Ce qui ne bloque pas la livraison

| Id | N | Sévérité | Catégorie | Lecture |
| --- | ---: | --- | --- | --- |
| UseKtx | 92 | Warning | Productivity | Style Kotlin. Pas un défaut de données. |
| UnusedResources | 57 | Warning | Performance | Ressources sans référence lint. Les retirer sans appelant vérifié pourrait supprimer un texte encore utile. Non fait. |
| AutoboxingStateCreation | 19 | Hint | Performance | Allocation Compose. Pas un défaut fonctionnel. |
| SetTextI18n | 21 | Warning | Internationalization | Littéraux hors `strings.xml`. Le produit V1 est en français. |
| IconLauncherShape | 10 | Warning | Usability:Icons | Forme d'icône. |
| IconDuplicates | 5 | Warning | Usability:Icons | Doublons d'icônes. |
| TypographyEllipsis | 2 | Warning | Usability:Typography | Points de suspension. |
| ObsoleteSdkInt | 4 | Warning | Performance | Branches SDK devenues mortes. |
| PluralsCandidate | 8 | Warning | Correctness:Messages | Pluriels suggérés. |
| UnusedAttribute | 6 | Warning | Correctness | Attributs de manifeste ignorés sous leur niveau d'API (`usesPermissionFlags` 31, `showWhenLocked` / `turnScreenOn` 27). Sans effet sur un S24+. |
| AndroidGradlePluginVersion | 1 | Warning | Correctness | Gradle 9.8.0, 9.9.2 annoncée. Pas une CVE relevée ici. Non monté. |
| ModifierParameter | 1 | Warning | Correctness | Ordre d'un paramètre Compose. |
| Typos | 1 | Warning | Correctness:Messages | `strings_phone_lists.xml` « correspondants ». Faux positif du dictionnaire anglais sur un mot français. Ne pas « corriger ». |

Sous-total cosmétique ou hors donnée : 227, dont les 19 hints.

## Correctness à ne pas « réparer » en silence

`InlinedApi`, 25 Warning. Constantes d'API supérieures à `minSdk` 24 (`RoleManager` 29, Bluetooth 31, abonnements 26/30, réglages de notification 26). Elles concernent surtout `PhoneCoreActivationActivity`, `MmsSubscriptionResolver` et `BluetoothScanner`. Lint ne démontre pas l'absence de garde `SDK_INT`. Le téléphone de qualification est un S24+, au-dessus de ces niveaux. Ce n'est pas un bloqueur de la migration V25 sur cet appareil. Ce n'est pas non plus une raison de lever `minSdk`.

`ApplySharedPref`, 9 Warning. `commit()` est synchrone. `apply()` retourne avant l'écriture et peut perdre l'état si le processus meurt. Ne pas basculer vers `apply()` pour éteindre le contrôle.

| Fichier | Lignes | Surface |
| --- | --- | --- |
| `PhoneCoreSetupWizardStore.kt` | 34, 41, 70, 85 | Phone Core, wizard |
| `MmsDownloadRecoveryJournal.kt` | 84 | MMS, journal de reprise |
| `MeshNodeCredentialStore.kt` | 32, 67 | Mesh, hors V1 exposée |
| `MeshWireGuardIdentityStore.kt` | 97 | Mesh, hors V1 exposée |
| `VpnWireGuardIdentityStore.kt` | 96 | VPN, hors V1 exposée |

Les quatre `commit()` du wizard et celui du journal MMS sont les seuls de ce groupe sur le chemin téléphonique. Les garder synchrone est plus sûr qu'un silence lint.

`QueryPermissionsNeeded`, 1 Warning, `AppPermissionAnalyzer.kt:75`. `getInstalledApplications` ne voit plus toutes les applications depuis Android 11. La capacité d'audit téléphone entier est hors surface commerciale V1 (`commercial_v1_exposed` false pour les audits de ce type). Pas une perte de `sentinel-local.db`. Le contrôle reste actif.

## Sécurité — les deux seuls Security

Ce sont les seuls issues de catégorie Security. Ils ne sont pas cosmétiques. Ils ne sont pas non plus une preuve que la base V25 sera ouverte.

1. `HardwareIds` — `IncomingMmsConversationStore.kt:495`, `TelephonyManager.line1Number`. Le numéro de la ligne qui a reçu le MMS sert à ne pas retirer ce numéro d'une conversation de groupe. L'échec de permission reste fail-closed (ensemble vide). Usage local d'identité MMS, pas un identifiant publicitaire. Ne pas retirer l'appel pour faire taire lint : cela changerait la résolution de fil. À garder dans la revue de vie privée MMS.
2. `HardwareIds` — `SentinelDeviceIdentity.kt:16`, `Settings.Secure.ANDROID_ID`, puis SHA-256. Appelé par `SettingsScreen` et `VoiceStudioActivity`. La voix est `commercial_v1_exposed` false (`voice_transform_calls`). L'identifiant hashé peut servir un rattachement backend hors Phone Core. Ne pas le traiter comme qualifié, ne pas supprimer le warning.

## Décision

Les 245 avertissements et 19 indications ne bloquent pas, à eux seuls, la suite du travail de qualification. Ils ne l'autorisent pas non plus. Le NO-GO reste tenu par l'absence de preuve ARM64, de migration réelle et de clé V25. Aucun contrôle lint n'a été désactivé.
