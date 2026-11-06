> État courant de mise à niveau : [versionCode 20 et signature locale](V20_UPGRADE_20261106.md). Les résultats ci-dessous sont historiques.

# Reprise — réglages de notifications Android 24/25

**BLOCKED global ; PASS technique local. Galaxy S24+ : NOT_TESTED.**

Source qualifiée : `e002ccc7927ec9df533c54d6707254bf0a71f7ab`, arbre Android `95943bbbe3b03980ece7348ef3d1866eb809f891`. Cette qualification remplace la précédente pour les sources Android. Les commits suivants de preuves conservent cet arbre et sont revérifiés avant poussée ; leur SHA de livraison et leurs journaux sont remis avec l’archive finale, hors dépôt, afin d’éviter l’autoréférence d’un commit dans son contenu.

## Correction minimale

Les deux chemins existants de notifications de `PhoneCoreActivationActivity` utilisaient `ACTION_APP_NOTIFICATION_SETTINGS` (introduit en API 26) sur API 24/25. Ils utilisent maintenant une politique commune : détails de l’application sur API 24/25 ; réglages de notifications sur API 26+ ; réglages plein écran sur API 34+ lorsque ce prérequis manque dans l’assistant. Aucun nouvel écran ou dashboard.

Les quatre tests vérifient les actions, URI et extras effectivement sélectionnés, avec des attentes indépendantes, aux frontières API 25/26 et 33/34. Le comportement antérieur extrait échoue sur API 24/25 (1 échec parmi 4 tests), puis les 4 tests passent après correction. Les tests JVM vérifient la destination ; l’ouverture réelle des activités de réglages/OEM reste NOT_TESTED.

## Résultats

| Contrôle | Résultat |
| --- | --- |
| PhoneSecurityVisualDashboardSourceContractTest | 1 test réussi, 0 ignoré |
| Suite complète sans fixture | 1 409 tests, 0 échec, 1 ignoré (fixture APK nécessaire) |
| Suite complète --rerun avec le nouvel APK | **1 409 tests, 0 échec, 0 erreur, 0 ignoré** |
| :app:lintDebug | **PASS ; 0 erreur, 221 warnings, 19 hints** |
| :app:assembleDebug | PASS |
| Régression notifications | 4 tests réussis après reproduction de l’échec |
| Cohérence produit Android | 69 tests réussis |
| Hygiène des credentials | 6 tests réussis |
| Audit npm | 0 vulnérabilité |
| Signature APK / zipalign | v2 debug vérifiée / PASS |
| Secrets dans l’APK décompressé | Aucun motif reconnu détecté ; contrôle non exhaustif |

Preuves, commandes exactes, empreintes des sources Android, résultats JUnit, rapports lint et journaux : `evidence/20261106-notification-api24-e002ccc7`. `RAW_LOGS.zip` conserve les journaux et commandes bruts ; les copies texte normalisent uniquement les espaces de fin de ligne pour git diff --check. Le ledger conserve l’ancienne qualification dans l’historique.

## Lint courant — classification exhaustive des 240 diagnostics

| Catégorie | Warnings | Hints | Types |
| --- | ---: | ---: | --- |
| Sécurité/confidentialité | 3 | 0 | HardwareIds 2 ; DataExtractionRules 1 |
| Compatibilité Android | 36 | 0 | InlinedApi 24 ; QueryPermissionsNeeded 3 ; UnusedAttribute 6 ; ObsoleteSdkInt 3 |
| Accessibilité/localisation | 11 | 0 | PluralsCandidate 8 ; TypographyEllipsis 2 ; Typos 1 |
| Performance | 8 | 19 | ApplySharedPref 8 ; AutoboxingStateCreation 19 |
| Maintenance | 163 | 0 | UseKtx 83 ; UnusedResources 62 ; IconLauncherShape 10 ; IconDuplicates 5 ; AndroidGradlePluginVersion 1 ; ModifierParameter 1 ; ViewConstructor 1 |

La correction retire les quatre références API 26 des deux appels et les concentre dans la politique, qui sélectionne ses constantes selon l’API fournie par Build.VERSION.SDK_INT. Lint ne déduit pas ce flux de la fonction pure et rapporte les trois constantes API 26/34 dans cette politique ; les tests en couvrent les seuils. Aucun avertissement supprimé. Le nouvel avis UseKtx est stylistique, sans refactorisation appliquée pendant ce gel.

Les risques restants sont ceux documentés précédemment : compatibilité des rôles/permissions sur anciennes API et OEM, inventaire d’applications partiel, sauvegarde/transfert OEM, SharedPreferences synchrones et accessibilité réelle. La sélection incorrecte des réglages API 24/25 est corrigée ; cela ne prouve pas l’absence d’autres incompatibilités Android.

## Nouvel artefact

- Chemin : `native-android-app/app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256 : `2f2ef6e25da6ebe74f098eab5b15e5b5a210ed72edf2c022e0b485969bebff89`.
- Package `com.sentinel.quantum`, versionCode `10`, versionName `1.0.9`, minSdk `24`, targetSdk `36`, compileSdk `37`.
- Certificat SHA-256 `fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6`, identique à l’APK du précédent SHA `5454f5a6`. La continuité avec l’installation originale de l’utilisateur reste inconnue ; aucune installation ou désinstallation effectuée.

Room, dépendances et wrapper inchangés. Le diff fonctionnel depuis la précédente qualification ne concerne que les deux appels de réglages, la politique et les tests. Pas de changement Phone Core/SMS/MMS/Contacts supplémentaire.

## Blocages qui demeurent

Dependabot : nouvelle requête REST toujours refusée en HTTP 403. Les six avis Vite sont corrigés sur la branche de travail, l’audit npm reste à zéro ; aucune fermeture des alertes sur la branche par défaut affirmée. Aucun workflow GitHub Actions déclenché, aucune fusion dans main. PR #1754 conservée en brouillon.

Essais physiques restants : ouverture des réglages sur API 24/25 et 34+, installation/mise à jour sans perte de données, appels entrants/sortants et double SIM, SMS/MMS/providers et miniatures avec permissions révoquées, Contacts, base Room existante, sauvegarde/transfert OEM, notifications/écran verrouillé et TalkBack/gros caractères. Aucun appareil connecté dans cette session.

**Galaxy S24+ : NOT_TESTED.**
