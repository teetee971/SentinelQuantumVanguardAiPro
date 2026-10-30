# Plan d'implémentation

Objectif : appliquer les deux propositions retenues, sur branche correction existante. Kotlin/Compose, pas de dépendance ni stockage nouveaux. Exécution directe dans cette session.

- [x] Écrire tests JVM : ContactPhotoPolicy.preferredLocalUri ; ExactCallBlockState.contains. Vérifier RED puis implémenter sélection URI et état exact sans confondre préfixes.
- [x] LocalContactLookup : projeter PHOTO_URI et choisir URI locale ; SentinelDialerActivity : header accessible cliquable, avatar64dp, ouverture même fiche.
- [x] CallBlockingNumberButton : garde IO, cycle RESUMED, confirmation propriétaire/numéro non restaurée, état exact et rôle factuels, ajout via CallBlocklistStore. Tester UI réelle avec opérations injectées dans tests seulement.
- [x] Brancher sur les numéros frais de D1ContactProfile et destinataire de conversation ouverte dans SmsComposeActivity. Garder vérification de sélection et permissions, pas d'autoblocage ni action SMS.
- [x] Compiler Kotlin/testAPK, exécuter unités app/wearable et contrôles Node. Examiner échecs, ne pas prétendre essais tactiles exécutés.
- [x] Commit source [skip ci] et préparation version supérieure ; livraison des empreintes et rapport suivra au commit documentaire. Jamais installer automatiquement.
