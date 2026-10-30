> Candidat historique 4a85b108. Livraison actuelle : [CURRENT_DELIVERY.md](CURRENT_DELIVERY.md).

# Messages : appartenance des résultats à la conversation sélectionnée

## Défaut établi

Dans SmsComposeActivity, le collecteur des callbacks de livraison incrémentait providerEpoch puis lançait une seconde lecture IO du fil sélectionné. Cet effet dépendait de l'envoi actif, pas de selectedThreadId. Si la sélection changeait pendant la lecture, il pouvait publier les messages du fil A dans la vue B. Ce chemin est établi par lecture de code ; aucune reproduction sur Galaxy S24+ n'est revendiquée.

## Correctif réellement raccordé

- Le LaunchedEffect déjà indexé sur providerEpoch, activationEpoch, selectedThreadId et showComposer est l'unique propriétaire des lectures et de leur publication. Les callbacks de livraison et les suppressions confirmées ne publient plus de lectures parallèles : ils incrémentent l'époque. La suppression en base reste la même méthode, après la même confirmation explicite.
- SmsConversationSnapshot associe les lignes à l'identifiant capturé avant la lecture. Il copie/filtre les lignes sur leur propre threadId, et ne les expose qu'à la sélection correspondante et positive. La composition les masque ; retour à la liste et ouverture d'un fil réinitialisent le snapshot.
- SmsConversationRecipient utilise uniquement le résumé du fil sélectionné pour l'en-tête, les raccourcis et la réponse. Le repli vers l'adresse du dernier message est supprimé ; résumé absent = destinataire vide, aucun envoi activable par ce repli. Cela ne démontre pas tous les participants d'un MMS de groupe ; le résumé reste l'adresse présentée par le store existant.
- Une confirmation de suppression de message n'apparaît que pour son propre fil et est effacée au retour à la liste. Les brouillons par fil, payloads MMS et méthodes de transport ne sont pas modifiés.

Aucune nouvelle permission, migration Room, écriture de préférences ou suppression de données automatique n'est introduite. Le correctif ne change pas les règles de permission du provider ni les capacités opérateur.

## Régression et revue

Six tests JVM couvrent : résultat A masqué sous B, sélection absente/identifiants invalides, lignes mélangées, photographie indépendante d'une liste mutable, destinataire du bon résumé et absence de destinataire deviné. La phase rouge échoue à la compilation avant ajout des symboles (aucune assertion exécutée à ce stade). Les journaux/codes sont conservés sous evidence/conversation-ownership.

Revue indépendante statique : aucun défaut important trouvé dans l'intégration ; le reviewer n'a pas exécuté de build ni de test. Les tests JVM ne démontrent pas la livraison des événements Compose ou une navigation pendant une IO suspendue : instrumentation et essais physiques NON EXÉCUTÉS/NON TESTÉS.

## Scénarios S24+ NON TESTÉS

1. Avec deux correspondants de test autorisés A et B, ouvrir alternativement les conversations, puis retour liste et compositeur : aucune bulle d'A sous le nom B ; réponses et raccourcis restent liés au numéro affiché.
2. Envoyer manuellement un SMS de test autorisé à A, naviguer vers B avant le callback réseau ; vérifier le fil B, son destinataire et son brouillon. Aucun envoi automatique n'est demandé par cette procédure.
3. Préparer une suppression de message A puis revenir à la liste et ouvrir B : la confirmation A ne doit pas apparaître dans B. Les essais de suppression réelle nécessitent des messages de test explicitement destinés à être supprimés.
4. Retirer un fil du périmètre des 50 résumés récents ou supprimer ses derniers messages de test : absence de résumé ne doit jamais reprendre l'adresse d'un ancien message pour envoyer.
5. Rotation, arrière-plan/retour et navigation rapide pendant rafraîchissement. Préserver SMS/MMS réels, captures expurgées et résultats par scénario ; ne pas publier de logcat contenant du texte ou des numéros personnels.

Le déterminisme d'une course réseau réelle n'est pas garanti ; les assertions JVM couvrent la frontière d'appartenance, pas le réseau. Pas de preuve de fidélité graphique D.1 complète, de nouvel état Voice/OSINT, ni de qualification Play dans cette itération.

## Qualification exécutée

Sources APK `4a85b10850a350d7b9c9a59e6c5022d79f22429f`. Commande : `python /workspace/d1-evidence/run-gradle.py conversation-ownership-full :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`. Code 0 ; BUILD SUCCESSFUL in 1m 6s. 1 403 tests unitaires Android, 303 suites XML, 0 échec/erreur/ignoré, dont six nouveaux tests d'appartenance/recipient.

APK debug et androidTest : apksigner verify --verbose --print-certs et zipalign -c -v 4, quatre contrôles code 0. Empreintes, tailles et chemins dans ARTIFACTS.json. Schéma Room inchangé. Avertissements de dépréciation Compose/Gradle préexistants conservés. Instrumentation compilée mais NON EXÉCUTÉE. Les 19 tests Node du candidat d7fce52a restent historiques, NON RÉEXÉCUTÉS ici. Aucun nouvel AAB ni test appareil.

Installation : `adb install -r Sentinel-D1-debug-4a85b108.apk`. Les rapports anciens conservent leurs propres noms d'APK historiques.
