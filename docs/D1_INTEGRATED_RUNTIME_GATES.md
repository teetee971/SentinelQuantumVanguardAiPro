# Sentinel D.1 — portes de qualification runtime de l'intégration 4 PR

## Preuves acquises (rapports locaux S24+, 21/10/2026)
- Assemblage Android + tests unitaires Gradle de la fusion **locale** #1718+#1719+#1720+#1721 : succès. SHA d'intégration locale `7c21bb1cdb9b0de8b7c0261b7e8fb8f98834704c` (non poussée sur GitHub).
- APK debug SHA-256 : `5d9aa8658aaf33858b55fae80e9af18270d9151ac3dcd98e142a093872811e3`.
- `compileDebugAndroidTestKotlin` + `assembleDebugAndroidTest` : succès ; APK tests SHA-256 `29676891c3095a05cd1f66f1cea9c81e75539047e69fbc5bcbf0b0c94653325f`.
- **Aucun** test `connectedDebugAndroidTest` exécuté ; aucun verdict READY global ni publication justifiés.

## Tests instrumentés présents dans le dépôt
- `AllStaticNavigationSurfacesInstrumentationTest` : 22 tests `render` de destinations NavHost statiques, vérifient l'absence de crash de composition seulement. Ne prouvent ni clics, ni accessibilité, ni qualité graphique.
- `StandaloneActivitySmokeInstrumentationTest` : lancement/reprise de 3 activités (activation Phone Core, diagnostic Phone Core, studio voix). Ni pavé/dialer, ni écran InCall, ni composition SMS réels ne sont couverts ici.
- Contrats supplémentaires : `HomeVisualContractTest`, `ProtectionVisualContractTest`, `SentinelVisualPrimitivesTest`, `OsintTruthInstrumentationTest`, tests Phone Core et sécurité listés dans `src/androidTest`.
- `Screen.OsintDetail` exige un paramètre d'itinéraire; pas couvert par la liste statique.

## Critères runtime bloquants avant publication
- Exécution des tests instrumentés sur un émulateur Android qualifié et collecte des XML/logcat.
- Installation **uniquement** d'un APK dont le certificat correspond à l'application existante ; pas d'effacement des données, de désinstallation ni de désactivation Play Protect. L'APK debug de cette qualification ne doit pas remplacer l'APK de production.
- Captures S24+ et tests de navigation de chaque destination ainsi que des activités indépendantes ; échelle police 100%/200%, TalkBack, écran étroit et refus de permissions.
- Tests téléphonie sortant/entrant, affichage InCall, SMS sortant/entrant, confirmations avant suppression, contacts facultatifs ; vrai statut READY/LIMITED/LOCKED selon preuves Android.
- Network Center : Wi-Fi et Bluetooth observés vs inconnus, disponibilité de permissions, montre appairée vs canal Sentinel authentifié ; VPN non « connecté » sans tunnel réellement attesté.

## Décision
Les gates `compilation`, `tests unitaires`, `APK debug`, `compilation androidTest` sont réussies **localement** pour le SHA d'intégration déclaré. Les gates `instrumentation exécutée`, `UX et accessibilité`, `tests terrain`, `signature production`, `publication` demeurent **ouverts**.

Il faut éviter les boucles de micro-correctifs visuels qui font varier les SHA sans mesurer l'application réelle. La prochaine étape décisive est l'émulateur/les tests d'appareil, pas un build debug supplémentaire.
