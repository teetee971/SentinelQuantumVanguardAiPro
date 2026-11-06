# Classification lint — qualification du 2026-11-06

Source exécutée : `1ac8bd5e325416bdb5ba1bca59566a561393b45b` ; arbre Android `0547bb2780ec041a204bac8ad7ebf1b4925ee34e`.
`:app:lintDebug` : **PASS**, **0 erreur, 221 warnings, 19 hints**. Aucun avertissement supprimé pour obtenir ce résultat.
Le rapport précédent mentionnant 216 warnings ne décrit pas cette exécution. Les 240 diagnostics et leurs emplacements sont archivés dans `qualification/evidence/20261106-final-1ac8bd5e/lint-classification.json`, avec les rapports XML, SARIF et texte.

| Catégorie principale | Diagnostics | Warnings | Hints |
| --- | --- | ---: | ---: |
| Sécurité / confidentialité | HardwareIds 2 ; DataExtractionRules 1 | 3 | 0 |
| Compatibilité Android | InlinedApi 25 ; QueryPermissionsNeeded 3 ; UnusedAttribute 6 ; ObsoleteSdkInt 3 | 37 | 0 |
| Accessibilité / localisation | PluralsCandidate 8 ; TypographyEllipsis 2 ; Typos 1 | 11 | 0 |
| Performance | ApplySharedPref 8 ; AutoboxingStateCreation 19 | 8 | 19 |
| Maintenance | UseKtx 82 ; UnusedResources 62 ; IconLauncherShape 10 ; IconDuplicates 5 ; AndroidGradlePluginVersion 1 ; ModifierParameter 1 ; ViewConstructor 1 | 162 | 0 |
| Total | | 221 | 19 |

## Risques à examiner en priorité

- **Compatibilité, 25 InlinedApi** : les permissions Bluetooth sont sélectionnées selon l’API ; les opérations RoleManager utilisent des branches API et des chemins hérités. Les constantes SubscriptionManager sont des clés de broadcast inlinées, avec validation numérique et repli hérité. Cela ne prouve pas le comportement des providers/OEM. Les deux chemins `ACTION_APP_NOTIFICATION_SETTINGS` de `PhoneCoreActivationActivity` (API 26) restent à vérifier sur API 24/25 ; le repli vers les détails de l’application n’est pas établi pour ces chemins. Essais API 24/28/29/31/37 encore requis. Pas de suppression globale de ces avertissements.
- **Visibilité des applications, 3 QueryPermissionsNeeded** : résultat Android 11+ partiel. Les textes de l’analyseur parlent des applications visibles ; ne pas présenter ces résultats comme un inventaire exhaustif ou un antivirus.
- **Confidentialité, 2 HardwareIds** : le numéro de ligne est lu localement pour l’identité de la SIM MMS, avec échec fermé si accès refusé ; ANDROID_ID est haché pour la liaison d’identité backend. Un identifiant haché persistant reste une donnée pseudonyme, pas un secret ni une preuve de nouvelle inscription.
- **Sauvegarde, 1 DataExtractionRules** : `allowBackup=false` limite le risque. L’exclusion effective et les transferts OEM sur API 24+ restent à tester ; ne pas déduire une qualification backup d’un résultat lint.
- **Performance, 8 ApplySharedPref** : `commit()` peut bloquer son thread ; plusieurs écritures portent sur l’identité, le rôle ou le nettoyage. Aucun remplacement automatique par `apply()` sans analyse de durabilité et tests crash/reprise.
- **Accessibilité** : 8 PluralsCandidate, 2 TypographyEllipsis et 1 Typos restent une dette de localisation. Les 19 hints d’autoboxing Compose concernent les allocations. TalkBack, grossissement et contraste n’ont pas été qualifiés physiquement.

## Maintenance et limites

Les 162 warnings de maintenance ne démontrent pas à eux seuls un défaut bloquant. Les 62 ressources dites inutilisées demandent une vérification des variantes et références indirectes avant suppression. L’avis AndroidGradlePluginVersion ne justifie pas une migration du toolchain pendant ce gel. Les 6 UnusedAttribute sont ignorés sur d’anciennes API ; les 3 ObsoleteSdkInt concernent des gardes devenues redondantes.

Les warnings du compilateur Kotlin, notamment les API Telecom dépréciées et les annotations nullables de `InetAddress.hostAddress`, sont conservés dans les journaux ; ils ne font pas partie du décompte lint 221/19.

Les corrections prioritaires réalisées sont l’import manquant bloquant la compilation, la lecture après-fin du curseur MMS (4 tests, échec reproduit avant correction) et les six avis npm de Vite (8.3.1 → 8.3.3). Aucune nouvelle surface UI, refactorisation esthétique ou mise à jour majeure.

**Galaxy S24+ : NOT_TESTED.** Lint PASS ne signifie pas READY, ni absence de vulnérabilités Android transitives.
