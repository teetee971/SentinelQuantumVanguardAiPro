# Qualification initiale — échec confirmé

SHA exact : `249c347768932b73a958bb3dd953958f152a1ac8`. Arbre propre lors du lancement.

Les quatre commandes obligatoires ont toutes renvoyé 1 : test ciblé, suite unitaire, lintDebug et assembleDebug. Cause commune : `PhoneSecurityScreen.kt:840`, référence `Alignment` non résolue. Les tests et lint n’ont pas pu s’exécuter. Aucun APK qualifié. Voir `commands.json` et les quatre journaux. Les anciens PASS du ledger ne prouvent rien sur ce SHA.

Gradle 9.8.0, JDK 17 et SDK 37 restaurés hors dépôt ; wrapper inchangé. Le téléchargement exact fonctionne avec proxy Java et truststore système. L’ancien environnement temporaire est absent ; la cause historique du refus de connexion ne peut pas être établie rétrospectivement.

Dependabot : REST 403 « Resource not accessible by integration » ; GraphQL retourne 0, même sans filtre d’état. Cette incohérence avec les six alertes signalées interdit de conclure à zéro vulnérabilité. Aucune mise à jour appliquée.

Galaxy S24+ : NOT_TESTED.
