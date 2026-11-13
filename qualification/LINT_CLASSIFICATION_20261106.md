# Classification lint — 2026-11-06

Source : `:app:lintDebug` sur le commit de code `b866bc45e`.

Résultat terminal : **PASS**, 0 erreur, 216 warnings, 19 hints.

## Priorité P1 — compatibilité à surveiller

- `InlinedApi` : 25 occurrences restantes. Les accès les plus dangereux identifiés
  (`SigningInfo` et les flags de signature API 28) ont été corrigés avec des branches
  API explicites. Les occurrences restantes concernent surtout les constantes RoleManager,
  Bluetooth, Settings et SubscriptionManager; elles doivent rester derrière leurs garde-fous
  runtime existants et méritent des tests API 24/28/29/31.
- `QueryPermissionsNeeded` : 3 occurrences. Android 11+ peut retourner une liste partielle
  des applications visibles. Le produit doit présenter cette liste comme partielle, jamais
  comme un inventaire exhaustif.

## Priorité P2 — sécurité, confidentialité ou robustesse

- `HardwareIds` : 2 occurrences (`ANDROID_ID` et numéro de ligne). `ANDROID_ID` est haché;
  le numéro de ligne est utilisé localement pour associer une MMS à la bonne souscription.
  Aucun secret ni identifiant brut n’est envoyé par ces chemins dans cette qualification.
- `DataExtractionRules` : 1 occurrence. `allowBackup=false` et les règles d’exclusion racine
  réduisent déjà le risque; la compatibilité d’une règle `fullBackupContent` API 24 reste à
  vérifier dans une qualification backup dédiée.
- `ApplySharedPref` : 8 occurrences. Aucun remplacement automatique par `apply()` : plusieurs
  écritures concernent des états d’identité, de rôle ou de nettoyage où la durabilité synchrone
  peut être intentionnelle. Toute conversion exige un test de crash/reprise.

## Priorité P3 — qualité ou maintenance

- `UseKtx` : 82 — style/API idiomatique, pas de défaut de sécurité démontré.
- `UnusedResources` : 57 — nettoyage à faire après vérification des flavors et ressources
  référencées indirectement.
- `IconLauncherShape` et `IconDuplicates` : 15 — qualité visuelle/package, sans impact
  fonctionnel démontré.
- `PluralsCandidate` : 8, `UnusedAttribute` : 6, `TypographyEllipsis` : 2,
  `ObsoleteSdkInt` : 3, `ViewConstructor` : 1, `Typos` : 1 — dette de qualité faible.
- `AndroidGradlePluginVersion` : 1 — suggestion de version Gradle 9.8.2, non appliquée pour
  éviter une migration non qualifiée.

## Hints

19 hints sont informatifs, principalement liés à l’autoboxing Compose et ne constituent pas
un défaut bloquant établi.

## Limites

Cette classification est statique. Elle ne remplace pas les tests instrumentés, l’émulateur,
le provider MMS Android réel ou la qualification Galaxy S24+.
